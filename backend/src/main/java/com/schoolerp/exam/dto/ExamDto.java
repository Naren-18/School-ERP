package com.schoolerp.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.schoolerp.exam.service.ResultCalculator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ExamDto {

	private ExamDto() {
	}

	// --- Grading -------------------------------------------------------------

	public record BandInput(
			@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal minPercent,
			@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal maxPercent,
			@NotBlank(message = "Grade is required") @Size(max = 5) String grade,
			@DecimalMin("0") @DecimalMax("99.99") @Digits(integer = 2, fraction = 2) BigDecimal gradePoint,
			@Size(max = 60) String remark) {
	}

	public record ScaleRequest(
			@NotBlank(message = "Name is required") @Size(max = 60) String name,
			@NotEmpty(message = "Add at least one grade") @Size(max = 15) List<@Valid BandInput> bands) {
	}

	public record Band(Long id, BigDecimal minPercent, BigDecimal maxPercent, String grade, BigDecimal gradePoint,
			String remark) {
	}

	public record Scale(Long id, String name, List<Band> bands, boolean inUse) {
	}

	// --- Exams and papers ----------------------------------------------------

	public record ExamRequest(
			@NotNull(message = "Academic year is required") Long academicYearId,
			@NotBlank(message = "Name is required") @Size(max = 40) String name,
			@NotNull(message = "Grading scale is required") Long gradingScaleId,
			LocalDate startDate,
			LocalDate endDate,
			@Min(0) @Max(999) Integer displayOrder,
			/** Share in the final result, e.g. 20 for FA and 80 for SA. Empty = not counted. */
			@DecimalMin(value = "0.01", message = "Must be more than zero") @DecimalMax("100")
			@Digits(integer = 3, fraction = 2) BigDecimal weightage) {
	}

	public record Exam(Long id, Long academicYearId, String name, Long gradingScaleId, String gradingScaleName,
			LocalDate startDate, LocalDate endDate, int displayOrder, BigDecimal weightage, boolean published,
			int papers) {
	}

	public record PaperRequest(
			@NotNull(message = "Class is required") Long classId,
			@NotNull(message = "Subject is required") Long subjectId,
			@NotNull(message = "Maximum marks are required") @DecimalMin(value = "0.01") @DecimalMax("999.99")
			@Digits(integer = 3, fraction = 2) BigDecimal maxMarks,
			@DecimalMin("0") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal passMarks,
			LocalDate examDate) {
	}

	public record Paper(Long id, Long examId, Long classId, String className, Long subjectId, String subjectName,
			BigDecimal maxMarks, BigDecimal passMarks, LocalDate examDate) {
	}

	/** Adds a paper for every subject of every class (or the given classes) that lacks one. */
	public record ApplyRequest(
			@NotNull @DecimalMin(value = "0.01") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal maxMarks,
			@DecimalMin("0") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal passMarks,
			List<Long> classIds) {
	}

	public record ApplyResult(int created) {
	}

	// --- Marks entry ---------------------------------------------------------

	/** A paper + section the user may enter marks for, with progress. */
	public record EntryOption(Long examSubjectId, Long sectionId, Long classId, String className, String sectionName,
			Long subjectId, String subjectName, BigDecimal maxMarks, int students, int entered) {
	}

	public record MarkRow(Long enrollmentId, Long studentId, Integer rollNo, String fullName, BigDecimal marks,
			boolean absent, String remarks) {
	}

	public record MarkSheet(Long examSubjectId, String examName, String subjectName, Long sectionId, String sectionLabel,
			BigDecimal maxMarks, BigDecimal passMarks, boolean editable, String lockedReason, List<MarkRow> rows) {
	}

	/** Blank marks and not absent clears a student's mark. */
	public record MarkEntry(@NotNull Long enrollmentId,
			@DecimalMin("0") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal marks, boolean absent,
			@Size(max = 150) String remarks) {
	}

	public record SaveMarks(@NotNull @Size(max = 200) List<@Valid MarkEntry> entries) {
	}

	// --- Results -------------------------------------------------------------

	public record SubjectHeader(Long subjectId, String subjectName, BigDecimal maxMarks) {
	}

	public record ResultRow(Long enrollmentId, Long studentId, Integer rollNo, String fullName,
			ResultCalculator.ExamResult result, Integer rank) {
	}

	public record ResultSheet(Long examId, String examName, boolean published, Long sectionId, String sectionLabel,
			List<SubjectHeader> subjects, List<ResultRow> rows) {
	}

	public record ExamSummary(Long examId, String examName, boolean published, BigDecimal weightage,
			ResultCalculator.ExamResult result) {
	}

	/** A student's year: every published exam, plus the weighted final result. */
	public record StudentResults(Long studentId, String fullName, String academicYearName, List<ExamSummary> exams,
			ResultCalculator.FinalResult finalResult, Map<Long, String> subjectNames) {
	}

}
