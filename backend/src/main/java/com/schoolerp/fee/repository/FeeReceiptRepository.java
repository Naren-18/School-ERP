package com.schoolerp.fee.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.fee.entity.FeeReceipt;

public interface FeeReceiptRepository extends JpaRepository<FeeReceipt, Long> {

	Optional<FeeReceipt> findByIdAndSchoolId(Long id, Long schoolId);

	List<FeeReceipt> findBySchoolIdAndEnrollmentIdInOrderByPaidOnDescIdDesc(Long schoolId,
			Collection<Long> enrollmentIds);

	@Query("""
			SELECT r FROM FeeReceipt r
			WHERE r.schoolId = :schoolId AND r.paidOn BETWEEN :from AND :to
			  AND (:q IS NULL OR lower(r.receiptNo) LIKE :q)
			  AND (:cancelled IS NULL OR r.cancelled = :cancelled)
			ORDER BY r.paidOn DESC, r.id DESC
			""")
	Page<FeeReceipt> search(@Param("schoolId") Long schoolId, @Param("from") LocalDate from,
			@Param("to") LocalDate to, @Param("q") String q, @Param("cancelled") Boolean cancelled,
			Pageable pageable);

}
