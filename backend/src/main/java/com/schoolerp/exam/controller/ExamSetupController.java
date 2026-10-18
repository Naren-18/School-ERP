package com.schoolerp.exam.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.service.ExamService;
import com.schoolerp.exam.service.GradingPresets;
import com.schoolerp.exam.service.GradingService;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Grading scales, exams and papers. Read: principal, admin, teachers. Change: principal and admin. */
@RestController
@RequestMapping("/api/v1/exams")
@RequiresModule(Module.EXAMS)
@RequiredArgsConstructor
public class ExamSetupController {

	private final GradingService grading;

	private final ExamService exams;

	@GetMapping("/grading-scales")
	@PreAuthorize(Access.TEACHING_STAFF)
	List<ExamDto.Scale> scales() {
		return grading.list();
	}

	@PostMapping("/grading-scales")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Scale createScale(@Valid @RequestBody ExamDto.ScaleRequest request) {
		return grading.create(request);
	}

	@PostMapping("/grading-scales/presets/{preset}")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Scale addPreset(@PathVariable GradingPresets.Preset preset) {
		return grading.addPreset(preset);
	}

	@PutMapping("/grading-scales/{id}")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Scale updateScale(@PathVariable Long id, @Valid @RequestBody ExamDto.ScaleRequest request) {
		return grading.update(id, request);
	}

	@DeleteMapping("/grading-scales/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	void deleteScale(@PathVariable Long id) {
		grading.delete(id);
	}

	@GetMapping
	@PreAuthorize(Access.TEACHING_STAFF)
	List<ExamDto.Exam> list(@RequestParam(required = false) Long academicYearId) {
		return exams.list(academicYearId);
	}

	@GetMapping("/{id}")
	@PreAuthorize(Access.TEACHING_STAFF)
	ExamDto.Exam get(@PathVariable Long id) {
		return exams.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Exam create(@Valid @RequestBody ExamDto.ExamRequest request) {
		return exams.create(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Exam update(@PathVariable Long id, @Valid @RequestBody ExamDto.ExamRequest request) {
		return exams.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	void delete(@PathVariable Long id) {
		exams.delete(id);
	}

	@PostMapping("/{id}/publish")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Exam publish(@PathVariable Long id) {
		return exams.setPublished(id, true);
	}

	@PostMapping("/{id}/unpublish")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Exam unpublish(@PathVariable Long id) {
		return exams.setPublished(id, false);
	}

	@GetMapping("/{id}/papers")
	@PreAuthorize(Access.TEACHING_STAFF)
	List<ExamDto.Paper> papers(@PathVariable Long id, @RequestParam(required = false) Long classId) {
		return exams.papers(id, classId);
	}

	/** Adds or updates the paper for a class + subject. */
	@PutMapping("/{id}/papers")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.Paper savePaper(@PathVariable Long id, @Valid @RequestBody ExamDto.PaperRequest request) {
		return exams.savePaper(id, request);
	}

	@PostMapping("/{id}/papers/apply")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ExamDto.ApplyResult apply(@PathVariable Long id, @Valid @RequestBody ExamDto.ApplyRequest request) {
		return exams.applyToClasses(id, request);
	}

	@DeleteMapping("/papers/{paperId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	void deletePaper(@PathVariable Long paperId) {
		exams.deletePaper(paperId);
	}

}
