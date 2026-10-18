package com.schoolerp.school.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.schoolerp.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The tenant itself. Not a {@code TenantEntity}: it is the root of isolation. */
@Getter
@Setter
@Entity
@Table(name = "school")
public class School extends BaseEntity {

	public enum Board { STATE, CBSE, ICSE, OTHER }

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, length = 40, unique = true)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Board board;

	@Column(nullable = false, length = 30)
	private String medium = "ENGLISH";

	private String address;

	@Column(length = 15)
	private String phone;

	@Column(length = 120)
	private String email;

	@Column(name = "logo_url")
	private String logoUrl;

	@Column(name = "primary_color", length = 9)
	private String primaryColor = "#1e40af";

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private SchoolSettings settings;

	@Column(nullable = false)
	private boolean active = true;

	@PrePersist
	void applyDefaultSettings() {
		if (settings == null) {
			settings = SchoolSettings.defaults(code);
		}
	}

	/** Settings with defaults applied for anything not yet configured. */
	public SchoolSettings getSettings() {
		return (settings == null ? SchoolSettings.defaults(code) : settings).withDefaults(code);
	}

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

}
