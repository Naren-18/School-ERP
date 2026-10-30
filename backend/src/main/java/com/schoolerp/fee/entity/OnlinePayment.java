package com.schoolerp.fee.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A payment started with the gateway; becomes a receipt once the gateway confirms it. */
@Getter
@Setter
@Entity
@Table(name = "online_payment")
public class OnlinePayment extends TenantEntity {

	public enum Status { CREATED, PAID, FAILED }

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Column(nullable = false, length = 20)
	private String gateway;

	@Column(name = "order_id", nullable = false, length = 60)
	private String orderId;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	/** Due id (as text) → amount, fixed when the order was created, e.g. {"41": "500.00"}. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, String> lines;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Status status = Status.CREATED;

	@Column(name = "payment_id", length = 60)
	private String paymentId;

	@Column(name = "receipt_id")
	private Long receiptId;

	@Column(length = 300)
	private String failure;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

}
