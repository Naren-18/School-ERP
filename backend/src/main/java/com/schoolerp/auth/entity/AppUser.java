package com.schoolerp.auth.entity;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A staff login. Roles live in {@code user_role}, mapped as an element collection. */
@Getter
@Setter
@Entity
@Table(name = "app_user")
public class AppUser extends TenantEntity {

	@Column(nullable = false, length = 60)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 120)
	private String fullName;

	@Column(length = 60)
	private String designation;

	@Column(length = 15)
	private String phone;

	@Column(length = 120)
	private String email;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "last_login_at")
	private OffsetDateTime lastLoginAt;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Set<Role> roles = EnumSet.noneOf(Role.class);

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

}
