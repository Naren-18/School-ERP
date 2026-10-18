package com.schoolerp.academic.entity;

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
@Table(name = "subject")
public class Subject extends TenantEntity {

	public enum Type { CORE, LANGUAGE, ELECTIVE, CO_CURRICULAR }

	@Column(nullable = false, length = 60)
	private String name;

	@Column(length = 15)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Type type = Type.CORE;

}
