package com.schoolerp.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.academic.entity.ClassSubject;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {

	List<ClassSubject> findBySchoolIdAndAcademicYearIdAndClassIdOrderByDisplayOrderAsc(Long schoolId,
			Long academicYearId, Long classId);

	List<ClassSubject> findBySchoolIdAndAcademicYearId(Long schoolId, Long academicYearId);

	boolean existsBySchoolIdAndAcademicYearIdAndClassIdAndSubjectId(Long schoolId, Long academicYearId,
			Long classId, Long subjectId);

	boolean existsBySchoolIdAndAcademicYearId(Long schoolId, Long academicYearId);

	boolean existsBySchoolIdAndClassId(Long schoolId, Long classId);

	boolean existsBySchoolIdAndSubjectId(Long schoolId, Long subjectId);

}
