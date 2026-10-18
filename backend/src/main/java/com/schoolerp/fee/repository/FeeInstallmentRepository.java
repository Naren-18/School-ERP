package com.schoolerp.fee.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.fee.entity.FeeInstallment;

public interface FeeInstallmentRepository extends JpaRepository<FeeInstallment, Long> {

	Optional<FeeInstallment> findByIdAndSchoolId(Long id, Long schoolId);

	List<FeeInstallment> findBySchoolIdAndAcademicYearIdOrderByDueDateAscIdAsc(Long schoolId, Long academicYearId);

	List<FeeInstallment> findBySchoolIdAndAcademicYearIdAndClassIdOrderByDueDateAscIdAsc(Long schoolId,
			Long academicYearId, Long classId);

	List<FeeInstallment> findBySchoolIdAndIdIn(Long schoolId, Collection<Long> ids);

	boolean existsBySchoolIdAndAcademicYearIdAndClassIdAndFeeHeadIdAndNameIgnoreCase(Long schoolId,
			Long academicYearId, Long classId, Long feeHeadId, String name);

	boolean existsBySchoolIdAndAcademicYearIdAndClassIdAndFeeHeadIdAndNameIgnoreCaseAndIdNot(Long schoolId,
			Long academicYearId, Long classId, Long feeHeadId, String name, Long id);

	boolean existsBySchoolIdAndFeeHeadId(Long schoolId, Long feeHeadId);

}
