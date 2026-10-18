package com.schoolerp.exam.entity;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A named set of grade bands, e.g. "CBSE 9-point". Its bands live in {@link GradingBand}. */
@Getter
@Setter
@Entity
@Table(name = "grading_scale")
public class GradingScale extends TenantEntity {

	@Column(nullable = false, length = 60)
	private String name;

}
