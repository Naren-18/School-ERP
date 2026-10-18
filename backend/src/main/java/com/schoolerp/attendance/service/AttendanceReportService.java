package com.schoolerp.attendance.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.SchoolCalendarService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.attendance.dto.AttendanceDto;
import com.schoolerp.attendance.entity.Attendance;
import com.schoolerp.attendance.repository.AttendanceReportRepository;
import com.schoolerp.attendance.repository.AttendanceRepository;
import com.schoolerp.common.csv.Csv;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Enrollment;
import com.schoolerp.student.entity.Student;
import com.schoolerp.student.service.EnrollmentService;
import com.schoolerp.student.service.StudentService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttendanceReportService {

	private final AttendanceRepository attendance;

	private final AttendanceReportRepository reports;

	private final SectionService sectionService;

	private final AcademicYearService yearService;

	private final SchoolCalendarService calendar;

	private final EnrollmentService enrollments;

	private final StudentService studentService;

	private final AttendanceAccess access;

	private final AttendanceService attendanceService;

	private final Clock clock;

	/** One student's month: a status per day plus totals (for the profile calendar). */
	@Transactional(readOnly = true)
	public AttendanceDto.StudentMonth studentMonth(Long studentId, YearMonth month) {
		Student student = studentService.requireVisible(studentId);
		List<AttendanceDto.Day> days = days(month);
		List<Long> enrollmentIds = enrollments
			.studentEnrollmentsDuring(studentId, month.atDay(1), month.atEndOfMonth())
			.stream()
			.map(Enrollment::getId)
			.toList();
		Map<LocalDate, Attendance.Status> byDate = enrollmentIds.isEmpty() ? Map.of()
				: attendance
					.findBySchoolIdAndEnrollmentIdInAndDateBetween(TenantContext.require(), enrollmentIds, month.atDay(1),
							month.atEndOfMonth())
					.stream()
					.collect(Collectors.toMap(Attendance::getDate, Attendance::getStatus, (a, b) -> b));
		List<Attendance.Status> statuses = days.stream().map(d -> byDate.get(d.date())).toList();
		return new AttendanceDto.StudentMonth(student.getId(), student.getFullName(), month.toString(), days, statuses,
				count(statuses));
	}

	/** Students × days for a section and month (the monthly register). */
	@Transactional(readOnly = true)
	public AttendanceDto.Register register(Long sectionId, YearMonth month) {
		Section section = sectionService.require(sectionId);
		access.requireSection(section);
		LocalDate from = month.atDay(1);
		LocalDate to = month.atEndOfMonth();
		List<AttendanceDto.Day> days = days(month);
		List<StudentDto.SectionStudent> roster = enrollments.rosterDuring(sectionId, from, to);

		Map<Long, Map<LocalDate, Attendance.Status>> byEnrollment = new LinkedHashMap<>();
		if (!roster.isEmpty()) {
			attendance
				.findBySchoolIdAndEnrollmentIdInAndDateBetween(TenantContext.require(),
						roster.stream().map(StudentDto.SectionStudent::enrollmentId).toList(), from, to)
				.forEach(a -> byEnrollment.computeIfAbsent(a.getEnrollmentId(), k -> new LinkedHashMap<>())
					.put(a.getDate(), a.getStatus()));
		}
		List<AttendanceDto.RegisterRow> rows = roster.stream().map(s -> {
			Map<LocalDate, Attendance.Status> marks = byEnrollment.getOrDefault(s.enrollmentId(), Map.of());
			List<Attendance.Status> statuses = days.stream().map(d -> marks.get(d.date())).toList();
			return new AttendanceDto.RegisterRow(s.enrollmentId(), s.studentId(), s.rollNo(), s.fullName(), statuses,
					count(statuses));
		}).toList();
		return new AttendanceDto.Register(sectionId, attendanceService.label(section), month.toString(), days, rows);
	}

	/** The register as CSV: Roll, Name, one column per day, then totals. */
	@Transactional(readOnly = true)
	public String registerCsv(Long sectionId, YearMonth month) {
		AttendanceDto.Register register = register(sectionId, month);
		List<List<String>> out = new ArrayList<>();
		List<String> header = new ArrayList<>(List.of("Roll", "Name"));
		register.days().forEach(d -> header.add(String.format("%02d %s", d.date().getDayOfMonth(), d.dayOfWeek())));
		header.addAll(List.of("Present", "Absent", "Leave", "Half day", "Attendance %"));
		out.add(header);
		for (AttendanceDto.RegisterRow row : register.rows()) {
			List<String> line = new ArrayList<>(List.of(row.rollNo() == null ? "" : row.rollNo().toString(), row.fullName()));
			for (int i = 0; i < register.days().size(); i++) {
				line.add(csvCell(row.statuses().get(i), register.days().get(i)));
			}
			AttendanceDto.Counts c = row.counts();
			line.addAll(List.of(String.valueOf(c.present()), String.valueOf(c.absent()), String.valueOf(c.leave()),
					String.valueOf(c.halfDay()), c.percentage() == null ? "" : c.percentage().toString()));
			out.add(line);
		}
		return Csv.write(out);
	}

	/** School-wide picture for a day (default today), by class, plus sections not yet marked. */
	@Transactional(readOnly = true)
	public AttendanceDto.Today today(LocalDate date) {
		LocalDate day = date == null ? LocalDate.now(clock) : date;
		String nonWorking = calendar.nonWorkingReason(day).orElse(null);
		Long yearId = yearService.resolve(null).orElseThrow(() -> new NotFoundException("Current academic year"));
		List<AttendanceReportRepository.SectionDay> sections = reports.sectionsOn(TenantContext.require(), yearId, day);

		Map<Long, List<AttendanceReportRepository.SectionDay>> byClass = sections.stream()
			.collect(Collectors.groupingBy(AttendanceReportRepository.SectionDay::classId, LinkedHashMap::new,
					Collectors.toList()));
		List<AttendanceDto.ClassToday> classes = byClass.values().stream().map(list -> {
			AttendanceReportRepository.SectionDay first = list.getFirst();
			int enrolled = list.stream().mapToInt(AttendanceReportRepository.SectionDay::enrolled).sum();
			int marked = list.stream().mapToInt(AttendanceReportRepository.SectionDay::marked).sum();
			// Sections with nobody enrolled that day need no marking, so they do not count.
			int withStudents = (int) list.stream().filter(s -> s.enrolled() > 0).count();
			return new AttendanceDto.ClassToday(first.classId(), first.className(), withStudents,
					(int) list.stream().filter(s -> s.marked() > 0).count(), enrolled, AttendanceDto.Counts.of(
							sum(list, AttendanceReportRepository.SectionDay::present),
							sum(list, AttendanceReportRepository.SectionDay::absent),
							sum(list, AttendanceReportRepository.SectionDay::leave),
							sum(list, AttendanceReportRepository.SectionDay::halfDay), enrolled - marked));
		}).toList();

		List<AttendanceDto.UnmarkedSection> unmarked = nonWorking != null ? List.of()
				: sections.stream()
					.filter(s -> s.enrolled() > 0 && s.marked() == 0)
					.sorted(Comparator.comparingInt(AttendanceReportRepository.SectionDay::classOrder))
					.map(s -> new AttendanceDto.UnmarkedSection(s.sectionId(), s.className() + " – " + s.sectionName(),
							s.enrolled()))
					.toList();
		int enrolled = sum(sections, AttendanceReportRepository.SectionDay::enrolled);
		int marked = sum(sections, AttendanceReportRepository.SectionDay::marked);
		return new AttendanceDto.Today(day, nonWorking, enrolled,
				AttendanceDto.Counts.of(sum(sections, AttendanceReportRepository.SectionDay::present),
						sum(sections, AttendanceReportRepository.SectionDay::absent),
						sum(sections, AttendanceReportRepository.SectionDay::leave),
						sum(sections, AttendanceReportRepository.SectionDay::halfDay), enrolled - marked),
				classes, unmarked);
	}

	/** P/A/L/H when marked; HOL or OFF on non-school days; blank otherwise. */
	private static String csvCell(Attendance.Status status, AttendanceDto.Day day) {
		if (status != null) {
			return status.name();
		}
		if (day.nonWorkingReason() == null) {
			return "";
		}
		return day.nonWorkingReason().startsWith("Holiday") ? "HOL" : "OFF";
	}

	private List<AttendanceDto.Day> days(YearMonth month) {
		LocalDate today = LocalDate.now(clock);
		Map<LocalDate, String> nonWorking = calendar.nonWorkingDays(month.atDay(1), month.atEndOfMonth());
		List<AttendanceDto.Day> days = new ArrayList<>();
		for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
			days.add(new AttendanceDto.Day(d, d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
					nonWorking.get(d), d.isAfter(today)));
		}
		return days;
	}

	/** Attendance totals of the given enrollments over a date range (report cards). */
	@Transactional(readOnly = true)
	public AttendanceDto.Counts summary(List<Long> enrollmentIds, LocalDate from, LocalDate to) {
		if (enrollmentIds.isEmpty()) {
			return count(List.of());
		}
		return count(attendance.findBySchoolIdAndEnrollmentIdInAndDateBetween(TenantContext.require(), enrollmentIds, from, to)
			.stream()
			.map(Attendance::getStatus)
			.toList());
	}

	private static AttendanceDto.Counts count(List<Attendance.Status> statuses) {
		Map<Attendance.Status, Long> c = statuses.stream()
			.filter(Objects::nonNull)
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
		return AttendanceDto.Counts.of(c.getOrDefault(Attendance.Status.P, 0L).intValue(),
				c.getOrDefault(Attendance.Status.A, 0L).intValue(), c.getOrDefault(Attendance.Status.L, 0L).intValue(),
				c.getOrDefault(Attendance.Status.H, 0L).intValue(), 0);
	}

	private static <T> int sum(List<T> list, ToIntFunction<T> f) {
		return list.stream().mapToInt(f).sum();
	}

}
