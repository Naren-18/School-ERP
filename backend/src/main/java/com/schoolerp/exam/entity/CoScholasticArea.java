package com.schoolerp.exam.entity;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Something graded outside academics, e.g. Art Education, Sports, Discipline. */
@Getter
@Setter
@Entity
@Table(name = "co_scholastic_area")
public class CoScholasticArea extends TenantEntity {

	@Column(nullable = false, length = 60)
	private String name;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

}
