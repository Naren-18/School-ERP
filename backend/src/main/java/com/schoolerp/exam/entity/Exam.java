package com.schoolerp.exam.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An exam in a year (FA1, SA1, Term 1). Weightage counts it in the final result; null = not counted. */
@Getter
@Setter
@Entity
@Table(name = "exam")
public class Exam extends TenantEntity {

	@Column(name = "academic_year_id", nullable = false)
	private Long academicYearId;

	@Column(nullable = false, length = 40)
	private String name;

	@Column(name = "grading_scale_id", nullable = false)
	private Long gradingScaleId;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(precision = 5, scale = 2)
	private BigDecimal weightage;

	@Column(name = "is_published", nullable = false)
	private boolean published;

}
