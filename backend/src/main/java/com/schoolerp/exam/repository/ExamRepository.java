package com.schoolerp.exam.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.exam.entity.Exam;

public interface ExamRepository extends JpaRepository<Exam, Long> {

	Optional<Exam> findByIdAndSchoolId(Long id, Long schoolId);

	List<Exam> findBySchoolIdAndAcademicYearIdOrderByDisplayOrderAscIdAsc(Long schoolId, Long academicYearId);

	boolean existsBySchoolIdAndAcademicYearIdAndNameIgnoreCase(Long schoolId, Long academicYearId, String name);

	boolean existsBySchoolIdAndAcademicYearIdAndNameIgnoreCaseAndIdNot(Long schoolId, Long academicYearId, String name,
			Long id);

	boolean existsBySchoolIdAndGradingScaleId(Long schoolId, Long gradingScaleId);

}
