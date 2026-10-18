package com.schoolerp.exam.controller;

import java.util.List;

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

import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.auth.Access;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.service.MarksService;
import com.schoolerp.exam.service.ReportCardService;
import com.schoolerp.exam.service.ResultService;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Marks entry, results and report cards. Teachers are limited by ExamAccess. */
@RestController
@RequestMapping("/api/v1/exams")
@RequiresModule(Module.EXAMS)
@PreAuthorize(Access.TEACHING_STAFF)
@RequiredArgsConstructor
public class MarksController {

	private final MarksService marks;

	private final ResultService results;

	private final ReportCardService reportCards;

	@GetMapping("/{examId}/entry-options")
	List<ExamDto.EntryOption> entryOptions(@PathVariable Long examId) {
		return marks.entryOptions(examId);
	}

	@GetMapping("/papers/{paperId}/marks")
	ExamDto.MarkSheet sheet(@PathVariable Long paperId, @RequestParam Long sectionId) {
		return marks.sheet(paperId, sectionId);
	}

	@PutMapping("/papers/{paperId}/marks")
	ExamDto.MarkSheet save(@PathVariable Long paperId, @RequestParam Long sectionId,
			@Valid @RequestBody ExamDto.SaveMarks request) {
		return marks.save(paperId, sectionId, request);
	}

	/** Sections the user may see results for (all for principal/admin). */
	@GetMapping("/result-sections")
	List<SectionDto.Response> resultSections(@RequestParam(required = false) Long academicYearId) {
		return results.sections(academicYearId);
	}

	@GetMapping("/{examId}/results")
	ExamDto.ResultSheet results(@PathVariable Long examId, @RequestParam Long sectionId) {
		return results.sectionSheet(examId, sectionId);
	}

	/** Published exams of a student's year (current by default) and the weighted final. */
	@GetMapping("/students/{studentId}/results")
	ExamDto.StudentResults studentResults(@PathVariable Long studentId,
			@RequestParam(required = false) Long academicYearId) {
		return results.studentResults(studentId, academicYearId);
	}

	/** Report cards as one PDF: a section (or one student), for one published exam or the whole year. */
	@GetMapping("/report-cards.pdf")
	ResponseEntity<byte[]> reportCards(@RequestParam Long sectionId, @RequestParam(required = false) Long examId,
			@RequestParam(required = false) Long studentId) {
		byte[] pdf = reportCards.pdf(sectionId, examId, studentId);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("report-cards.pdf").build().toString())
			.body(pdf);
	}

}
