package com.schoolerp.academic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.academic.entity.SchoolClass;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

	Optional<SchoolClass> findByIdAndSchoolId(Long id, Long schoolId);

	List<SchoolClass> findBySchoolIdOrderByDisplayOrderAscNameAsc(Long schoolId);

	List<SchoolClass> findBySchoolIdAndIdIn(Long schoolId, Collection<Long> ids);

	boolean existsBySchoolIdAndNameIgnoreCase(Long schoolId, String name);

	boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(Long schoolId, String name, Long id);

}
