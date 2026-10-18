package com.schoolerp.academic.entity;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A subject that a class studies in a given academic year. */
@Getter
@Setter
@Entity
@Table(name = "class_subject")
public class ClassSubject extends TenantEntity {

	@Column(name = "academic_year_id", nullable = false)
	private Long academicYearId;

	@Column(name = "class_id", nullable = false)
	private Long classId;

	@Column(name = "subject_id", nullable = false)
	private Long subjectId;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

}
