package com.schoolerp.exam.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.common.pdf.HtmlPdf;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.entity.Exam;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.service.StudentService;

import lombok.RequiredArgsConstructor;

/**
 * Report card PDFs: one page per student, for one exam or for the whole year
 * (every published exam plus the weighted final). Built-in layout themed with
 * the school's logo and colour; rank and attendance follow school settings.
 */
@Service
@RequiredArgsConstructor
public class ReportCardService {

	private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private final ResultService results;

	private final ExamService examService;

	private final SectionService sectionService;

	private final AcademicYearService yearService;

	private final StudentService studentService;

	private final SchoolService schoolService;

	private final AttendanceReportService attendance;

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
		ResultService.SectionLabel label = results.label(section);
		Map<Long, ResultService.YearResults> yearResults = results.yearResults(year.getId(), section.getClassId(),
				roster.stream().map(StudentDto.SectionStudent::enrollmentId).toList(), true);
		Map<Long, Integer> ranks = rankForCard(sectionId, exam, yearResults);
		String logo = schoolService.currentLogo().map(l -> HtmlPdf.img(l.content(), l.contentType(), "logo")).orElse("");
		LocalDate attendanceTo = min(year.getEndDate(), LocalDate.now(clock));

		StringBuilder pages = new StringBuilder();
		for (int i = 0; i < roster.size(); i++) {
			StudentDto.SectionStudent s = roster.get(i);
			StudentDto.Detail detail = studentService.get(s.studentId());
			ResultService.YearResults yr = yearResults.get(s.enrollmentId());
			AttendanceDto.Counts att = settings.reportCard().showAttendance() && settings.modules().attendance()
					? attendance.summary(List.of(s.enrollmentId()), year.getStartDate(), attendanceTo)
					: null;
			pages.append(page(school, logo, year, exam, label, s, detail, yr, att,
					settings.reportCard().showRank() ? ranks.get(s.enrollmentId()) : null, i == roster.size() - 1));
		}
		audit.log(AuditAction.ISSUE, "report_card", sectionId,
				Map.of("exam", exam == null ? "final" : exam.getName(), "students", roster.size()));
		return HtmlPdf.render(document(school, pages.toString()));
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

	private static String page(School school, String logo, AcademicYear year, Exam exam, ResultService.SectionLabel label,
			StudentDto.SectionStudent s, StudentDto.Detail d, ResultService.YearResults yr, AttendanceDto.Counts att,
			Integer rank, boolean last) {
		String title = exam == null ? "Annual Progress Report " + year.getName()
				: "Progress Report – " + exam.getName() + " (" + year.getName() + ")";
		String guardian = d.guardians().isEmpty() ? "" : d.guardians().getFirst().fullName();
		String table;
		String overall;
		if (exam != null) {
			ExamDto.ExamSummary summary = yr.exams().stream().filter(e -> e.examId().equals(exam.getId())).findFirst().orElseThrow();
			table = examTable(summary.result());
			overall = overall(summary.result().percent(), summary.result().grade(), summary.result().passed(), rank);
		}
		else {
			table = yearTable(yr);
			overall = overall(yr.finalResult().percent(), yr.finalResult().grade(), yr.finalResult().passed(), rank);
		}
		String attendanceLine = att == null || att.percentage() == null ? ""
				: "<p>Attendance: <b>" + (att.present() + att.halfDay()) + "</b> of <b>"
						+ (att.present() + att.absent() + att.leave() + att.halfDay()) + "</b> school days marked ("
						+ att.percentage() + "%)</p>";
		return """
				<div class="page%s">
				<div class="head">%s<h1>%s</h1><div class="muted">%s</div><div style="clear: both"></div></div>
				<div class="title">%s</div>
				<table class="meta">
				<tr><td>Name <b>%s</b></td><td>Adm. no. <b>%s</b></td></tr>
				<tr><td>Class &amp; section <b>%s</b> · Roll <b>%s</b></td><td>Date of birth <b>%s</b></td></tr>
				<tr><td colspan="2">Parent / guardian <b>%s</b></td></tr>
				</table>
				%s
				%s
				%s
				<table class="sign"><tr><td>Class teacher</td><td class="gap"></td><td>Principal</td><td class="gap"></td><td>Parent</td></tr></table>
				</div>
				""".formatted(last ? "" : " break", logo, HtmlPdf.esc(school.getName()), HtmlPdf.esc(nullToEmpty(school.getAddress())),
				HtmlPdf.esc(title), HtmlPdf.esc(s.fullName()), HtmlPdf.esc(s.admissionNo()), HtmlPdf.esc(label.text()),
				s.rollNo() == null ? "–" : s.rollNo(), d.dateOfBirth().format(DMY), HtmlPdf.esc(guardian), table, overall,
				attendanceLine);
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
				<table class="marks"><tr><th>Subject</th><th class="num">Max</th><th class="num">Marks</th><th class="c">Grade</th><th class="c">Result</th></tr>
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
		return "<table class=\"marks\">" + head + rows + "</table>";
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
		return "<table class=\"overall\"><tr><td>Percentage <b>" + (percent == null ? "–" : num(percent) + "%")
				+ "</b></td><td>Grade <b>" + (grade == null ? "–" : HtmlPdf.esc(grade)) + "</b></td><td>Result <b>" + result
				+ "</b></td>" + (rank == null ? "" : "<td>Rank <b>" + rank + "</b></td>") + "</tr></table>";
	}

	private static String document(School school, String pages) {
		return """
				<!DOCTYPE html>
				<html><head><meta charset="UTF-8"/><style>
				@page { size: A4; margin: 14mm; }
				body { font-family: '%s'; font-size: 10.5pt; color: #111; }
				.break { page-break-after: always; }
				.head { border-bottom: 3px solid %s; padding-bottom: 6px; margin-bottom: 10px; }
				.logo { height: 52px; float: left; margin-right: 12px; }
				h1 { font-size: 17pt; margin: 0; }
				.muted { color: #555; font-size: 9pt; }
				.title { text-align: center; font-weight: 700; font-size: 12.5pt; margin: 8px 0 10px; }
				table { width: 100%%; border-collapse: collapse; }
				.meta td { padding: 3px 0; }
				.marks { margin-top: 10px; }
				.marks th, .marks td { border: 1px solid #bbb; padding: 5px 7px; }
				.marks th { background: #f3f4f6; text-align: left; }
				.num { text-align: right; }
				.c { text-align: center; }
				.total td { font-weight: 700; }
				.overall { margin-top: 10px; }
				.overall td { border: 1px solid #bbb; padding: 7px; text-align: center; }
				.fail { color: #b91c1c; }
				.sign { margin-top: 60px; }
				.sign td { text-align: center; border-top: 1px solid #777; padding-top: 4px; width: 30%%; }
				.sign td.gap { border-top: none; width: 5%%; }
				</style></head><body>%s</body></html>
				""".formatted(HtmlPdf.FONT_FAMILY, HtmlPdf.esc(school.getPrimaryColor()), pages);
	}

	/** 45.50 → "45.5", 50.00 → "50". */
	private static String num(BigDecimal v) {
		return v == null ? "–" : v.stripTrailingZeros().toPlainString();
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}

}
