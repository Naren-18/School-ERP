package com.schoolerp.certificate.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.certificate.entity.TemplateType;

import lombok.RequiredArgsConstructor;

/**
 * Gap-free certificate serial numbers per school and type. The counter row is
 * locked FOR UPDATE inside the issuing transaction, so concurrent issues queue
 * up, and an issue that rolls back also rolls back its number.
 */
@Repository
@RequiredArgsConstructor
public class CertificateCounterRepository {

	private final JdbcTemplate jdbc;

	@Transactional(propagation = Propagation.MANDATORY)
	public long next(Long schoolId, TemplateType type) {
		jdbc.update("""
				INSERT INTO certificate_counter (school_id, type) VALUES (?, ?)
				ON CONFLICT (school_id, type) DO NOTHING
				""", schoolId, type.name());
		Long value = jdbc.queryForObject("""
				SELECT next_value FROM certificate_counter
				WHERE school_id = ? AND type = ? FOR UPDATE
				""", Long.class, schoolId, type.name());
		jdbc.update("UPDATE certificate_counter SET next_value = next_value + 1 WHERE school_id = ? AND type = ?",
				schoolId, type.name());
		return value;
	}

}
