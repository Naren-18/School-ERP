package com.schoolerp.student.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.student.entity.Enrollment;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

	Optional<Enrollment> findByIdAndSchoolId(Long id, Long schoolId);

	Optional<Enrollment> findBySchoolIdAndStudentIdAndAcademicYearId(Long schoolId, Long studentId,
			Long academicYearId);

	List<Enrollment> findBySchoolIdAndStudentIdOrderByJoinedOnDesc(Long schoolId, Long studentId);

	List<Enrollment> findBySchoolIdAndSectionIdAndStatus(Long schoolId, Long sectionId, Enrollment.Status status);

	List<Enrollment> findBySchoolIdAndSectionIdAndStatusIn(Long schoolId, Long sectionId,
			Collection<Enrollment.Status> statuses);

	List<Enrollment> findBySchoolIdAndAcademicYearIdAndStudentIdIn(Long schoolId, Long academicYearId,
			Collection<Long> studentIds);

	boolean existsBySchoolIdAndSectionIdAndRollNoAndStatusAndIdNot(Long schoolId, Long sectionId, Integer rollNo,
			Enrollment.Status status, Long id);

	boolean existsBySchoolIdAndSectionIdAndRollNoAndStatus(Long schoolId, Long sectionId, Integer rollNo,
			Enrollment.Status status);

	boolean existsBySchoolIdAndSectionId(Long schoolId, Long sectionId);

	long countBySchoolIdAndSectionId(Long schoolId, Long sectionId);

	long countBySchoolIdAndSectionIdAndStatus(Long schoolId, Long sectionId, Enrollment.Status status);

	boolean existsBySchoolIdAndStudentIdAndSectionIdIn(Long schoolId, Long studentId, Collection<Long> sectionIds);

	/** Enrollments of a section that overlap [from, to]: joined by {@code to}, not left before {@code from}. */
	@Query("""
			SELECT e FROM Enrollment e
			WHERE e.schoolId = :schoolId AND e.sectionId = :sectionId
			  AND e.joinedOn <= :to AND (e.leftOn IS NULL OR e.leftOn >= :from)
			""")
	List<Enrollment> findInSectionDuring(@Param("schoolId") Long schoolId, @Param("sectionId") Long sectionId,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** A student's enrollments that overlap [from, to]. */
	@Query("""
			SELECT e FROM Enrollment e
			WHERE e.schoolId = :schoolId AND e.studentId = :studentId
			  AND e.joinedOn <= :to AND (e.leftOn IS NULL OR e.leftOn >= :from)
			""")
	List<Enrollment> findForStudentDuring(@Param("schoolId") Long schoolId, @Param("studentId") Long studentId,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query("""
			SELECT coalesce(max(e.rollNo), 0) FROM Enrollment e
			WHERE e.schoolId = :schoolId AND e.sectionId = :sectionId AND e.status = 'ACTIVE'
			""")
	int maxRollNo(@Param("schoolId") Long schoolId, @Param("sectionId") Long sectionId);

}
