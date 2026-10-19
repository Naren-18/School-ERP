package com.schoolerp.certificate.entity;

import java.time.OffsetDateTime;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A school's own version of a template. With none, the built-in default is
 * used; "reset to default" deletes the row.
 */
@Getter
@Setter
@Entity
@Table(name = "certificate_template")
public class CertificateTemplate extends TenantEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TemplateType type;

	@Column(name = "html_template", nullable = false)
	private String htmlTemplate;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

}
