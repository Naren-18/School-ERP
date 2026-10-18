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
@Table(name = "academic_year")
public class AcademicYear extends TenantEntity {

	@Column(nullable = false, length = 20)
	private String name;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "is_current", nullable = false)
	private boolean current;

	public boolean contains(LocalDate date) {
		return !date.isBefore(startDate) && !date.isAfter(endDate);
	}

}
