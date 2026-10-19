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
		List<UpcomingExam> upcomingExams, ExamResult latestResult, List<Admission> recentAdmissions, Teacher teacher) {

	/** Active students of a class; {@code students - boys - girls} are recorded as other. */
	public record ClassCount(Long classId, String className, int students, int boys, int girls) {
	}

	public record Students(int total, List<ClassCount> byClass) {
	}

	/** One school day: (P + ½H) ÷ marked, like the attendance module. */
	public record DayPercent(LocalDate date, int marked, Double percentage) {
	}

	public record PendingSection(Long sectionId, String label, int students) {
	}

	/** Attendance of a class over a period, same formula as {@link DayPercent}; null when nothing is marked. */
	public record ClassPercent(Long classId, String className, int marked, Double percentage) {
	}

	/**
	 * @param nonWorkingReason set on holidays and weekly off days (then nothing is pending)
	 * @param trend the last school days with attendance, oldest first
	 * @param byClass this month so far, per class in class order (classes with nothing marked left out)
	 */
	public record Attendance(LocalDate date, String nonWorkingReason, int enrolled, int present, int absent, int leave,
			int halfDay, Double percentage, int sectionsToMark, List<PendingSection> notMarked, List<DayPercent> trend,
			List<ClassPercent> byClass) {
	}

	public record DayAmount(LocalDate date, BigDecimal amount) {
	}

	/** @param month the first day of the month */
	public record MonthAmount(LocalDate month, BigDecimal amount) {
	}

	/** @param key a payment mode name, or a fee head id */
	public record PartAmount(String key, String label, BigDecimal amount) {
	}

	public record OverdueClass(Long classId, String className, int students, BigDecimal overdue) {
	}

	/**
	 * @param collectedThisYear paid against the year's dues (with outstanding: what the year's plan asks for, after concessions)
	 * @param trend collection per day over the last 14 days, oldest first (days without receipts are 0)
	 * @param byMonth collection per month from the start of the year to this month (months without receipts are 0)
	 * @param byMode the year's receipts per payment mode, largest first
	 * @param byHead the year's receipts per fee head, largest first
	 */
	public record Fees(BigDecimal collectedToday, BigDecimal collectedThisMonth, BigDecimal collectedThisYear,
			BigDecimal outstanding, BigDecimal overdue, List<OverdueClass> topOverdueClasses, List<DayAmount> trend,
			List<MonthAmount> byMonth, List<PartAmount> byMode, List<PartAmount> byHead) {
	}

	/**
	 * A subject in an exam, over every class that wrote it.
	 * @param averagePercent of the students who were present
	 * @param passPercent of the students with marks or marked absent (absent fails)
	 */
	public record SubjectResult(Long subjectId, String subjectName, int students, Double averagePercent,
			Double passPercent) {
	}

	/** The latest published exam of the year, per subject. */
	public record ExamResult(Long examId, String examName, List<SubjectResult> subjects) {
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
