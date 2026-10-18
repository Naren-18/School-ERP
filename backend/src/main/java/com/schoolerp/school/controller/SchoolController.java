package com.schoolerp.school.controller;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.schoolerp.school.dto.SchoolProfileResponse;
import com.schoolerp.school.dto.UpdateSchoolProfileRequest;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.school.service.SchoolService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The signed-in user's own school. Everyone can read it; only the principal can change it. */
@RestController
@RequestMapping("/api/v1/school")
@RequiredArgsConstructor
public class SchoolController {

	private final SchoolService schoolService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	SchoolProfileResponse get() {
		return schoolService.current();
	}

	@PutMapping("/profile")
	@PreAuthorize("hasRole('PRINCIPAL')")
	SchoolProfileResponse updateProfile(@Valid @RequestBody UpdateSchoolProfileRequest request) {
		return schoolService.updateProfile(request);
	}

	@PutMapping("/settings")
	@PreAuthorize("hasRole('PRINCIPAL')")
	SchoolProfileResponse updateSettings(@Valid @RequestBody SchoolSettings settings) {
		return schoolService.updateSettings(settings);
	}

	@PostMapping(path = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('PRINCIPAL')")
	SchoolProfileResponse uploadLogo(@RequestParam("file") MultipartFile file) {
		return schoolService.uploadLogo(file);
	}

}
