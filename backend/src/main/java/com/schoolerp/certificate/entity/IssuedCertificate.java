package com.schoolerp.certificate.entity;

import java.time.LocalDate;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A certificate as issued: the values printed are snapshotted in {@code data}
 * and the PDF is stored, so re-downloading gives exactly the same document even
 * after the student's details or the template change.
 */
@Getter
@Setter
@Entity
@Table(name = "issued_certificate")
public class IssuedCertificate extends TenantEntity {

	@Column(name = "student_id", nullable = false)
	private Long studentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TemplateType type;

	@Column(name = "serial_no", nullable = false, length = 30)
	private String serialNo;

	@Column(name = "issued_on", nullable = false)
	private LocalDate issuedOn;

	@Column(name = "issued_by", nullable = false)
	private Long issuedBy;

	@Column(length = 150)
	private String purpose;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, String> data;

	/** FileStorage key of the PDF. */
	@Column(name = "pdf_url")
	private String pdfKey;

}
