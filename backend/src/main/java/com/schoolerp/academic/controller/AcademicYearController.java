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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.academic.dto.AcademicYearDto;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.auth.Access;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/academic-years")
@RequiredArgsConstructor
public class AcademicYearController {

	private final AcademicYearService service;

	@GetMapping
	@PreAuthorize(Access.ANY_STAFF)
	List<AcademicYearDto.Response> list() {
		return service.list();
	}

	@GetMapping("/current")
	@PreAuthorize(Access.ANY_STAFF)
	AcademicYearDto.Response current() {
		return service.current();
	}

	@GetMapping("/{id}")
	@PreAuthorize(Access.ANY_STAFF)
	AcademicYearDto.Response get(@PathVariable Long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	AcademicYearDto.Response create(@Valid @RequestBody AcademicYearDto.Request request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	AcademicYearDto.Response update(@PathVariable Long id, @Valid @RequestBody AcademicYearDto.Request request) {
		return service.update(id, request);
	}

	@PostMapping("/{id}/make-current")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	AcademicYearDto.Response makeCurrent(@PathVariable Long id) {
		return service.makeCurrent(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
