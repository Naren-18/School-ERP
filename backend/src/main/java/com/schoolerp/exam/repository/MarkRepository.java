package com.schoolerp.exam.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.exam.entity.Mark;

public interface MarkRepository extends JpaRepository<Mark, Long> {

	List<Mark> findBySchoolIdAndExamSubjectIdAndEnrollmentIdIn(Long schoolId, Long examSubjectId,
			Collection<Long> enrollmentIds);

	List<Mark> findBySchoolIdAndExamSubjectIdInAndEnrollmentIdIn(Long schoolId, Collection<Long> examSubjectIds,
			Collection<Long> enrollmentIds);

	boolean existsBySchoolIdAndExamSubjectIdIn(Long schoolId, Collection<Long> examSubjectIds);

	long countBySchoolIdAndExamSubjectIdAndEnrollmentIdIn(Long schoolId, Long examSubjectId,
			Collection<Long> enrollmentIds);

}
