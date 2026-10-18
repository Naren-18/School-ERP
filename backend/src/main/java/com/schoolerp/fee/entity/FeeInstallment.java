package com.schoolerp.fee.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One line of a class's fee plan for a year, e.g. Tuition "Term 1" ₹12,000 due 15-06. */
@Getter
@Setter
@Entity
@Table(name = "fee_installment")
public class FeeInstallment extends TenantEntity {

	@Column(name = "academic_year_id", nullable = false)
	private Long academicYearId;

	@Column(name = "class_id", nullable = false)
	private Long classId;

	@Column(name = "fee_head_id", nullable = false)
	private Long feeHeadId;

	@Column(nullable = false, length = 40)
	private String name;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

}
