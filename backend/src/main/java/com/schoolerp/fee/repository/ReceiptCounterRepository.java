package com.schoolerp.fee.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Gap-free receipt numbers per school and academic year. The counter row is
 * locked FOR UPDATE inside the caller's transaction, so concurrent payments
 * queue up, and a payment that rolls back also rolls back its number.
 */
@Repository
@RequiredArgsConstructor
public class ReceiptCounterRepository {

	private final JdbcTemplate jdbc;

	/** Takes the next number. Must run inside the transaction that saves the receipt. */
	@Transactional(propagation = Propagation.MANDATORY)
	public long next(Long schoolId, Long academicYearId) {
		jdbc.update("""
				INSERT INTO receipt_counter (school_id, academic_year_id) VALUES (?, ?)
				ON CONFLICT (school_id, academic_year_id) DO NOTHING
				""", schoolId, academicYearId);
		Long value = jdbc.queryForObject("""
				SELECT next_value FROM receipt_counter
				WHERE school_id = ? AND academic_year_id = ? FOR UPDATE
				""", Long.class, schoolId, academicYearId);
		jdbc.update("UPDATE receipt_counter SET next_value = next_value + 1 WHERE school_id = ? AND academic_year_id = ?",
				schoolId, academicYearId);
		return value;
	}

}
