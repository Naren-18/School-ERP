package com.schoolerp.student.controller;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.schoolerp.auth.Access;
import com.schoolerp.common.dto.PageResponse;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Student;
import com.schoolerp.student.service.EnrollmentService;
import com.schoolerp.student.service.StudentImportService;
import com.schoolerp.student.service.StudentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * Students. Everyone can read (teachers only their sections' students, see
 * StudentAccess); principal and admin manage.
 */
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {

	private final StudentService studentService;

	private final EnrollmentService enrollmentService;

	private final StudentImportService importService;

	@GetMapping
	@PreAuthorize(Access.ANY_STAFF)
	PageResponse<StudentDto.ListItem> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) Long academicYearId, @RequestParam(required = false) Long classId,
			@RequestParam(required = false) Long sectionId,
			@RequestParam(required = false, defaultValue = "ACTIVE") Student.Status status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return studentService.search(q, academicYearId, classId, sectionId, status, page, size);
	}

	@GetMapping("/next-admission-no")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	Map<String, String> nextAdmissionNo() {
		return Map.of("admissionNo", studentService.nextAdmissionNo());
	}

	@GetMapping("/{id}")
	@PreAuthorize(Access.ANY_STAFF)
	StudentDto.Detail get(@PathVariable Long id) {
		return studentService.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	StudentDto.Detail create(@Valid @RequestBody StudentDto.CreateRequest request) {
		return studentService.create(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	StudentDto.Detail update(@PathVariable Long id, @Valid @RequestBody StudentDto.Details request) {
		return studentService.update(id, request);
	}

	@PostMapping("/{id}/leave")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	StudentDto.Detail leave(@PathVariable Long id, @Valid @RequestBody StudentDto.LeaveRequest request) {
		return studentService.markLeft(id, request);
	}

	@PostMapping("/{id}/enrollments")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	StudentDto.EnrollmentView enroll(@PathVariable Long id, @Valid @RequestBody StudentDto.Placement placement) {
		return enrollmentService.enrollExisting(id, placement);
	}

	@PostMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	StudentDto.Detail uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
		return studentService.uploadPhoto(id, file);
	}

	/** Private to signed-in staff who may see the student; cached only by the browser. */
	@GetMapping("/{id}/photo")
	@PreAuthorize(Access.ANY_STAFF)
	ResponseEntity<byte[]> photo(@PathVariable Long id) {
		StudentService.Photo photo = studentService.photo(id);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(photo.contentType()))
			.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
			.header("X-Content-Type-Options", "nosniff")
			.body(photo.content());
	}

	@GetMapping("/import/template")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	ResponseEntity<byte[]> importTemplate() {
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename("students-template.csv").build().toString())
			.body(("﻿" + importService.template()).getBytes(StandardCharsets.UTF_8));
	}

	/** Validates the CSV; with {@code dryRun=false} and no errors, admits every student in it. */
	@PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	StudentDto.ImportReport importCsv(@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "true") boolean dryRun) {
		return importService.importCsv(file, dryRun);
	}

}
