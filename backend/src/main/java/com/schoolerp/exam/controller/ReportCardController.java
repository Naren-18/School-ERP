package com.schoolerp.exam.controller;

import java.util.List;

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
import com.schoolerp.certificate.dto.TemplateDto;
import com.schoolerp.certificate.entity.TemplateType;
import com.schoolerp.certificate.service.TemplateService;
import com.schoolerp.common.pdf.PdfResponse;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.service.ReportCardNoteService;
import com.schoolerp.exam.service.ReportCardService;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Report card extras: co-scholastic areas (principal/admin), per-exam remarks
 * and grades (class teacher or principal/admin), and the report card template
 * (principal).
 */
@RestController
@RequestMapping("/api/v1/exams")
@RequiresModule(Module.EXAMS)
@RequiredArgsConstructor
public class ReportCardController {

	private final ReportCardNoteService notes;

	private final ReportCardService reportCards;

	private final TemplateService templates;

	@GetMapping("/co-scholastic-areas")
	@PreAuthorize(Access.TEACHING_STAFF)
	List<ExamDto.Area> areas() {
		return notes.areas();
	}

	@PostMapping("/co-scholastic-areas")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Area createArea(@Valid @RequestBody ExamDto.AreaRequest request) {
		return notes.createArea(request);
	}

	@PutMapping("/co-scholastic-areas/{id}")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Area updateArea(@PathVariable Long id, @Valid @RequestBody ExamDto.AreaRequest request) {
		return notes.updateArea(id, request);
	}

	@DeleteMapping("/co-scholastic-areas/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	void deleteArea(@PathVariable Long id) {
		notes.deleteArea(id);
	}

	@GetMapping("/{examId}/notes")
	@PreAuthorize(Access.TEACHING_STAFF)
	ExamDto.NoteSheet notes(@PathVariable Long examId, @RequestParam Long sectionId) {
		return notes.sheet(examId, sectionId);
	}

	@PutMapping("/{examId}/notes")
	@PreAuthorize(Access.TEACHING_STAFF)
	ExamDto.NoteSheet saveNotes(@PathVariable Long examId, @RequestParam Long sectionId,
			@Valid @RequestBody ExamDto.SaveNotes request) {
		return notes.save(examId, sectionId, request);
	}

	@GetMapping("/report-card-template")
	@PreAuthorize(Access.PRINCIPAL)
	TemplateDto.View template() {
		return templates.get(TemplateType.REPORT_CARD);
	}

	@PutMapping("/report-card-template")
	@PreAuthorize(Access.PRINCIPAL)
	TemplateDto.View saveTemplate(@Valid @RequestBody TemplateDto.SaveRequest request) {
		return templates.save(TemplateType.REPORT_CARD, request.html(), reportCards::sample);
	}

	@DeleteMapping("/report-card-template")
	@PreAuthorize(Access.PRINCIPAL)
	TemplateDto.View resetTemplate() {
		return templates.reset(TemplateType.REPORT_CARD);
	}

	@PostMapping("/report-card-template/preview")
	@PreAuthorize(Access.PRINCIPAL)
	ResponseEntity<byte[]> previewTemplate(@Valid @RequestBody TemplateDto.SaveRequest request) {
		return PdfResponse.inline("preview.pdf", templates.preview(TemplateType.REPORT_CARD, request.html(), reportCards::sample));
	}

}
