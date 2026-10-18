package com.schoolerp.exam.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A student's marks in one exam subject. Absent means no marks (the DB enforces it). */
@Getter
@Setter
@Entity
@Table(name = "mark")
public class Mark extends TenantEntity {

	@Column(name = "exam_subject_id", nullable = false)
	private Long examSubjectId;

	@Column(name = "enrollment_id", nullable = false)
	private Long enrollmentId;

	@Column(name = "marks_obtained", precision = 5, scale = 2)
	private BigDecimal marksObtained;

	@Column(name = "is_absent", nullable = false)
	private boolean absent;

	@Column(length = 150)
	private String remarks;

	@Column(name = "entered_by")
	private Long enteredBy;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

}
