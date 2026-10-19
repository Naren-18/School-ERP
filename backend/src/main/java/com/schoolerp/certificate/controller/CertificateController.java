package com.schoolerp.certificate.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.auth.Access;
import com.schoolerp.certificate.dto.CertificateDto;
import com.schoolerp.certificate.dto.TemplateDto;
import com.schoolerp.certificate.entity.TemplateType;
import com.schoolerp.certificate.service.CertificateService;
import com.schoolerp.certificate.service.TemplateService;
import com.schoolerp.common.dto.PageResponse;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.common.pdf.PdfResponse;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Issuing and re-downloading certificates (principal/admin); editing their templates (principal). */
@RestController
@RequestMapping("/api/v1/certificates")
@RequiresModule(Module.CERTIFICATES)
@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
@RequiredArgsConstructor
public class CertificateController {

	private final CertificateService certificates;

	private final TemplateService templates;

	/** The register. */
	@GetMapping
	PageResponse<CertificateDto.Issued> list(@RequestParam(required = false) TemplateType type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return certificates.list(type, from, to, q, page, size);
	}

	@GetMapping("/{id}/pdf")
	ResponseEntity<byte[]> pdf(@PathVariable Long id) {
		CertificateService.Pdf pdf = certificates.pdf(id);
		return PdfResponse.inline(pdf.fileName(), pdf.content());
	}

	@GetMapping("/students/{studentId}")
	List<CertificateDto.Issued> forStudent(@PathVariable Long studentId) {
		return certificates.forStudent(studentId);
	}

	@PostMapping("/students/{studentId}/preview")
	ResponseEntity<byte[]> preview(@PathVariable Long studentId, @Valid @RequestBody CertificateDto.IssueRequest request) {
		return PdfResponse.inline("preview.pdf", certificates.preview(studentId, request));
	}

	@PostMapping("/students/{studentId}")
	@ResponseStatus(HttpStatus.CREATED)
	CertificateDto.Issued issue(@PathVariable Long studentId, @Valid @RequestBody CertificateDto.IssueRequest request) {
		return certificates.issue(studentId, request);
	}

	@GetMapping("/templates/{type}")
	@PreAuthorize(Access.PRINCIPAL)
	TemplateDto.View template(@PathVariable TemplateType type) {
		return templates.get(certificateType(type));
	}

	@PutMapping("/templates/{type}")
	@PreAuthorize(Access.PRINCIPAL)
	TemplateDto.View saveTemplate(@PathVariable TemplateType type, @Valid @RequestBody TemplateDto.SaveRequest request) {
		TemplateType t = certificateType(type);
		return templates.save(t, request.html(), html -> certificates.sample(t, html));
	}

	/** Back to the built-in default. */
	@DeleteMapping("/templates/{type}")
	@PreAuthorize(Access.PRINCIPAL)
	TemplateDto.View resetTemplate(@PathVariable TemplateType type) {
		return templates.reset(certificateType(type));
	}

	/** A draft filled with sample data, for the editor's live preview. */
	@PostMapping("/templates/{type}/preview")
	@PreAuthorize(Access.PRINCIPAL)
	ResponseEntity<byte[]> previewTemplate(@PathVariable TemplateType type, @Valid @RequestBody TemplateDto.SaveRequest request) {
		TemplateType t = certificateType(type);
		return PdfResponse.inline("preview.pdf", templates.preview(t, request.html(), html -> certificates.sample(t, html)));
	}

	private static TemplateType certificateType(TemplateType type) {
		if (!type.isCertificate()) {
			throw new NotFoundException("Template");
		}
		return type;
	}

}
