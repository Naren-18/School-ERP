package com.schoolerp.fee.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.fee.entity.StudentFeeDue;

import jakarta.persistence.LockModeType;

public interface StudentFeeDueRepository extends JpaRepository<StudentFeeDue, Long> {

	Optional<StudentFeeDue> findByIdAndSchoolId(Long id, Long schoolId);

	List<StudentFeeDue> findBySchoolIdAndIdIn(Long schoolId, Collection<Long> ids);

	List<StudentFeeDue> findBySchoolIdAndEnrollmentIdIn(Long schoolId, Collection<Long> enrollmentIds);

	List<StudentFeeDue> findBySchoolIdAndFeeInstallmentId(Long schoolId, Long feeInstallmentId);

	/** [installmentId, number of students with a due] for each installment that has dues. */
	@Query("""
			SELECT d.feeInstallmentId, count(d) FROM StudentFeeDue d
			WHERE d.schoolId = :schoolId AND d.feeInstallmentId IN :ids GROUP BY d.feeInstallmentId
			""")
	List<Object[]> countByInstallment(@Param("schoolId") Long schoolId, @Param("ids") Collection<Long> ids);

	/**
	 * Loads dues locked FOR UPDATE, in id order (so two payments touching the same
	 * dues queue up instead of deadlocking or losing an update).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT d FROM StudentFeeDue d WHERE d.schoolId = :schoolId AND d.id IN :ids ORDER BY d.id")
	List<StudentFeeDue> lockBySchoolIdAndIdIn(@Param("schoolId") Long schoolId, @Param("ids") Collection<Long> ids);

	boolean existsBySchoolIdAndEnrollmentIdAndFeeInstallmentId(Long schoolId, Long enrollmentId,
			Long feeInstallmentId);

}
