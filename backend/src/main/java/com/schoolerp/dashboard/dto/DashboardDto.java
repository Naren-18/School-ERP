package com.schoolerp.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The dashboard, shaped by role: widgets the user may not see, or whose module
 * is switched off, are null. Principal/admin get everything; the accountant
 * gets students and fees; a teacher gets {@link #teacher} only.
 */
public record DashboardDto(String academicYearName, Students students, Attendance attendance, Fees fees,
		List<UpcomingExam> upcomingExams, List<Admission> recentAdmissions, Teacher teacher) {

	public record ClassCount(Long classId, String className, int students) {
	}

	public record Students(int total, List<ClassCount> byClass) {
	}

	/** One school day: (P + ½H) ÷ marked, like the attendance module. */
	public record DayPercent(LocalDate date, int marked, Double percentage) {
	}

	public record PendingSection(Long sectionId, String label, int students) {
	}

	/**
	 * @param nonWorkingReason set on holidays and weekly off days (then nothing is pending)
	 * @param trend the last school days with attendance, oldest first
	 */
	public record Attendance(LocalDate date, String nonWorkingReason, int enrolled, int present, int absent, int leave,
			int halfDay, Double percentage, int sectionsToMark, List<PendingSection> notMarked, List<DayPercent> trend) {
	}

	public record DayAmount(LocalDate date, BigDecimal amount) {
	}

	public record OverdueClass(Long classId, String className, int students, BigDecimal overdue) {
	}

	/**
	 * @param trend collection per day over the last 14 days, oldest first (days without receipts are 0)
	 */
	public record Fees(BigDecimal collectedToday, BigDecimal collectedThisMonth, BigDecimal outstanding,
			BigDecimal overdue, List<OverdueClass> topOverdueClasses, List<DayAmount> trend) {
	}

	public record UpcomingExam(Long id, String name, LocalDate startDate, LocalDate endDate, boolean published) {
	}

	public record Admission(Long studentId, String fullName, String admissionNo, LocalDate admissionDate,
			String className) {
	}

	/**
	 * @param marked attendance taken today; null when the attendance module is off or today is not a school day
	 */
	public record TeacherSection(Long sectionId, String label, int students, boolean classTeacher, Boolean marked) {
	}

	public record MarksPending(Long examId, String examName, Long examSubjectId, Long sectionId, String label,
			String subjectName, int entered, int students) {
	}

	/** @param nonWorkingReason today is a holiday or off day */
	public record Teacher(List<TeacherSection> sections, String nonWorkingReason, List<MarksPending> marksPending) {
	}

}
