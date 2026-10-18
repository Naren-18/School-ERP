package com.schoolerp.academic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.academic.entity.TeacherAssignment;

public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Long> {

	Optional<TeacherAssignment> findByIdAndSchoolId(Long id, Long schoolId);

	Optional<TeacherAssignment> findBySchoolIdAndSectionIdAndSubjectId(Long schoolId, Long sectionId,
			Long subjectId);

	List<TeacherAssignment> findBySchoolIdAndSectionIdIn(Long schoolId, Collection<Long> sectionIds);

	List<TeacherAssignment> findBySchoolIdAndSectionIdInAndSubjectIdIn(Long schoolId, Collection<Long> sectionIds,
			Collection<Long> subjectIds);

	boolean existsBySchoolIdAndSectionId(Long schoolId, Long sectionId);

}
