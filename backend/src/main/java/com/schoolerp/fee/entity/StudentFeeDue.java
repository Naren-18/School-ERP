package com.schoolerp.fee.entity;

import java.math.BigDecimal;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * What one enrolled student owes for one installment. The database enforces
 * {@code 0 <= concession <= amount} and {@code 0 <= paid <= amount - concession}.
 */
@Getter
@Setter
@Entity
@Table(name = "student_fee_due")
public class StudentFeeDue extends TenantEntity {

	@Column(name = "enrollment_id", nullable = false)
	private Long enrollmentId;

	@Column(name = "fee_installment_id", nullable = false)
	private Long feeInstallmentId;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal concession = BigDecimal.ZERO;

	@Column(name = "concession_reason", length = 100)
	private String concessionReason;

	@Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal paidAmount = BigDecimal.ZERO;

	/** Amount after concession. */
	public BigDecimal getNetAmount() {
		return amount.subtract(concession);
	}

	/** Still to pay. */
	public BigDecimal getBalance() {
		return getNetAmount().subtract(paidAmount);
	}

}
