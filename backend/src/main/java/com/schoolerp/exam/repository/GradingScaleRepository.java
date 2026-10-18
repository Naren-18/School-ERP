package com.schoolerp.exam.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.exam.entity.GradingScale;

public interface GradingScaleRepository extends JpaRepository<GradingScale, Long> {

	Optional<GradingScale> findByIdAndSchoolId(Long id, Long schoolId);

	List<GradingScale> findBySchoolIdOrderByNameAsc(Long schoolId);

	boolean existsBySchoolIdAndNameIgnoreCase(Long schoolId, String name);

	boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(Long schoolId, String name, Long id);

}
