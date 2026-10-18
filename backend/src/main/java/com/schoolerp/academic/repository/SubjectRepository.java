package com.schoolerp.academic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.academic.entity.Subject;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

	Optional<Subject> findByIdAndSchoolId(Long id, Long schoolId);

	List<Subject> findBySchoolIdOrderByNameAsc(Long schoolId);

	List<Subject> findBySchoolIdAndIdIn(Long schoolId, Collection<Long> ids);

	boolean existsBySchoolIdAndNameIgnoreCase(Long schoolId, String name);

	boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(Long schoolId, String name, Long id);

}
