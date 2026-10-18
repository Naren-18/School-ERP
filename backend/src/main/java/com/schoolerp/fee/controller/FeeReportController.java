package com.schoolerp.fee.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.auth.Access;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.service.FeeReportService;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/fees/reports")
@RequiresModule(Module.FEES)
@PreAuthorize(Access.FEE_STAFF)
@RequiredArgsConstructor
public class FeeReportController {

	private final FeeReportService reports;

	/** Money received per day and mode; defaults to today. */
	@GetMapping("/collection")
	FeeDto.CollectionReport collection(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reports.collection(from, to);
	}

	/** Students with an outstanding balance in the year (current by default). */
	@GetMapping("/dues")
	List<FeeDto.DueRow> dues(@RequestParam(required = false) Long academicYearId,
			@RequestParam(required = false) Long classId, @RequestParam(required = false) Long sectionId,
			@RequestParam(defaultValue = "false") boolean overdueOnly) {
		return reports.dues(academicYearId, classId, sectionId, overdueOnly);
	}

	@GetMapping("/class-summary")
	List<FeeDto.ClassSummary> classSummary(@RequestParam(required = false) Long academicYearId) {
		return reports.classSummary(academicYearId);
	}

}
