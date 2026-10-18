package com.schoolerp.audit.entity;

import java.time.OffsetDateTime;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "audit_log")
public class AuditLog extends TenantEntity {

	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false, length = 30)
	private String action;

	@Column(nullable = false, length = 40)
	private String entity;

	@Column(name = "entity_id")
	private Long entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> details;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

}
