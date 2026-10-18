package com.schoolerp.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.academic.entity.AcademicYear;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

	Optional<AcademicYear> findByIdAndSchoolId(Long id, Long schoolId);

	List<AcademicYear> findBySchoolIdOrderByStartDateDesc(Long schoolId);

	Optional<AcademicYear> findBySchoolIdAndCurrentTrue(Long schoolId);

	boolean existsBySchoolIdAndNameIgnoreCase(Long schoolId, String name);

	boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(Long schoolId, String name, Long id);

	long countBySchoolId(Long schoolId);

	@Modifying
	@Query("UPDATE AcademicYear y SET y.current = false WHERE y.schoolId = :schoolId AND y.current = true")
	void clearCurrent(@Param("schoolId") Long schoolId);

}
