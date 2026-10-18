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

import com.schoolerp.academic.dto.ClassDto;
import com.schoolerp.academic.service.ClassService;
import com.schoolerp.auth.Access;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
public class ClassController {

	private final ClassService service;

	@GetMapping
	@PreAuthorize(Access.ANY_STAFF)
	List<ClassDto.Response> list() {
		return service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ClassDto.Response create(@Valid @RequestBody ClassDto.Request request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ClassDto.Response update(@PathVariable Long id, @Valid @RequestBody ClassDto.Request request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
