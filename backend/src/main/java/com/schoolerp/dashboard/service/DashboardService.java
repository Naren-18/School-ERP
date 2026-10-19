package com.schoolerp.dashboard.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.SchoolCalendarService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.academic.service.TeacherScopeService;
import com.schoolerp.attendance.dto.AttendanceDto;
import com.schoolerp.attendance.service.AttendanceReportService;
import com.schoolerp.attendance.service.AttendanceService;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.service.AuthUser;
import com.schoolerp.auth.service.CurrentUser;
import com.schoolerp.dashboard.dto.DashboardDto;
import com.schoolerp.dashboard.repository.DashboardRepository;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.service.ExamService;
import com.schoolerp.exam.service.MarksService;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Builds the role-aware dashboard from one query per widget. Widgets of a
 * switched-off module are left out; without a current academic year there is
 * nothing to show yet (the screen shows the setup checklist).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

	/** Days back for the attendance and collection charts. */
	static final int TREND_DAYS = 14;

	private final DashboardRepository repo;

	private final AcademicYearService yearService;

	private final SchoolService schoolService;

	private final SchoolCalendarService calendar;

	private final SectionService sectionService;

	private final TeacherScopeService teacherScope;

	private final AttendanceReportService attendanceReports;

	private final AttendanceService attendanceService;

	private final ExamService examService;

	private final MarksService marksService;

	private final Clock clock;

	@Transactional(readOnly = true)
	public DashboardDto get() {
		Long schoolId = TenantContext.require();
		Long yearId = yearService.resolve(null).orElse(null);
		if (yearId == null) {
			return new DashboardDto(null, null, null, null, null, null, null);
		}
		AcademicYear year = yearService.require(yearId);
		SchoolSettings.Modules modules = schoolService.requireCurrent().getSettings().modules();
		AuthUser user = CurrentUser.get();
		LocalDate today = LocalDate.now(clock);
		boolean adminLike = user.hasRole(Role.PRINCIPAL) || user.hasRole(Role.ADMIN);
		boolean accountant = user.hasRole(Role.ACCOUNTANT);

		if (!adminLike && !accountant) {
			return new DashboardDto(year.getName(), null, null, null, null, null, teacher(user, modules, today));
		}
		List<DashboardDto.ClassCount> byClass = repo.studentsByClass(schoolId, yearId);
		DashboardDto.Students students = new DashboardDto.Students(
				byClass.stream().mapToInt(DashboardDto.ClassCount::students).sum(), byClass);
		DashboardDto.Fees fees = modules.fees() ? fees(schoolId, yearId, today) : null;
		if (!adminLike) {
			return new DashboardDto(year.getName(), students, null, fees, null, null, null);
		}
		return new DashboardDto(year.getName(), students, modules.attendance() ? attendance(schoolId, today) : null, fees,
				modules.exams() ? repo.upcomingExams(schoolId, yearId, today, 5) : null,
				repo.recentAdmissions(schoolId, yearId, 5), null);
	}

	private DashboardDto.Attendance attendance(Long schoolId, LocalDate today) {
		AttendanceDto.Today t = attendanceReports.today(today);
		AttendanceDto.Counts c = t.counts();
		int sectionsToMark = t.classes().stream().mapToInt(AttendanceDto.ClassToday::sections).sum();
		List<DashboardDto.DayPercent> trend = repo.attendanceByDay(schoolId, today.minusDays(TREND_DAYS - 1L), today);
		return new DashboardDto.Attendance(t.date(), t.nonWorkingReason(), t.enrolled(), c.present(), c.absent(), c.leave(),
				c.halfDay(), c.percentage(), sectionsToMark,
				t.unmarkedSections().stream().map(u -> new DashboardDto.PendingSection(u.sectionId(), u.label(), u.enrolled())).toList(),
				trend);
	}

	private DashboardDto.Fees fees(Long schoolId, Long yearId, LocalDate today) {
		LocalDate from = today.minusDays(TREND_DAYS - 1L);
		Map<LocalDate, BigDecimal> byDay = repo.collectedByDay(schoolId, from, today);
		List<DashboardDto.DayAmount> trend = new ArrayList<>();
		for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
			trend.add(new DashboardDto.DayAmount(d, byDay.getOrDefault(d, BigDecimal.ZERO)));
		}
		DashboardRepository.DuesTotal dues = repo.duesTotal(schoolId, yearId, today);
		return new DashboardDto.Fees(byDay.getOrDefault(today, BigDecimal.ZERO),
				repo.collectedBetween(schoolId, today.withDayOfMonth(1), today), dues.outstanding(), dues.overdue(),
				repo.topOverdueClasses(schoolId, yearId, today, 5), trend);
	}

	/** A teacher's day: their sections (attendance taken or not) and marks still to enter. */
	private DashboardDto.Teacher teacher(AuthUser user, SchoolSettings.Modules modules, LocalDate today) {
		Set<Long> mine = teacherScope.sectionIdsFor(user.userId());
		List<SectionDto.Response> sections = sectionService.list(null, null)
			.stream()
			.filter(s -> mine.contains(s.id()))
			.toList();
		String nonWorking = calendar.nonWorkingReason(today).orElse(null);
		Set<Long> marked = modules.attendance() && nonWorking == null
				? attendanceService.sections(today)
					.stream()
					.filter(AttendanceDto.SectionOption::marked)
					.map(AttendanceDto.SectionOption::sectionId)
					.collect(Collectors.toSet())
				: null;
		Map<Long, Integer> counts = repo.activeStudents(TenantContext.require(),
				sections.stream().map(SectionDto.Response::id).toList());
		List<DashboardDto.TeacherSection> mySections = sections.stream()
			.map(s -> new DashboardDto.TeacherSection(s.id(), s.className() + " – " + s.name(), counts.getOrDefault(s.id(), 0),
					user.userId().equals(s.classTeacherId()), marked == null ? null : marked.contains(s.id())))
			.toList();

		List<DashboardDto.MarksPending> pending = new ArrayList<>();
		if (modules.exams()) {
			for (ExamDto.Exam exam : examService.list(null)) {
				if (exam.published()) {
					continue;
				}
				for (ExamDto.EntryOption o : marksService.entryOptions(exam.id())) {
					if (o.students() > 0 && o.entered() < o.students()) {
						pending.add(new DashboardDto.MarksPending(exam.id(), exam.name(), o.examSubjectId(), o.sectionId(),
								o.className() + " – " + o.sectionName(), o.subjectName(), o.entered(), o.students()));
					}
				}
			}
			pending.sort(Comparator.comparing(DashboardDto.MarksPending::examId).thenComparing(DashboardDto.MarksPending::label));
		}
		return new DashboardDto.Teacher(mySections, nonWorking, pending);
	}

}
