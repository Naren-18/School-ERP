package com.schoolerp.exam.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.auth.service.CurrentUser;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.entity.Exam;
import com.schoolerp.exam.entity.ExamSubject;
import com.schoolerp.exam.entity.Mark;
import com.schoolerp.exam.repository.ExamSubjectRepository;
import com.schoolerp.exam.repository.MarkRepository;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.service.EnrollmentService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Entering marks for one paper and one section. */
@Service
@RequiredArgsConstructor
public class MarksService {

	private final MarkRepository marks;

	private final ExamSubjectRepository papers;

	private final ExamService examService;

	private final SectionService sectionService;

	private final EnrollmentService enrollments;

	private final ExamAccess access;

	private final Clock clock;

	/** Every paper × section of the exam the user may enter marks for, with how many are entered. */
	@Transactional(readOnly = true)
	public List<ExamDto.EntryOption> entryOptions(Long examId) {
		Long schoolId = TenantContext.require();
		Exam exam = examService.require(examId);
		Set<List<Long>> allowed = access.markPairs();
		Map<Long, List<ExamSubject>> byClass = papers.findBySchoolIdAndExamId(schoolId, exam.getId())
			.stream()
			.collect(Collectors.groupingBy(ExamSubject::getClassId));
		Map<Long, ExamDto.Paper> paperViews = examService
			.views(byClass.values().stream().flatMap(List::stream).toList())
			.stream()
			.collect(Collectors.toMap(ExamDto.Paper::id, Function.identity()));
		if (paperViews.isEmpty()) {
			return List.of();
		}
		// Two grouped counts for the whole exam instead of a class list per paper.
		Map<Long, Integer> students = new HashMap<>();
		marks.activeBySection(schoolId, exam.getAcademicYearId())
			.forEach(r -> students.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue()));
		Map<List<Long>, Integer> entered = new HashMap<>();
		marks.enteredBySection(schoolId, paperViews.keySet())
			.forEach(r -> entered.put(List.of(((Number) r[0]).longValue(), ((Number) r[1]).longValue()),
					((Number) r[2]).intValue()));
		List<ExamDto.EntryOption> options = new ArrayList<>();
		for (SectionDto.Response section : sectionService.list(exam.getAcademicYearId(), null)) {
			for (ExamSubject p : byClass.getOrDefault(section.classId(), List.of())) {
				if (allowed != null && !allowed.contains(List.of(section.id(), p.getSubjectId()))) {
					continue;
				}
				options.add(new ExamDto.EntryOption(p.getId(), section.id(), section.classId(), section.className(),
						section.name(), p.getSubjectId(), paperViews.get(p.getId()).subjectName(), p.getMaxMarks(),
						students.getOrDefault(section.id(), 0), entered.getOrDefault(List.of(p.getId(), section.id()), 0)));
			}
		}
		return options;
	}

	@Transactional(readOnly = true)
	public ExamDto.MarkSheet sheet(Long paperId, Long sectionId) {
		Context c = context(paperId, sectionId);
		Map<Long, Mark> existing = existing(c);
		List<ExamDto.MarkRow> rows = c.roster.stream().map(s -> {
			Mark m = existing.get(s.enrollmentId());
			return new ExamDto.MarkRow(s.enrollmentId(), s.studentId(), s.rollNo(), s.fullName(),
					m == null ? null : m.getMarksObtained(), m != null && m.isAbsent(), m == null ? null : m.getRemarks());
		}).toList();
		return new ExamDto.MarkSheet(c.paper.getId(), c.exam.getName(), c.paperView.subjectName(), c.section.getId(),
				c.paperView.className() + " – " + c.sectionLabel, c.paper.getMaxMarks(), c.paper.getPassMarks(),
				!c.exam.isPublished(), c.exam.isPublished() ? c.exam.getName() + " is published, so marks are locked" : null,
				rows);
	}

	/**
	 * Saves the given rows (others are left as they are). Blank marks and not
	 * absent removes a student's mark. Locked once the exam is published.
	 */
	@Transactional
	public ExamDto.MarkSheet save(Long paperId, Long sectionId, ExamDto.SaveMarks request) {
		Context c = context(paperId, sectionId);
		if (c.exam.isPublished()) {
			throw new ConflictException(c.exam.getName() + " is published. Unpublish it to change marks.");
		}
		Set<Long> rosterIds = c.roster.stream().map(StudentDto.SectionStudent::enrollmentId).collect(Collectors.toSet());
		Map<Long, Mark> existing = existing(c);
		OffsetDateTime now = OffsetDateTime.now(clock);
		Long userId = CurrentUser.get().userId();
		for (ExamDto.MarkEntry e : request.entries()) {
			if (!rosterIds.contains(e.enrollmentId())) {
				throw new BadRequestException("The class list has changed since it was opened. Reload and try again.");
			}
			if (e.absent() && e.marks() != null) {
				throw new BadRequestException("A student marked absent cannot also have marks");
			}
			if (e.marks() != null && e.marks().compareTo(c.paper.getMaxMarks()) > 0) {
				throw new BadRequestException("Marks cannot be more than the maximum of " + c.paper.getMaxMarks().stripTrailingZeros().toPlainString());
			}
			Mark m = existing.get(e.enrollmentId());
			String remarks = e.remarks() == null || e.remarks().isBlank() ? null : e.remarks().trim();
			if (e.marks() == null && !e.absent()) {
				if (m != null) {
					marks.delete(m);
				}
				continue;
			}
			if (m == null) {
				m = new Mark();
				m.setExamSubjectId(c.paper.getId());
				m.setEnrollmentId(e.enrollmentId());
			}
			m.setMarksObtained(e.absent() ? null : e.marks());
			m.setAbsent(e.absent());
			m.setRemarks(remarks);
			m.setEnteredBy(userId);
			m.setUpdatedAt(now);
			marks.save(m);
		}
		marks.flush();
		return sheet(paperId, sectionId);
	}

	private record Context(Exam exam, ExamSubject paper, ExamDto.Paper paperView, Section section, String sectionLabel,
			List<StudentDto.SectionStudent> roster) {
	}

	/** Loads and checks paper + section: same class and year, and the user may enter this subject there. */
	private Context context(Long paperId, Long sectionId) {
		ExamSubject paper = examService.requirePaper(paperId);
		Exam exam = examService.require(paper.getExamId());
		Section section = sectionService.require(sectionId);
		if (!section.getClassId().equals(paper.getClassId()) || !section.getAcademicYearId().equals(exam.getAcademicYearId())) {
			throw new BadRequestException("This section is not in that paper's class and year");
		}
		access.requireMarksEntry(section, paper.getSubjectId());
		return new Context(exam, paper, examService.views(List.of(paper)).getFirst(), section, section.getName(),
				enrollments.sectionStudents(sectionId, false));
	}

	private Map<Long, Mark> existing(Context c) {
		if (c.roster.isEmpty()) {
			return Map.of();
		}
		return marks
			.findBySchoolIdAndExamSubjectIdAndEnrollmentIdIn(TenantContext.require(), c.paper.getId(),
					c.roster.stream().map(StudentDto.SectionStudent::enrollmentId).toList())
			.stream()
			.collect(Collectors.toMap(Mark::getEnrollmentId, Function.identity()));
	}

}
