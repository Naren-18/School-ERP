package com.schoolerp.certificate.dto;

import java.time.LocalDate;

import com.schoolerp.certificate.entity.TemplateType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class CertificateDto {

	private CertificateDto() {
	}

	/**
	 * @param tc required for a TC; ignored otherwise
	 */
	public record IssueRequest(@NotNull(message = "Choose the certificate") TemplateType type,
			@Size(max = 150) String purpose, @Valid TcDetails tc) {
	}

	/**
	 * @param leavingDate required when the student is still active (issuing the TC marks them as left)
	 */
	public record TcDetails(@PastOrPresent(message = "Cannot be in the future") LocalDate leavingDate,
			@Size(max = 300) String reason, @Size(max = 60) String conduct, @Size(max = 100) String promotion,
			@Size(max = 300) String remarks) {
	}

	public record Issued(Long id, Long studentId, String studentName, String admissionNo, TemplateType type,
			String title, String serialNo, LocalDate issuedOn, String issuedByName, String purpose) {
	}

}
