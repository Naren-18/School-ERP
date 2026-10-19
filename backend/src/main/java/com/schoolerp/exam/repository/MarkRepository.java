package com.schoolerp.exam.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.exam.entity.Mark;

public interface MarkRepository extends JpaRepository<Mark, Long> {

	List<Mark> findBySchoolIdAndExamSubjectIdAndEnrollmentIdIn(Long schoolId, Long examSubjectId,
			Collection<Long> enrollmentIds);

	List<Mark> findBySchoolIdAndExamSubjectIdInAndEnrollmentIdIn(Long schoolId, Collection<Long> examSubjectIds,
			Collection<Long> enrollmentIds);

	boolean existsBySchoolIdAndExamSubjectIdIn(Long schoolId, Collection<Long> examSubjectIds);

	long countBySchoolIdAndExamSubjectIdAndEnrollmentIdIn(Long schoolId, Long examSubjectId,
			Collection<Long> enrollmentIds);

	/** Rows of [paperId, sectionId, marks entered for active students]; one query for a whole exam. */
	@Query(value = """
			SELECT m.exam_subject_id, e.section_id, count(*)
			FROM mark m
			JOIN enrollment e ON e.id = m.enrollment_id AND e.school_id = :schoolId AND e.status = 'ACTIVE'
			WHERE m.school_id = :schoolId AND m.exam_subject_id IN (:paperIds)
			GROUP BY m.exam_subject_id, e.section_id
			""", nativeQuery = true)
	List<Object[]> enteredBySection(@Param("schoolId") Long schoolId, @Param("paperIds") Collection<Long> paperIds);

	/** Rows of [sectionId, active students] for the year's sections. */
	@Query(value = """
			SELECT e.section_id, count(*)
			FROM enrollment e
			WHERE e.school_id = :schoolId AND e.academic_year_id = :yearId AND e.status = 'ACTIVE'
			GROUP BY e.section_id
			""", nativeQuery = true)
	List<Object[]> activeBySection(@Param("schoolId") Long schoolId, @Param("yearId") Long yearId);

}
