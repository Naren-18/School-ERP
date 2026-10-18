package com.schoolerp.student.entity;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "guardian")
public class Guardian extends TenantEntity {

	public enum Relation { FATHER, MOTHER, GUARDIAN }

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Relation relation;

	@Column(name = "full_name", nullable = false, length = 120)
	private String fullName;

	@Column(length = 80)
	private String occupation;

	@Column(length = 15)
	private String phone;

	@Column(length = 120)
	private String email;

	@Column(name = "is_primary", nullable = false)
	private boolean primary;

}
