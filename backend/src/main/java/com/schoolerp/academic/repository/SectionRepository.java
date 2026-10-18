package com.schoolerp.academic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.academic.entity.Section;

public interface SectionRepository extends JpaRepository<Section, Long> {

	Optional<Section> findByIdAndSchoolId(Long id, Long schoolId);

	List<Section> findBySchoolIdAndAcademicYearId(Long schoolId, Long academicYearId);

	List<Section> findBySchoolIdAndIdIn(Long schoolId, Collection<Long> ids);

	boolean existsBySchoolIdAndAcademicYearIdAndClassIdAndNameIgnoreCase(Long schoolId, Long academicYearId,
			Long classId, String name);

	boolean existsBySchoolIdAndAcademicYearIdAndClassIdAndNameIgnoreCaseAndIdNot(Long schoolId,
			Long academicYearId, Long classId, String name, Long id);

	List<Section> findBySchoolIdAndClassTeacherId(Long schoolId, Long classTeacherId);

	List<Section> findBySchoolIdAndAcademicYearIdAndClassId(Long schoolId, Long academicYearId, Long classId);

	Optional<Section> findBySchoolIdAndAcademicYearIdAndClassIdAndNameIgnoreCase(Long schoolId, Long academicYearId,
			Long classId, String name);

	boolean existsBySchoolIdAndAcademicYearId(Long schoolId, Long academicYearId);

	boolean existsBySchoolIdAndClassId(Long schoolId, Long classId);

}
