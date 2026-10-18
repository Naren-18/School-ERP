package com.schoolerp.exam.entity;

import java.math.BigDecimal;

import com.schoolerp.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One grade of a scale. The table has no school_id: bands are only read
 * through a {@link GradingScale} already loaded through the tenant filter.
 */
@Getter
@Setter
@Entity
@Table(name = "grading_band")
public class GradingBand extends BaseEntity {

	@Column(name = "grading_scale_id", nullable = false)
	private Long gradingScaleId;

	@Column(name = "min_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal minPercent;

	@Column(name = "max_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal maxPercent;

	@Column(nullable = false, length = 5)
	private String grade;

	@Column(name = "grade_point", precision = 4, scale = 2)
	private BigDecimal gradePoint;

	@Column(length = 60)
	private String remark;

}
