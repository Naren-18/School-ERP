package com.schoolerp.student.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.schoolerp.common.crypto.EncryptedStringConverter;
import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The permanent person record. Year-by-year placement lives in {@link Enrollment}. */
@Getter
@Setter
@Entity
@Table(name = "student")
public class Student extends TenantEntity {

	public enum Gender { MALE, FEMALE, OTHER }

	public enum Status { ACTIVE, LEFT, TC_ISSUED, PASSED_OUT }

	public enum SocialCategory { GEN, OBC, BC, SC, ST, OTHER }

	@Column(name = "admission_no", nullable = false, length = 30)
	private String admissionNo;

	@Column(name = "admission_date", nullable = false)
	private LocalDate admissionDate;

	@Column(name = "first_name", nullable = false, length = 60)
	private String firstName;

	@Column(name = "last_name", length = 60)
	private String lastName;

	@Column(name = "date_of_birth", nullable = false)
	private LocalDate dateOfBirth;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Gender gender;

	@Column(name = "mother_tongue", length = 30)
	private String motherTongue;

	@Enumerated(EnumType.STRING)
	@Column(name = "social_category", length = 10)
	private SocialCategory socialCategory;

	@Column(length = 30)
	private String religion;

	@Column(length = 50)
	private String caste;

	/** Full number, encrypted at rest. Never returned by the API. */
	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "aadhaar_encrypted")
	private String aadhaar;

	@Column(name = "aadhaar_last4", length = 4, columnDefinition = "bpchar(4)")
	private String aadhaarLast4;

	@Column(name = "identification_marks")
	private String identificationMarks;

	private String address;

	/** Storage key of the current photo, e.g. {@code schools/3/students/41/photo-1718.jpg}. */
	@Column(name = "photo_url")
	private String photoKey;

	@Column(name = "previous_school", length = 150)
	private String previousSchool;

	@Column(name = "previous_tc_no", length = 50)
	private String previousTcNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status = Status.ACTIVE;

	@Column(name = "left_on")
	private LocalDate leftOn;

	@Column(name = "leaving_reason")
	private String leavingReason;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	public String getFullName() {
		return lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName;
	}

	public void setAadhaarNumber(String digits) {
		this.aadhaar = digits;
		this.aadhaarLast4 = digits == null ? null : digits.substring(digits.length() - 4);
	}

}
