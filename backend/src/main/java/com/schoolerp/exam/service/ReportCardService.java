package com.schoolerp.exam.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.attendance.dto.AttendanceDto;
import com.schoolerp.attendance.service.AttendanceReportService;
import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.auth.service.StaffService;
import com.schoolerp.certificate.entity.TemplateType;
import com.schoolerp.certificate.service.TemplateDocument;
import com.schoolerp.certificate.service.TemplateService;
import com.schoolerp.certificate.service.TemplateValues;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.common.pdf.HtmlPdf;
import com.schoolerp.common.template.Placeholders;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.entity.Exam;
import com.schoolerp.exam.entity.ReportCardNote;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.service.StudentService;

import lombok.RequiredArgsConstructor;

/**
 * Report card PDFs: one page per student, for one exam or for the whole year
 * (every published exam plus the weighted final), printed through the
 * school's report card template. Rank and attendance follow school settings;
 * remarks and co-scholastic grades come from the exam's notes (the annual card
 * uses the last published exam's).
 */
@Service
@RequiredArgsConstructor
public class ReportCardService {

	private final ResultService results;

	private final ExamService examService;

	private final ReportCardNoteService notes;

	private final SectionService sectionService;

	private final AcademicYearService yearService;

	private final StudentService studentService;

	private final SchoolService schoolService;

	private final StaffService staff;

	private final AttendanceReportService attendance;

	private final TemplateService templates;

	private final TemplateValues templateValues;

	private final ExamAccess access;

	private final AuditService audit;

	private final Clock clock;

	/**
	 * @param examId  one exam (must be published), or null for the year's consolidated card
	 * @param studentId one student of the section, or null for the whole section in one PDF
	 */
	// Not read-only: issuing report cards is written to the audit log.
	@Transactional
	public byte[] pdf(Long sectionId, Long examId, Long studentId) {
		Section section = sectionService.require(sectionId);
		access.requireResultsView(section);
		AcademicYear year = yearService.require(section.getAcademicYearId());
		Exam exam = examId == null ? null : examService.require(examId);
		if (exam != null && !exam.isPublished()) {
			throw new ConflictException("Publish " + exam.getName() + " before printing its report cards");
		}
		if (exam != null && !exam.getAcademicYearId().equals(year.getId())) {
			throw new NotFoundException("Exam");
		}

		List<StudentDto.SectionStudent> roster = results.roster(sectionId)
			.stream()
			.filter(s -> studentId == null || s.studentId().equals(studentId))
			.toList();
		if (roster.isEmpty()) {
			throw new NotFoundException(studentId == null ? "Students" : "Student");
		}
		School school = schoolService.requireCurrent();
		SchoolSettings settings = school.getSettings();
		List<Long> enrollmentIds = roster.stream().map(StudentDto.SectionStudent::enrollmentId).toList();
		Map<Long, ResultService.YearResults> yearResults = results.yearResults(year.getId(), section.getClassId(),
				enrollmentIds, true);
		Map<Long, Integer> ranks = rankForCard(sectionId, exam, yearResults);
		Long notesExamId = exam != null ? exam.getId()
				: yearResults.values().stream().findFirst()
					.flatMap(yr -> yr.exams().isEmpty() ? Optional.<Long>empty() : Optional.of(yr.exams().getLast().examId()))
					.orElse(null);
		Map<Long, ReportCardNote> noteByEnrollment = notesExamId == null ? Map.of() : notes.notesFor(notesExamId, enrollmentIds);
		List<ExamDto.Area> areas = notes.areas();
		String classTeacher = section.getClassTeacherId() == null ? ""
				: staff.fullNames(List.of(section.getClassTeacherId())).getOrDefault(section.getClassTeacherId(), "");
		String template = templates.activeHtml(TemplateType.REPORT_CARD);
		Map<String, String> schoolValues = templateValues.school(school);
		String logo = templateValues.logoBlock();
		LocalDate attendanceTo = min(year.getEndDate(), LocalDate.now(clock));
		String title = exam == null ? "Annual Progress Report " + year.getName()
				: "Progress Report – " + exam.getName() + " (" + year.getName() + ")";

		List<String> pages = new ArrayList<>();
		for (StudentDto.SectionStudent s : roster) {
			StudentDto.Detail detail = studentService.get(s.studentId());
			StudentDto.EnrollmentView enrollment = detail.enrollments().stream()
				.filter(e -> e.id().equals(s.enrollmentId()))
				.findFirst()
				.orElse(TemplateValues.classOf(detail));
			ResultService.YearResults yr = yearResults.get(s.enrollmentId());
			AttendanceDto.Counts att = settings.reportCard().showAttendance() && settings.modules().attendance()
					? attendance.summary(List.of(s.enrollmentId()), year.getStartDate(), attendanceTo)
					: null;
			Integer rank = settings.reportCard().showRank() ? ranks.get(s.enrollmentId()) : null;
			ReportCardNote note = noteByEnrollment.get(s.enrollmentId());

			Map<String, String> v = new LinkedHashMap<>(schoolValues);
			v.putAll(templateValues.student(detail, enrollment));
			Map<String, String> blocks = new LinkedHashMap<>();
			blocks.put("school.logo", logo);
			if (exam != null) {
				ExamDto.ExamSummary summary = yr.exams().stream().filter(e -> e.examId().equals(exam.getId())).findFirst().orElseThrow();
				fill(v, blocks, title, exam.getName(), examTable(summary.result()), summary.result().percent(),
						summary.result().grade(), summary.result().passed(), rank);
			}
			else {
				fill(v, blocks, title, "Annual", yearTable(yr), yr.finalResult().percent(), yr.finalResult().grade(),
						yr.finalResult().passed(), rank);
			}
			notesAndAttendance(v, blocks, att, note, areas, classTeacher);
			pages.add(Placeholders.render(template, v, blocks));
		}
		audit.log(AuditAction.ISSUE, "report_card", sectionId,
				Map.of("exam", exam == null ? "final" : exam.getName(), "students", roster.size()));
		return HtmlPdf.render(TemplateDocument.wrap(pages, school.getPrimaryColor()));
	}

	/** The template editor's preview: a made-up student with two subjects. */
	public byte[] sample(String html) {
		School school = schoolService.requireCurrent();
		List<ResultCalculator.Band> bands = GradingPresets.bands(GradingPresets.Preset.CBSE_9_POINT);
		ResultCalculator.ExamResult r = ResultCalculator.exam(
				List.of(new ResultCalculator.Paper(1L, 1L, "English", new BigDecimal("50"), new BigDecimal("17")),
						new ResultCalculator.Paper(2L, 2L, "Mathematics", new BigDecimal("50"), new BigDecimal("17"))),
				Map.of(1L, new ResultCalculator.Entry(new BigDecimal("43"), false), 2L,
						new ResultCalculator.Entry(new BigDecimal("38.5"), false)),
				bands);
		Map<String, String> v = new LinkedHashMap<>(templateValues.school(school));
		v.putAll(Map.of("student.fullName", "Sample Student", "student.admissionNo", "1234", "student.class", "Class 5 – A",
				"student.className", "Class 5", "student.sectionName", "A", "student.rollNo", "7", "student.dateOfBirth",
				"03-02-2016", "student.guardianName", "Sample Parent", "student.academicYear", "2026-27"));
		Map<String, String> blocks = new LinkedHashMap<>();
		blocks.put("school.logo", templateValues.logoBlock());
		fill(v, blocks, "Progress Report – FA1 (2026-27)", "FA1", examTable(r), r.percent(), r.grade(), r.passed(), 3);
		List<ExamDto.Area> areas = List.of(new ExamDto.Area(1L, "Art Education", 1), new ExamDto.Area(2L, "Discipline", 2));
		ReportCardNote note = new ReportCardNote();
		note.setTeacherRemark("Attentive in class; can read more at home.");
		note.setCoScholastic(Map.of("1", "A", "2", "B"));
		notesAndAttendance(v, blocks, new AttendanceDto.Counts(170, 6, 2, 2, 0, 95.6), note, areas,
				"Class Teacher");
		return HtmlPdf.render(TemplateDocument.wrap(List.of(Placeholders.render(html, v, blocks)), school.getPrimaryColor()));
	}

	private static void fill(Map<String, String> v, Map<String, String> blocks, String title, String examName, String table,
			BigDecimal percent, String grade, Boolean passed, Integer rank) {
		v.put("report.title", title);
		v.put("report.examName", examName);
		v.put("result.percent", percent == null ? "" : num(percent) + "%");
		v.put("result.grade", grade == null ? "" : grade);
		v.put("result.status", passed == null ? "Incomplete" : passed ? "PASS" : "NOT PASSED");
		v.put("result.rank", rank == null ? "" : rank.toString());
		blocks.put("marks.table", table);
		blocks.put("result.summary", overall(percent, grade, passed, rank));
	}

	private static void notesAndAttendance(Map<String, String> v, Map<String, String> blocks, AttendanceDto.Counts att,
			ReportCardNote note, List<ExamDto.Area> areas, String classTeacher) {
		String attendanceText = att == null || att.percentage() == null ? ""
				: (att.present() + att.halfDay()) + " of " + (att.present() + att.absent() + att.leave() + att.halfDay())
						+ " days (" + att.percentage() + "%)";
		v.put("attendance.summary", attendanceText);
		blocks.put("attendance.line",
				attendanceText.isEmpty() ? "" : "<p>Attendance: <b>" + HtmlPdf.esc(attendanceText) + "</b></p>");
		String remark = note == null || note.getTeacherRemark() == null ? "" : note.getTeacherRemark();
		v.put("teacher.remark", remark);
		blocks.put("remark.line",
				remark.isEmpty() ? "" : "<p>Class teacher's remark: <i>" + HtmlPdf.esc(remark) + "</i></p>");
		v.put("classTeacher.name", classTeacher);
		blocks.put("coScholastic.table", coScholasticTable(areas, note == null ? Map.of() : ReportCardNoteService.grades(note)));
	}

	/** Rank by the exam's % (complete results only), or by the final % for the year card. */
	private Map<Long, Integer> rankForCard(Long sectionId, Exam exam, Map<Long, ResultService.YearResults> yr) {
		if (exam != null) {
			return results.sectionSheet(exam.getId(), sectionId)
				.rows()
				.stream()
				.filter(r -> r.rank() != null)
				.collect(Collectors.toMap(ExamDto.ResultRow::enrollmentId, ExamDto.ResultRow::rank));
		}
		return ResultCalculator.rank(List.copyOf(yr.keySet()), id -> yr.get(id).finalResult().percent());
	}

	private static String examTable(ResultCalculator.ExamResult r) {
		StringBuilder rows = new StringBuilder();
		for (ResultCalculator.SubjectResult s : r.subjects()) {
			rows.append("<tr><td>").append(HtmlPdf.esc(s.subjectName())).append("</td><td class=\"num\">")
				.append(num(s.maxMarks())).append("</td><td class=\"num\">")
				.append(!s.entered() ? "–" : s.absent() ? "AB" : num(s.marks())).append("</td><td class=\"c\">")
				.append(s.grade() == null ? "–" : HtmlPdf.esc(s.grade())).append("</td><td class=\"c\">")
				.append(s.passed() == null ? "–" : s.passed() ? "Pass" : "<b class=\"fail\">Fail</b>").append("</td></tr>");
		}
		return """
				<table class="grid"><tr><th>Subject</th><th class="num">Max</th><th class="num">Marks</th><th class="c">Grade</th><th class="c">Result</th></tr>
				%s
				<tr class="total"><td>Total</td><td class="num">%s</td><td class="num">%s</td><td></td><td></td></tr></table>
				""".formatted(rows, num(r.maxTotal()), num(r.total()));
	}

	private static String yearTable(ResultService.YearResults yr) {
		StringBuilder head = new StringBuilder("<tr><th>Subject</th>");
		yr.exams().forEach(e -> head.append("<th class=\"c\">").append(HtmlPdf.esc(e.examName()))
			.append(e.weightage() == null ? "" : " (" + num(e.weightage()) + "%)").append("</th>"));
		head.append("<th class=\"c\">Final</th></tr>");
		StringBuilder rows = new StringBuilder();
		for (Map.Entry<Long, String> subject : yr.subjectNames().entrySet()) {
			rows.append("<tr><td>").append(HtmlPdf.esc(subject.getValue())).append("</td>");
			for (ExamDto.ExamSummary e : yr.exams()) {
				ResultCalculator.SubjectResult s = e.result().subjects().stream()
					.filter(x -> x.subjectId().equals(subject.getKey()))
					.findFirst()
					.orElse(null);
				rows.append("<td class=\"c\">").append(cell(s)).append("</td>");
			}
			String grade = yr.finalResult().subjectGrade().get(subject.getKey());
			rows.append("<td class=\"c\"><b>").append(grade == null ? "–" : HtmlPdf.esc(grade)).append("</b></td></tr>");
		}
		return "<table class=\"grid\">" + head + rows + "</table>";
	}

	private static String coScholasticTable(List<ExamDto.Area> areas, Map<Long, String> grades) {
		if (areas.isEmpty()) {
			return "";
		}
		StringBuilder rows = new StringBuilder();
		for (ExamDto.Area a : areas) {
			String g = grades.get(a.id());
			rows.append("<tr><td>").append(HtmlPdf.esc(a.name())).append("</td><td class=\"c\">")
				.append(g == null ? "–" : HtmlPdf.esc(g)).append("</td></tr>");
		}
		return "<table class=\"grid\"><tr><th>Co-scholastic area</th><th class=\"c\" style=\"width: 25%;\">Grade</th></tr>"
				+ rows + "</table>";
	}

	private static String cell(ResultCalculator.SubjectResult s) {
		if (s == null || !s.entered()) {
			return "–";
		}
		if (s.absent()) {
			return "AB";
		}
		return num(s.marks()) + "/" + num(s.maxMarks()) + (s.grade() == null ? "" : " " + HtmlPdf.esc(s.grade()));
	}

	private static String overall(BigDecimal percent, String grade, Boolean passed, Integer rank) {
		String result = passed == null ? "Incomplete" : passed ? "PASS" : "<span class=\"fail\">NOT PASSED</span>";
		return "<table class=\"grid\"><tr><td class=\"c\">Percentage <b>" + (percent == null ? "–" : num(percent) + "%")
				+ "</b></td><td class=\"c\">Grade <b>" + (grade == null ? "–" : HtmlPdf.esc(grade)) + "</b></td><td class=\"c\">Result <b>"
				+ result + "</b></td>" + (rank == null ? "" : "<td class=\"c\">Rank <b>" + rank + "</b></td>") + "</tr></table>";
	}

	/** 45.50 → "45.5", 50.00 → "50". */
	private static String num(BigDecimal v) {
		return v == null ? "–" : v.stripTrailingZeros().toPlainString();
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

}
