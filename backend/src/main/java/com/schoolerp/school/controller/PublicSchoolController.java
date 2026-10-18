package com.schoolerp.school.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.school.dto.BrandingResponse;
import com.schoolerp.school.repository.SchoolRepository;

import lombok.RequiredArgsConstructor;

/** Unauthenticated endpoints used by the login page. */
@RestController
@RequestMapping("/api/v1/public/schools")
@RequiredArgsConstructor
public class PublicSchoolController {

	private final SchoolRepository schools;

	@GetMapping("/{code}/branding")
	BrandingResponse branding(@PathVariable String code) {
		return schools.findByCodeIgnoreCase(code)
			.filter(school -> school.isActive())
			.map(BrandingResponse::of)
			.orElseThrow(() -> new NotFoundException("School"));
	}

}
