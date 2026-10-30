package com.schoolerp.notification.entity;

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

/** One WhatsApp message tried for a student, sent or failed. */
@Getter
@Setter
@Entity
@Table(name = "notification_log")
public class NotificationLog extends TenantEntity {

	public enum Status { SENT, FAILED }

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MessageType type;

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Column(nullable = false, length = 15)
	private String phone;

	/** The day of an absence. */
	@Column(name = "ref_date")
	private LocalDate refDate;

	/** The receipt or exam the message is about. */
	@Column(name = "ref_id")
	private Long refId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Status status;

	@Column(length = 300)
	private String error;

	@Column(name = "provider_message_id", length = 100)
	private String providerMessageId;

	@Column(name = "sent_by")
	private Long sentBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

}
