package com.schoolerp.certificate.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.certificate.entity.IssuedCertificate;
import com.schoolerp.certificate.entity.TemplateType;

public interface IssuedCertificateRepository extends JpaRepository<IssuedCertificate, Long> {

	Optional<IssuedCertificate> findByIdAndSchoolId(Long id, Long schoolId);

	List<IssuedCertificate> findBySchoolIdAndStudentIdOrderByIssuedOnDescIdDesc(Long schoolId, Long studentId);

	Optional<IssuedCertificate> findFirstBySchoolIdAndStudentIdAndType(Long schoolId, Long studentId, TemplateType type);

	/** The register: newest first, optionally by type, date range and serial / student name / admission no. */
	@Query(value = """
			SELECT c.* FROM issued_certificate c
			JOIN student s ON s.id = c.student_id AND s.school_id = :schoolId
			WHERE c.school_id = :schoolId
			  AND (CAST(:type AS varchar) IS NULL OR c.type = :type)
			  AND (CAST(:from AS date) IS NULL OR c.issued_on >= :from)
			  AND (CAST(:to AS date) IS NULL OR c.issued_on <= :to)
			  AND (CAST(:q AS varchar) IS NULL
			       OR c.serial_no ILIKE '%' || :q || '%'
			       OR s.admission_no ILIKE '%' || :q || '%'
			       OR (s.first_name || ' ' || COALESCE(s.last_name, '')) ILIKE '%' || :q || '%')
			ORDER BY c.issued_on DESC, c.id DESC
			""", countQuery = """
			SELECT COUNT(*) FROM issued_certificate c
			JOIN student s ON s.id = c.student_id AND s.school_id = :schoolId
			WHERE c.school_id = :schoolId
			  AND (CAST(:type AS varchar) IS NULL OR c.type = :type)
			  AND (CAST(:from AS date) IS NULL OR c.issued_on >= :from)
			  AND (CAST(:to AS date) IS NULL OR c.issued_on <= :to)
			  AND (CAST(:q AS varchar) IS NULL
			       OR c.serial_no ILIKE '%' || :q || '%'
			       OR s.admission_no ILIKE '%' || :q || '%'
			       OR (s.first_name || ' ' || COALESCE(s.last_name, '')) ILIKE '%' || :q || '%')
			""", nativeQuery = true)
	Page<IssuedCertificate> search(@Param("schoolId") Long schoolId, @Param("type") String type,
			@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("q") String q, Pageable pageable);

}
