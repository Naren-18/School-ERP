package com.schoolerp.attendance.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.SchoolCalendarService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.attendance.dto.AttendanceDto;
import com.schoolerp.attendance.entity.Attendance;
import com.schoolerp.attendance.repository.AttendanceRepository;
import com.schoolerp.auth.service.CurrentUser;
import com.schoolerp.auth.service.StaffService;
import com.schoolerp.common.error.ApiException;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.service.EnrollmentService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Taking attendance: one section, one day, everyone at once. */
@Service
@RequiredArgsConstructor
public class AttendanceService {

	private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private final AttendanceRepository attendance;

	private final SectionService sectionService;

	private final AcademicYearService yearService;

	private final SchoolCalendarService calendar;

	private final EnrollmentService enrollments;

	private final StaffService staff;

	private final AttendanceAccess access;

	private final Clock clock;

	/** Current-year sections the user may take attendance for, flagged if already marked on the date. */
	@Transactional(readOnly = true)
	public List<AttendanceDto.SectionOption> sections(LocalDate date) {
		Set<Long> scope = access.sectionScope();
		List<SectionDto.Response> list = sectionService.list(null, null)
			.stream()
			.filter(s -> scope == null || scope.contains(s.id()))
			.toList();
		if (list.isEmpty()) {
			return List.of();
		}
		Set<Long> marked = new HashSet<>(attendance.markedSectionIds(TenantContext.require(), date,
				list.stream().map(SectionDto.Response::id).toList()));
		return list.stream()
			.map(s -> new AttendanceDto.SectionOption(s.id(), s.classId(), s.className(), s.name(), marked.contains(s.id())))
			.toList();
	}

	@Transactional(readOnly = true)
	public AttendanceDto.Sheet sheet(Long sectionId, LocalDate date) {
		Section section = sectionService.require(sectionId);
		access.requireSection(section);
		Optional<String> blocked = blockedReason(section, date).map(Blocked::message);
		List<StudentDto.SectionStudent> roster = enrollments.rosterDuring(sectionId, date, date);
		Map<Long, Attendance> byEnrollment = recorded(roster, date);

		int[] counts = new int[5];
		List<AttendanceDto.SheetStudent> students = roster.stream().map(s -> {
			Attendance a = byEnrollment.get(s.enrollmentId());
			counts[a == null ? 4 : a.getStatus().ordinal()]++;
			return new AttendanceDto.SheetStudent(s.enrollmentId(), s.studentId(), s.rollNo(), s.admissionNo(),
					s.fullName(), a == null ? null : a.getStatus());
		}).toList();

		Optional<Attendance> latest = byEnrollment.values().stream().max(Comparator.comparing(Attendance::getMarkedAt));
		String markedBy = latest.map(Attendance::getMarkedBy)
			.filter(Objects::nonNull)
			.map(id -> staff.fullNames(List.of(id)).get(id))
			.orElse(null);
		return new AttendanceDto.Sheet(sectionId, label(section), date, blocked.isEmpty() && !roster.isEmpty(),
				blocked.orElse(roster.isEmpty() ? "No students are enrolled in this section on this date" : null),
				!byEnrollment.isEmpty(), markedBy, latest.map(Attendance::getMarkedAt).orElse(null),
				AttendanceDto.Counts.of(counts[0], counts[1], counts[2], counts[3], counts[4]), students);
	}

	/**
	 * Saves the whole section for the day. Every student on the roster must be
	 * included, so nobody is silently left unmarked.
	 */
	@Transactional
	public AttendanceDto.Sheet save(Long sectionId, LocalDate date, AttendanceDto.SaveRequest request) {
		Section section = sectionService.require(sectionId);
		access.requireSection(section);
		blockedReason(section, date).ifPresent(b -> {
			throw new ApiException(b.status(), b.message());
		});

		List<StudentDto.SectionStudent> roster = enrollments.rosterDuring(sectionId, date, date);
		Set<Long> rosterIds = roster.stream().map(StudentDto.SectionStudent::enrollmentId).collect(Collectors.toSet());
		Map<Long, Attendance.Status> wanted = request.entries()
			.stream()
			.collect(Collectors.toMap(AttendanceDto.Entry::enrollmentId, AttendanceDto.Entry::status, (a, b) -> b));
		if (!wanted.keySet().equals(rosterIds)) {
			throw new BadRequestException("The class list has changed since it was opened. Reload and mark again.");
		}

		Map<Long, Attendance> existing = recorded(roster, date);
		OffsetDateTime now = OffsetDateTime.now(clock);
		Long userId = CurrentUser.get().userId();
		for (Map.Entry<Long, Attendance.Status> e : wanted.entrySet()) {
			Attendance a = existing.get(e.getKey());
			if (a == null) {
				a = new Attendance();
				a.setEnrollmentId(e.getKey());
				a.setDate(date);
			}
			else if (a.getStatus() == e.getValue()) {
				continue;
			}
			a.setStatus(e.getValue());
			a.setMarkedBy(userId);
			a.setMarkedAt(now);
			attendance.save(a);
		}
		attendance.flush();
		return sheet(sectionId, date);
	}

	/** Why a day cannot be marked, and the HTTP status a save attempt gets. */
	record Blocked(String message, HttpStatus status) {
	}

	/**
	 * Why attendance cannot be taken on this date, if anything: outside the
	 * year or in the future (400), a holiday or weekly off day (409), or, for
	 * teachers, any day but today (403).
	 */
	Optional<Blocked> blockedReason(Section section, LocalDate date) {
		AcademicYear year = yearService.require(section.getAcademicYearId());
		LocalDate today = LocalDate.now(clock);
		if (!year.contains(date)) {
			return Optional.of(new Blocked(date.format(DMY) + " is outside the academic year " + year.getName(),
					HttpStatus.BAD_REQUEST));
		}
		if (date.isAfter(today)) {
			return Optional.of(new Blocked("Attendance cannot be marked for a future date", HttpStatus.BAD_REQUEST));
		}
		Optional<String> nonWorking = calendar.nonWorkingReason(date);
		if (nonWorking.isPresent()) {
			return Optional.of(new Blocked(date.format(DMY) + " is not a school day: " + nonWorking.get(),
					HttpStatus.CONFLICT));
		}
		if (!access.isAdminLike() && !date.equals(today)) {
			return Optional.of(new Blocked("Teachers can mark or change only today's attendance. "
					+ "Ask the principal or admin to correct earlier days.", HttpStatus.FORBIDDEN));
		}
		return Optional.empty();
	}

	private Map<Long, Attendance> recorded(List<StudentDto.SectionStudent> roster, LocalDate date) {
		if (roster.isEmpty()) {
			return Map.of();
		}
		return attendance
			.findBySchoolIdAndEnrollmentIdInAndDate(TenantContext.require(),
					roster.stream().map(StudentDto.SectionStudent::enrollmentId).toList(), date)
			.stream()
			.collect(Collectors.toMap(Attendance::getEnrollmentId, Function.identity()));
	}

	String label(Section section) {
		SectionDto.Response s = sectionService.describe(List.of(section.getId())).get(section.getId());
		return s.className() + " – " + s.name();
	}

}
