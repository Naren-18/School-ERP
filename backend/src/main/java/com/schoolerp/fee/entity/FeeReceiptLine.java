package com.schoolerp.fee.entity;

import java.math.BigDecimal;

import com.schoolerp.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One due paid on a receipt. The table has no school_id: it is a child of
 * {@link FeeReceipt} and is only ever read by the ids of receipts that were
 * already loaded through the tenant filter.
 */
@Getter
@Setter
@Entity
@Table(name = "fee_receipt_line")
public class FeeReceiptLine extends BaseEntity {

	@Column(name = "receipt_id", nullable = false)
	private Long receiptId;

	@Column(name = "student_fee_due_id", nullable = false)
	private Long studentFeeDueId;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

}
