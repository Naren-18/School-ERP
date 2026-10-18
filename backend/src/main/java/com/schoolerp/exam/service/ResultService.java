package com.schoolerp.exam.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.academic.service.SubjectService;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.entity.Exam;
import com.schoolerp.exam.entity.Mark;
import com.schoolerp.exam.repository.ExamRepository;
import com.schoolerp.exam.repository.ExamSubjectRepository;
import com.schoolerp.exam.repository.MarkRepository;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Enrollment;
import com.schoolerp.student.entity.Student;
import com.schoolerp.student.service.EnrollmentService;
import com.schoolerp.student.service.StudentService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Turns marks into results: per section for an exam, and per student for a year. */
@Service
@RequiredArgsConstructor
public class ResultService {

	private final ExamRepository exams;

	private final ExamSubjectRepository papers;

	private final MarkRepository marks;

	private final ExamService examService;

	private final GradingService grading;

	private final SectionService sectionService;

	private final EnrollmentService enrollments;

	private final StudentService studentService;

	private final ExamAccess access;

	private final AcademicYearService yearService;

	private final SubjectService subjectService;

	/** Students × subjects for one exam in one section, with totals, grades and ranks. */
	@Transactional(readOnly = true)
	public ExamDto.ResultSheet sectionSheet(Long examId, Long sectionId) {
		Exam exam = examService.require(examId);
		Section section = sectionService.require(sectionId);
		if (!section.getAcademicYearId().equals(exam.getAcademicYearId())) {
			throw new BadRequestException("This section is not in the exam's academic year");
		}
		access.requireResultsView(section);
		List<ExamDto.Paper> paperViews = inClassOrder(exam.getAcademicYearId(), section.getClassId(), examService
			.views(papers.findBySchoolIdAndExamIdAndClassId(TenantContext.require(), exam.getId(), section.getClassId())));
		List<ResultCalculator.Paper> calcPapers = calcPapers(paperViews);
		List<StudentDto.SectionStudent> roster = roster(sectionId);
		Map<Long, Map<Long, ResultCalculator.Entry>> entries = entries(
				paperViews.stream().map(ExamDto.Paper::id).toList(),
				roster.stream().map(StudentDto.SectionStudent::enrollmentId).toList());
		List<ResultCalculator.Band> bands = grading.calculatorBands(exam.getGradingScaleId());

		Map<Long, ResultCalculator.ExamResult> results = new LinkedHashMap<>();
		for (StudentDto.SectionStudent s : roster) {
			results.put(s.enrollmentId(),
					ResultCalculator.exam(calcPapers, entries.getOrDefault(s.enrollmentId(), Map.of()), bands));
		}
		Map<Long, Integer> ranks = ResultCalculator.rank(new ArrayList<>(results.keySet()),
				id -> results.get(id).complete() ? results.get(id).percent() : null);
		SectionLabel label = label(section);
		return new ExamDto.ResultSheet(exam.getId(), exam.getName(), exam.isPublished(), sectionId, label.text(),
				paperViews.stream().map(p -> new ExamDto.SubjectHeader(p.subjectId(), p.subjectName(), p.maxMarks())).toList(),
				roster.stream()
					.map(s -> new ExamDto.ResultRow(s.enrollmentId(), s.studentId(), s.rollNo(), s.fullName(),
							results.get(s.enrollmentId()), ranks.get(s.enrollmentId())))
					.toList());
	}

	/** Sections of the year (current by default) whose results the user may see. */
	@Transactional(readOnly = true)
	public List<SectionDto.Response> sections(Long academicYearId) {
		Long yearId = academicYearId != null ? academicYearId : yearService.resolve(null).orElse(null);
		if (yearId == null) {
			return List.of();
		}
		Set<Long> allowed = access.resultSectionIds();
		return sectionService.list(yearId, null).stream().filter(s -> allowed == null || allowed.contains(s.id())).toList();
	}

	/** A student's published exams in a year and the weighted final result. */
	@Transactional(readOnly = true)
	public ExamDto.StudentResults studentResults(Long studentId, Long academicYearId) {
		Student student = studentService.requireVisible(studentId);
		List<StudentDto.EnrollmentView> history = enrollments.history(studentId);
		// Default: this year's class; otherwise the most recent one.
		Long yearId = academicYearId != null ? academicYearId : yearService.resolve(null).orElse(null);
		StudentDto.EnrollmentView enrollment = history.stream()
			.filter(e -> e.academicYearId().equals(yearId))
			.findFirst()
			.orElse(academicYearId == null && !history.isEmpty() ? history.getFirst() : null);
		if (enrollment == null) {
			return new ExamDto.StudentResults(student.getId(), student.getFullName(), null, List.of(),
					ResultCalculator.finalResult(List.of(), List.of()), Map.of());
		}
		YearResults year = yearResults(enrollment.academicYearId(), enrollment.classId(), List.of(enrollment.id()), true)
			.get(enrollment.id());
		return new ExamDto.StudentResults(student.getId(), student.getFullName(), enrollment.academicYearName(),
				year.exams(), year.finalResult(), year.subjectNames());
	}

	record YearResults(List<ExamDto.ExamSummary> exams, ResultCalculator.FinalResult finalResult,
			Map<Long, String> subjectNames) {
	}

	/**
	 * Every exam of the year (published only, when asked) for the given
	 * enrollments of one class, and the weighted final. The final grade uses the
	 * scale of the last weighted exam.
	 */
	Map<Long, YearResults> yearResults(Long yearId, Long classId, List<Long> enrollmentIds, boolean publishedOnly) {
		Long schoolId = TenantContext.require();
		List<Exam> yearExams = exams.findBySchoolIdAndAcademicYearIdOrderByDisplayOrderAscIdAsc(schoolId, yearId)
			.stream()
			.filter(e -> !publishedOnly || e.isPublished())
			.toList();
		Map<Long, List<ExamDto.Paper>> papersByExam = inClassOrder(yearId, classId, examService
			.views(yearExams.isEmpty() ? List.of()
					: papers.findBySchoolIdAndExamIdInAndClassId(schoolId, yearExams.stream().map(Exam::getId).toList(), classId)))
			.stream()
			.collect(Collectors.groupingBy(ExamDto.Paper::examId, LinkedHashMap::new, Collectors.toList()));
		Map<Long, Map<Long, ResultCalculator.Entry>> entries = entries(
				papersByExam.values().stream().flatMap(List::stream).map(ExamDto.Paper::id).toList(), enrollmentIds);
		Map<Long, List<ResultCalculator.Band>> bandsByScale = new HashMap<>();
		yearExams.forEach(e -> bandsByScale.computeIfAbsent(e.getGradingScaleId(), grading::calculatorBands));
		List<ResultCalculator.Band> finalBands = yearExams.stream()
			.filter(e -> e.getWeightage() != null)
			.max(Comparator.comparingInt(Exam::getDisplayOrder).thenComparing(Exam::getId))
			.map(e -> bandsByScale.get(e.getGradingScaleId()))
			.orElse(List.of());
		Map<Long, String> subjectNames = new LinkedHashMap<>();
		papersByExam.values().stream().flatMap(List::stream).forEach(p -> subjectNames.putIfAbsent(p.subjectId(), p.subjectName()));

		Map<Long, YearResults> out = new LinkedHashMap<>();
		for (Long enrollmentId : enrollmentIds) {
			List<ExamDto.ExamSummary> summaries = new ArrayList<>();
			List<ResultCalculator.WeightedExam> weighted = new ArrayList<>();
			for (Exam e : yearExams) {
				ResultCalculator.ExamResult r = ResultCalculator.exam(calcPapers(papersByExam.getOrDefault(e.getId(), List.of())),
						entries.getOrDefault(enrollmentId, Map.of()), bandsByScale.get(e.getGradingScaleId()));
				summaries.add(new ExamDto.ExamSummary(e.getId(), e.getName(), e.isPublished(), e.getWeightage(), r));
				weighted.add(new ResultCalculator.WeightedExam(e.getWeightage(), r));
			}
			out.put(enrollmentId, new YearResults(summaries, ResultCalculator.finalResult(weighted, finalBands), subjectNames));
		}
		return out;
	}

	/** Students of the section for results: everyone placed there this year except those who left. */
	List<StudentDto.SectionStudent> roster(Long sectionId) {
		return enrollments.sectionStudents(sectionId, true)
			.stream()
			.filter(s -> s.status() != Enrollment.Status.LEFT)
			.toList();
	}

	record SectionLabel(String text, String className, String sectionName) {
	}

	SectionLabel label(Section section) {
		var s = sectionService.describe(List.of(section.getId())).get(section.getId());
		return new SectionLabel(s.className() + " – " + s.name(), s.className(), s.name());
	}

	/** Papers in the order set under Setup → Class subjects (the report card order); unmapped ones last. */
	private List<ExamDto.Paper> inClassOrder(Long yearId, Long classId, List<ExamDto.Paper> list) {
		Map<Long, Integer> order = new HashMap<>();
		subjectService.classSubjects(yearId, classId).forEach(cs -> order.put(cs.subjectId(), cs.displayOrder()));
		return list.stream()
			.sorted(Comparator.comparing((ExamDto.Paper p) -> order.getOrDefault(p.subjectId(), Integer.MAX_VALUE))
				.thenComparing(ExamDto.Paper::subjectName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static List<ResultCalculator.Paper> calcPapers(List<ExamDto.Paper> list) {
		return list.stream()
			.map(p -> new ResultCalculator.Paper(p.id(), p.subjectId(), p.subjectName(), p.maxMarks(), p.passMarks()))
			.toList();
	}

	/** enrollmentId → (paperId → entry) */
	private Map<Long, Map<Long, ResultCalculator.Entry>> entries(List<Long> paperIds, List<Long> enrollmentIds) {
		Map<Long, Map<Long, ResultCalculator.Entry>> out = new HashMap<>();
		if (paperIds.isEmpty() || enrollmentIds.isEmpty()) {
			return out;
		}
		for (Mark m : marks.findBySchoolIdAndExamSubjectIdInAndEnrollmentIdIn(TenantContext.require(), paperIds, enrollmentIds)) {
			out.computeIfAbsent(m.getEnrollmentId(), k -> new HashMap<>())
				.put(m.getExamSubjectId(), new ResultCalculator.Entry(m.getMarksObtained(), m.isAbsent()));
		}
		return out;
	}

}
