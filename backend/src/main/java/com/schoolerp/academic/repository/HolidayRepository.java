package com.schoolerp.academic.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.academic.entity.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

	Optional<Holiday> findByIdAndSchoolId(Long id, Long schoolId);

	List<Holiday> findBySchoolIdAndAcademicYearIdOrderByDateAsc(Long schoolId, Long academicYearId);

	boolean existsBySchoolIdAndDate(Long schoolId, LocalDate date);

	boolean existsBySchoolIdAndDateAndIdNot(Long schoolId, LocalDate date, Long id);

	boolean existsBySchoolIdAndAcademicYearId(Long schoolId, Long academicYearId);

	@Query("""
			SELECT count(h) > 0 FROM Holiday h
			WHERE h.schoolId = :schoolId AND h.academicYearId = :yearId AND (h.date < :start OR h.date > :end)
			""")
	boolean existsOutsideRange(@Param("schoolId") Long schoolId, @Param("yearId") Long yearId,
			@Param("start") LocalDate start, @Param("end") LocalDate end);

}
