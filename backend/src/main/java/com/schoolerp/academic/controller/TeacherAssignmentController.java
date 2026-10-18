package com.schoolerp.academic.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.academic.dto.TeacherAssignmentDto;
import com.schoolerp.academic.service.TeacherAssignmentService;
import com.schoolerp.auth.Access;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/teacher-assignments")
@RequiredArgsConstructor
public class TeacherAssignmentController {

	private final TeacherAssignmentService service;

	@GetMapping
	@PreAuthorize(Access.ANY_STAFF)
	List<TeacherAssignmentDto.Response> list(@RequestParam(required = false) Long academicYearId,
			@RequestParam(required = false) Long sectionId, @RequestParam(required = false) Long teacherId) {
		return service.list(academicYearId, sectionId, teacherId);
	}

	/** Sets the teacher for a section + subject, replacing any existing one. */
	@PutMapping
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	TeacherAssignmentDto.Response assign(@Valid @RequestBody TeacherAssignmentDto.Request request) {
		return service.assign(request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
