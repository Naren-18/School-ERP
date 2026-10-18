package com.schoolerp.school.entity;

import java.time.DayOfWeek;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Per-school configuration stored in {@code school.settings} (JSONB). Also the
 * request body for updating settings, so validation lives here.
 */
public record SchoolSettings(
		@NotNull @Pattern(regexp = "^[A-Z0-9]{1,10}$",
				message = "Use 1-10 capital letters or digits") String receiptPrefix,
		@NotNull @Pattern(regexp = "^[A-Z0-9]{1,10}$",
				message = "Use 1-10 capital letters or digits") String certificatePrefix,
		@NotNull @Size(max = 6, message = "At least one working day is required") List<DayOfWeek> weekOffDays,
		@NotNull @Valid Modules modules,
		@NotNull @Valid ReportCard reportCard) {

	public record Modules(boolean attendance, boolean fees, boolean exams, boolean certificates) {
	}

	public record ReportCard(boolean showRank, boolean showAttendance) {
	}

	public SchoolSettings {
		if (weekOffDays != null) {
			weekOffDays = weekOffDays.stream().distinct().sorted().toList();
		}
	}

	public static SchoolSettings defaults(String schoolCode) {
		String prefix = defaultPrefix(schoolCode);
		return new SchoolSettings(prefix, prefix, List.of(DayOfWeek.SUNDAY), new Modules(true, true, true, true),
				new ReportCard(false, true));
	}

	/** Fills anything missing (e.g. a freshly created school's {@code '{}'}) with defaults. */
	public SchoolSettings withDefaults(String schoolCode) {
		SchoolSettings d = defaults(schoolCode);
		return new SchoolSettings(receiptPrefix != null ? receiptPrefix : d.receiptPrefix,
				certificatePrefix != null ? certificatePrefix : d.certificatePrefix,
				weekOffDays != null ? weekOffDays : d.weekOffDays, modules != null ? modules : d.modules,
				reportCard != null ? reportCard : d.reportCard);
	}

	private static String defaultPrefix(String schoolCode) {
		String cleaned = schoolCode == null ? "" : schoolCode.toUpperCase().replaceAll("[^A-Z0-9]", "");
		return cleaned.isEmpty() ? "SCH" : cleaned.substring(0, Math.min(10, cleaned.length()));
	}

}
