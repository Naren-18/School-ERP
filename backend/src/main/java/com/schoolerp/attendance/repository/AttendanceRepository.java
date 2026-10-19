package com.schoolerp.attendance.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.attendance.entity.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

	List<Attendance> findBySchoolIdAndEnrollmentIdInAndDate(Long schoolId, Collection<Long> enrollmentIds,
			LocalDate date);

	List<Attendance> findBySchoolIdAndEnrollmentIdInAndDateBetween(Long schoolId, Collection<Long> enrollmentIds,
			LocalDate from, LocalDate to);

	/** Sections (of the given ones) that have any attendance recorded on the date. */
	@Query(value = """
			SELECT DISTINCT e.section_id FROM attendance a
			JOIN enrollment e ON e.id = a.enrollment_id AND e.school_id = :schoolId
			WHERE a.school_id = :schoolId AND a.att_date = :date AND e.section_id IN (:sectionIds)
			""", nativeQuery = true)
	List<Long> markedSectionIds(@Param("schoolId") Long schoolId, @Param("date") LocalDate date,
			@Param("sectionIds") Collection<Long> sectionIds);

	long countBySchoolIdAndDate(Long schoolId, LocalDate date);

	@Query(value = """
			SELECT COUNT(DISTINCT e.section_id) FROM attendance a
			JOIN enrollment e ON e.id = a.enrollment_id AND e.school_id = :schoolId
			WHERE a.school_id = :schoolId AND a.att_date = :date
			""", nativeQuery = true)
	int countMarkedSections(@Param("schoolId") Long schoolId, @Param("date") LocalDate date);

	@Modifying
	@Query(value = "DELETE FROM attendance WHERE school_id = :schoolId AND att_date = :date", nativeQuery = true)
	int deleteOnDate(@Param("schoolId") Long schoolId, @Param("date") LocalDate date);

}
