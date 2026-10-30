package com.schoolerp.fee.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.fee.entity.OnlinePayment;

import jakarta.persistence.LockModeType;

public interface OnlinePaymentRepository extends JpaRepository<OnlinePayment, Long> {

	Optional<OnlinePayment> findByIdAndSchoolId(Long id, Long schoolId);

	/** Locked FOR UPDATE, so a payment completed twice at once makes only one receipt. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM OnlinePayment p WHERE p.id = :id AND p.schoolId = :schoolId")
	Optional<OnlinePayment> lockByIdAndSchoolId(@Param("id") Long id, @Param("schoolId") Long schoolId);

}
