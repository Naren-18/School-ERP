package com.schoolerp.academic.entity;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A division of a class in one academic year, e.g. Class 5 "A" in 2026-27. */
@Getter
@Setter
@Entity
@Table(name = "section")
public class Section extends TenantEntity {

	@Column(name = "academic_year_id", nullable = false)
	private Long academicYearId;

	@Column(name = "class_id", nullable = false)
	private Long classId;

	@Column(nullable = false, length = 10)
	private String name;

	@Column(name = "class_teacher_id")
	private Long classTeacherId;

	private Integer capacity;

}
