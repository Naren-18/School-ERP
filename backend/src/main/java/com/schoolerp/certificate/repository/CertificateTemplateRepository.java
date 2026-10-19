package com.schoolerp.certificate.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.certificate.entity.CertificateTemplate;
import com.schoolerp.certificate.entity.TemplateType;

public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, Long> {

	Optional<CertificateTemplate> findBySchoolIdAndTypeAndActiveTrue(Long schoolId, TemplateType type);

}
