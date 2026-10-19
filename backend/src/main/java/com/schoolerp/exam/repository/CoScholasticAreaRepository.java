package com.schoolerp.exam.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.exam.entity.CoScholasticArea;

public interface CoScholasticAreaRepository extends JpaRepository<CoScholasticArea, Long> {

	Optional<CoScholasticArea> findByIdAndSchoolId(Long id, Long schoolId);

	List<CoScholasticArea> findBySchoolIdOrderByDisplayOrderAscNameAsc(Long schoolId);

	boolean existsBySchoolIdAndNameIgnoreCase(Long schoolId, String name);

	boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(Long schoolId, String name, Long id);

}
