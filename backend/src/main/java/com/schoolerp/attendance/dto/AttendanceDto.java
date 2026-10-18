package com.schoolerp.attendance.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.schoolerp.attendance.entity.Attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AttendanceDto {

	private AttendanceDto() {
	}

	/** Present / absent / leave / half-day counts and the attendance percentage. */
	public record Counts(int present, int absent, int leave, int halfDay, int unmarked, Double percentage) {

		/** (P + ½H) ÷ days marked, one decimal; null when nothing is marked. */
		public static Counts of(int p, int a, int l, int h, int unmarked) {
			int marked = p + a + l + h;
			Double pct = marked == 0 ? null : Math.round((p + h / 2.0) * 1000.0 / marked) / 10.0;
			return new Counts(p, a, l, h, unmarked, pct);
		}

	}

	/** A section that the user can take attendance for, with whether the date is already marked. */
	public record SectionOption(Long sectionId, Long classId, String className, String sectionName, boolean marked) {
	}

	public record SheetStudent(Long enrollmentId, Long studentId, Integer rollNo, String admissionNo, String fullName,
			Attendance.Status status) {
	}

	/**
	 * A section's attendance for one day. {@code status} is null for students not
	 * yet marked. {@code editable} is false (with {@code blockedReason}) on holidays,
	 * off days, future dates, or past days for teachers.
	 */
	public record Sheet(Long sectionId, String sectionLabel, LocalDate date, boolean editable, String blockedReason,
			boolean marked, String markedByName, OffsetDateTime markedAt, Counts counts, List<SheetStudent> students) {
	}

	public record Entry(@NotNull Long enrollmentId, @NotNull(message = "Choose P, A, L or H") Attendance.Status status) {
	}

	public record SaveRequest(@NotEmpty(message = "No students to save") @Size(max = 200) List<@Valid Entry> entries) {
	}

	/** One calendar day of a month view. */
	public record Day(LocalDate date, String dayOfWeek, String nonWorkingReason, boolean future) {
	}

	public record StudentMonth(Long studentId, String fullName, String month, List<Day> days,
			List<Attendance.Status> statuses, Counts counts) {
	}

	public record RegisterRow(Long enrollmentId, Long studentId, Integer rollNo, String fullName,
			List<Attendance.Status> statuses, Counts counts) {
	}

	/** Students × days for a month; {@code statuses} align with {@code days}. */
	public record Register(Long sectionId, String sectionLabel, String month, List<Day> days, List<RegisterRow> rows) {
	}

	public record ClassToday(Long classId, String className, int sections, int markedSections, int enrolled,
			Counts counts) {
	}

	public record UnmarkedSection(Long sectionId, String label, int enrolled) {
	}

	public record Today(LocalDate date, String nonWorkingReason, int enrolled, Counts counts, List<ClassToday> classes,
			List<UnmarkedSection> unmarkedSections) {
	}

}
