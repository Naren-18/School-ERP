package com.schoolerp.student.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.auth.Access;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.service.EnrollmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EnrollmentController {

	private final EnrollmentService enrollmentService;

	/** The section's students by roll number. Teachers: only sections they teach. */
	@GetMapping("/sections/{sectionId}/students")
	@PreAuthorize(Access.ANY_STAFF)
	List<StudentDto.SectionStudent> sectionStudents(@PathVariable Long sectionId,
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return enrollmentService.sectionStudents(sectionId, includeInactive);
	}

	@PostMapping("/sections/{sectionId}/renumber")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	List<StudentDto.SectionStudent> renumber(@PathVariable Long sectionId) {
		return enrollmentService.renumber(sectionId);
	}

	@PutMapping("/enrollments/{id}/section")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	StudentDto.EnrollmentView changeSection(@PathVariable Long id,
			@Valid @RequestBody StudentDto.SectionChangeRequest request) {
		return enrollmentService.changeSection(id, request);
	}

	@PostMapping("/promotions")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	StudentDto.PromotionResult promote(@Valid @RequestBody StudentDto.PromotionRequest request) {
		return enrollmentService.promote(request);
	}

}
