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

import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.auth.Access;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/sections")
@RequiredArgsConstructor
public class SectionController {

	private final SectionService service;

	/** Sections of the given year (current year by default), optionally of one class. */
	@GetMapping
	@PreAuthorize(Access.ANY_STAFF)
	List<SectionDto.Response> list(@RequestParam(required = false) Long academicYearId,
			@RequestParam(required = false) Long classId) {
		return service.list(academicYearId, classId);
	}

	@GetMapping("/{id}")
	@PreAuthorize(Access.ANY_STAFF)
	SectionDto.Response get(@PathVariable Long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	SectionDto.Response create(@Valid @RequestBody SectionDto.Request request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	SectionDto.Response update(@PathVariable Long id, @Valid @RequestBody SectionDto.Request request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
