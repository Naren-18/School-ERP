package com.schoolerp.exam.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A subject paper of an exam for one class: maximum and pass marks, date. */
@Getter
@Setter
@Entity
@Table(name = "exam_subject")
public class ExamSubject extends TenantEntity {

	@Column(name = "exam_id", nullable = false)
	private Long examId;

	@Column(name = "class_id", nullable = false)
	private Long classId;

	@Column(name = "subject_id", nullable = false)
	private Long subjectId;

	@Column(name = "max_marks", nullable = false, precision = 5, scale = 2)
	private BigDecimal maxMarks;

	@Column(name = "pass_marks", precision = 5, scale = 2)
	private BigDecimal passMarks;

	@Column(name = "exam_date")
	private LocalDate examDate;

}
