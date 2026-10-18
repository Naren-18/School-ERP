package com.schoolerp.academic.controller;

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

import com.schoolerp.academic.dto.ClassSubjectDto;
import com.schoolerp.academic.dto.SubjectDto;
import com.schoolerp.academic.service.SubjectService;
import com.schoolerp.auth.Access;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubjectController {

	private final SubjectService service;

	@GetMapping("/subjects")
	@PreAuthorize(Access.ANY_STAFF)
	List<SubjectDto.Response> list() {
		return service.list();
	}

	@PostMapping("/subjects")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	SubjectDto.Response create(@Valid @RequestBody SubjectDto.Request request) {
		return service.create(request);
	}

	@PutMapping("/subjects/{id}")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	SubjectDto.Response update(@PathVariable Long id, @Valid @RequestBody SubjectDto.Request request) {
		return service.update(id, request);
	}

	@DeleteMapping("/subjects/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

	@GetMapping("/class-subjects")
	@PreAuthorize(Access.ANY_STAFF)
	List<ClassSubjectDto.Response> classSubjects(@RequestParam Long academicYearId, @RequestParam Long classId) {
		return service.classSubjects(academicYearId, classId);
	}

	@PutMapping("/class-subjects")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	List<ClassSubjectDto.Response> setClassSubjects(@Valid @RequestBody ClassSubjectDto.Request request) {
		return service.setClassSubjects(request);
	}

}
