package com.schoolerp.fee.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A payment. Never deleted, only cancelled, so the receipt book stays honest. */
@Getter
@Setter
@Entity
@Table(name = "fee_receipt")
public class FeeReceipt extends TenantEntity {

	@Column(name = "academic_year_id", nullable = false)
	private Long academicYearId;

	@Column(name = "receipt_no", nullable = false, length = 30)
	private String receiptNo;

	@Column(name = "enrollment_id", nullable = false)
	private Long enrollmentId;

	@Column(name = "paid_on", nullable = false)
	private LocalDate paidOn;

	@Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_mode", nullable = false, length = 10)
	private PaymentMode paymentMode;

	@Column(name = "reference_no", length = 60)
	private String referenceNo;

	@Column(name = "collected_by", nullable = false)
	private Long collectedBy;

	@Column(nullable = false)
	private boolean cancelled;

	@Column(name = "cancelled_by")
	private Long cancelledBy;

	@Column(name = "cancelled_at")
	private OffsetDateTime cancelledAt;

	@Column(name = "cancel_reason", length = 200)
	private String cancelReason;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

}
