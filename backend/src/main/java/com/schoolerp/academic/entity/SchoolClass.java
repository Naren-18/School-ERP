package com.schoolerp.academic.entity;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A grade such as Nursery, LKG or Class 5 ("class" is a Java keyword). */
@Getter
@Setter
@Entity
@Table(name = "school_class")
public class SchoolClass extends TenantEntity {

	@Column(nullable = false, length = 30)
	private String name;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

}
