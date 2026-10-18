package com.schoolerp.academic.entity;

import java.time.LocalDate;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "holiday")
public class Holiday extends TenantEntity {

	@Column(name = "academic_year_id", nullable = false)
	private Long academicYearId;

	@Column(name = "holiday_date", nullable = false)
	private LocalDate date;

	@Column(nullable = false, length = 100)
	private String description;

}
