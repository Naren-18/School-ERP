package com.schoolerp.school.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.school.dto.BrandingResponse;
import com.schoolerp.school.repository.SchoolRepository;
import com.schoolerp.school.service.SchoolService;

import lombok.RequiredArgsConstructor;

/** Unauthenticated endpoints used by the login page. */
@RestController
@RequestMapping("/api/v1/public/schools")
@RequiredArgsConstructor
public class PublicSchoolController {

	private final SchoolRepository schools;

	private final SchoolService schoolService;

	@GetMapping("/{code}/branding")
	BrandingResponse branding(@PathVariable String code) {
		return schools.findByCodeIgnoreCase(code)
			.filter(school -> school.isActive())
			.map(BrandingResponse::of)
			.orElseThrow(() -> new NotFoundException("School"));
	}

	/** Logo file names change on every upload, so they can be cached for a long time. */
	@GetMapping("/{code}/logo/{fileName}")
	ResponseEntity<byte[]> logo(@PathVariable String code, @PathVariable String fileName) {
		SchoolService.Logo logo = schoolService.publicLogo(code, fileName);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(logo.contentType()))
			.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
			.header("X-Content-Type-Options", "nosniff")
			.body(logo.content());
	}

}
