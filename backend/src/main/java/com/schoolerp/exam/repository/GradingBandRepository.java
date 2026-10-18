package com.schoolerp.exam.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.exam.entity.GradingBand;

/** Bands have no school_id; only query them by ids of scales already loaded for this school. */
public interface GradingBandRepository extends JpaRepository<GradingBand, Long> {

	List<GradingBand> findByGradingScaleIdInOrderByMinPercentDesc(Collection<Long> scaleIds);

	void deleteByGradingScaleId(Long scaleId);

}
