package com.schoolerp.student.entity;

import java.time.LocalDate;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A student's place in one academic year: section and roll number. One per student per year. */
@Getter
@Setter
@Entity
@Table(name = "enrollment")
public class Enrollment extends TenantEntity {

	public enum Status { ACTIVE, PROMOTED, DETAINED, LEFT }

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Column(name = "academic_year_id", nullable = false)
	private Long academicYearId;

	@Column(name = "section_id", nullable = false)
	private Long sectionId;

	@Column(name = "roll_no")
	private Integer rollNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status = Status.ACTIVE;

	@Column(name = "joined_on", nullable = false)
	private LocalDate joinedOn;

	@Column(name = "left_on")
	private LocalDate leftOn;

}
