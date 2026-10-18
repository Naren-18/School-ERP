package com.schoolerp.attendance.controller;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.attendance.dto.AttendanceDto;
import com.schoolerp.attendance.service.AttendanceReportService;
import com.schoolerp.attendance.service.AttendanceService;
import com.schoolerp.auth.Access;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Attendance. Dates default to today; months are {@code yyyy-MM}. Hidden when the module is off. */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiresModule(Module.ATTENDANCE)
@RequiredArgsConstructor
public class AttendanceController {

	private final AttendanceService attendanceService;

	private final AttendanceReportService reportService;

	private final Clock clock;

	@GetMapping("/sections")
	@PreAuthorize(Access.TEACHING_STAFF)
	List<AttendanceDto.SectionOption> sections(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return attendanceService.sections(orToday(date));
	}

	@GetMapping("/sections/{sectionId}")
	@PreAuthorize(Access.TEACHING_STAFF)
	AttendanceDto.Sheet sheet(@PathVariable Long sectionId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return attendanceService.sheet(sectionId, orToday(date));
	}

	@PutMapping("/sections/{sectionId}")
	@PreAuthorize(Access.TEACHING_STAFF)
	AttendanceDto.Sheet save(@PathVariable Long sectionId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@Valid @RequestBody AttendanceDto.SaveRequest request) {
		return attendanceService.save(sectionId, orToday(date), request);
	}

	@GetMapping("/sections/{sectionId}/register")
	@PreAuthorize(Access.TEACHING_STAFF)
	AttendanceDto.Register register(@PathVariable Long sectionId, @RequestParam YearMonth month) {
		return reportService.register(sectionId, month);
	}

	@GetMapping("/sections/{sectionId}/register.csv")
	@PreAuthorize(Access.TEACHING_STAFF)
	ResponseEntity<byte[]> registerCsv(@PathVariable Long sectionId, @RequestParam YearMonth month) {
		String csv = "﻿" + reportService.registerCsv(sectionId, month);
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename("attendance-" + month + ".csv").build().toString())
			.body(csv.getBytes(StandardCharsets.UTF_8));
	}

	/** Anyone who may see the student (teachers: their sections only). */
	@GetMapping("/students/{studentId}")
	@PreAuthorize(Access.ANY_STAFF)
	AttendanceDto.StudentMonth studentMonth(@PathVariable Long studentId, @RequestParam YearMonth month) {
		return reportService.studentMonth(studentId, month);
	}

	@GetMapping("/today")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	AttendanceDto.Today today(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return reportService.today(date);
	}

	private LocalDate orToday(LocalDate date) {
		return date != null ? date : LocalDate.now(clock);
	}

}
