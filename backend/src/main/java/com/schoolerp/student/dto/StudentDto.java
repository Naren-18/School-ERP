package com.schoolerp.student.dto;

import java.time.LocalDate;
import java.util.List;

import com.schoolerp.student.entity.Enrollment;
import com.schoolerp.student.entity.Guardian;
import com.schoolerp.student.entity.Student;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StudentDto {

	/** Indian mobile number: 10 digits starting 6-9. */
	public static final String MOBILE = "^[6-9]\\d{9}$";

	private StudentDto() {
	}

	public record GuardianInput(
			@NotNull(message = "Relation is required") Guardian.Relation relation,
			@NotBlank(message = "Name is required") @Size(max = 120) String fullName,
			@Size(max = 80) String occupation,
			@Pattern(regexp = MOBILE, message = "Enter a 10-digit mobile number") String phone,
			@Email(message = "Enter a valid email") @Size(max = 120) String email,
			/** Optional; the first guardian becomes primary when none is marked. */
			Boolean primary) {

		public boolean isPrimary() {
			return Boolean.TRUE.equals(primary);
		}
	}

	/** Student fields shared by admission and edit. */
	public record Details(
			@NotBlank(message = "Admission number is required") @Size(max = 30) String admissionNo,
			@NotNull(message = "Admission date is required") @PastOrPresent(message = "Cannot be in the future")
			LocalDate admissionDate,
			@NotBlank(message = "First name is required") @Size(max = 60) String firstName,
			@Size(max = 60) String lastName,
			@NotNull(message = "Date of birth is required") @Past(message = "Must be in the past") LocalDate dateOfBirth,
			@NotNull(message = "Gender is required") Student.Gender gender,
			@Size(max = 30) String motherTongue,
			Student.SocialCategory socialCategory,
			@Size(max = 30) String religion,
			@Size(max = 50) String caste,
			/** Write-only. Null keeps the stored number; an empty string removes it. */
			@Size(max = 14) String aadhaar,
			@Size(max = 500) String identificationMarks,
			@Size(max = 500) String address,
			@Size(max = 150) String previousSchool,
			@Size(max = 50) String previousTcNo,
			@NotEmpty(message = "Add at least one parent or guardian") @Size(max = 4)
			List<@Valid GuardianInput> guardians,
			/** OK to send WhatsApp messages to the primary contact. Null keeps the stored choice (no on admission). */
			Boolean whatsappConsent) {
	}

	public record Placement(
			@NotNull(message = "Section is required") Long sectionId,
			@Min(1) @Max(999) Integer rollNo,
			LocalDate joinedOn) {
	}

	public record CreateRequest(@NotNull @Valid Details student, @NotNull @Valid Placement enrollment) {
	}

	public record ListItem(Long id, String admissionNo, String fullName, Student.Gender gender, LocalDate dateOfBirth,
			Student.Status status, Long enrollmentId, Long sectionId, String className, String sectionName,
			Integer rollNo, String guardianName, String guardianPhone, boolean hasPhoto) {
	}

	public record GuardianView(Long id, Guardian.Relation relation, String fullName, String occupation, String phone,
			String email, boolean primary) {

		public static GuardianView of(Guardian g) {
			return new GuardianView(g.getId(), g.getRelation(), g.getFullName(), g.getOccupation(), g.getPhone(),
					g.getEmail(), g.isPrimary());
		}

	}

	public record EnrollmentView(Long id, Long academicYearId, String academicYearName, Long sectionId, Long classId,
			String className, String sectionName, Integer rollNo, Enrollment.Status status, LocalDate joinedOn,
			LocalDate leftOn) {
	}

	/**
	 * Full record. Sensitive fields (category, religion, caste, Aadhaar) are null
	 * unless {@code sensitiveVisible}; the full Aadhaar number is never returned.
	 */
	public record Detail(Long id, String admissionNo, LocalDate admissionDate, String firstName, String lastName,
			String fullName, LocalDate dateOfBirth, Student.Gender gender, String motherTongue, boolean sensitiveVisible,
			Student.SocialCategory socialCategory, String religion, String caste, String aadhaarMasked,
			String identificationMarks, String address, String previousSchool, String previousTcNo,
			Student.Status status, LocalDate leftOn, String leavingReason, boolean hasPhoto, long photoVersion,
			boolean whatsappConsent, List<GuardianView> guardians, EnrollmentView currentEnrollment,
			List<EnrollmentView> enrollments) {
	}

	/**
	 * Who to message about a student: the primary contact's mobile number (or the
	 * first contact with one) and whether the parent agreed to WhatsApp messages.
	 */
	public record Contact(Long studentId, String fullName, String phone, boolean whatsappConsent) {
	}

	public record LeaveRequest(
			@NotNull(message = "Date is required") @PastOrPresent(message = "Cannot be in the future") LocalDate leftOn,
			@NotBlank(message = "Reason is required") @Size(max = 300) String reason) {
	}

	public record SectionChangeRequest(@NotNull(message = "Section is required") Long sectionId,
			@Min(1) @Max(999) Integer rollNo) {
	}

	/** One student of a section, for registers, promotion and (later) attendance. */
	public record SectionStudent(Long enrollmentId, Long studentId, String admissionNo, String fullName,
			Student.Gender gender, Integer rollNo, Enrollment.Status status, boolean hasPhoto) {
	}

	public record PromotionRequest(
			@NotNull(message = "Source section is required") Long sourceSectionId,
			@NotNull(message = "Target section is required") Long targetSectionId,
			@NotNull List<@NotNull Long> promoteEnrollmentIds,
			/** Optional: same class in the target year, where detained students are placed. */
			Long detainSectionId) {
	}

	public record PromotionResult(int promoted, int detained, int placedInDetainSection) {
	}

	public record ImportError(int row, String field, String message) {
	}

	public record ImportReport(boolean dryRun, int totalRows, int validRows, int created, List<ImportError> errors) {
	}

}
