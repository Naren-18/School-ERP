package com.schoolerp.exam.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.ClassDto;
import com.schoolerp.academic.dto.ClassSubjectDto;
import com.schoolerp.academic.dto.SubjectDto;
import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.ClassService;
import com.schoolerp.academic.service.SubjectService;
import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.entity.Exam;
import com.schoolerp.exam.entity.ExamSubject;
import com.schoolerp.exam.repository.ExamRepository;
import com.schoolerp.exam.repository.ExamSubjectRepository;
import com.schoolerp.exam.repository.MarkRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Exams of a year, their subject papers per class, and publishing. */
@Service
@RequiredArgsConstructor
public class ExamService {

	private final ExamRepository exams;

	private final ExamSubjectRepository papers;

	private final MarkRepository marks;

	private final GradingService grading;

	private final AcademicYearService yearService;

	private final ClassService classService;

	private final SubjectService subjectService;

	private final AuditService audit;

	@Transactional(readOnly = true)
	public List<ExamDto.Exam> list(Long academicYearId) {
		Long yearId = yearService.resolve(academicYearId).orElse(null);
		if (yearId == null) {
			return List.of();
		}
		return exams.findBySchoolIdAndAcademicYearIdOrderByDisplayOrderAscIdAsc(TenantContext.require(), yearId)
			.stream()
			.map(this::view)
			.toList();
	}

	@Transactional(readOnly = true)
	public ExamDto.Exam get(Long id) {
		return view(require(id));
	}

	@Transactional
	public ExamDto.Exam create(ExamDto.ExamRequest r) {
		AcademicYear year = yearService.require(r.academicYearId());
		grading.require(r.gradingScaleId());
		String name = r.name().trim();
		if (exams.existsBySchoolIdAndAcademicYearIdAndNameIgnoreCase(TenantContext.require(), year.getId(), name)) {
			throw new ConflictException(year.getName() + " already has an exam named " + name);
		}
		Exam exam = new Exam();
		exam.setAcademicYearId(year.getId());
		apply(exam, name, r, year);
		return view(exams.save(exam));
	}

	@Transactional
	public ExamDto.Exam update(Long id, ExamDto.ExamRequest r) {
		Exam exam = require(id);
		if (!exam.getAcademicYearId().equals(r.academicYearId())) {
			throw new BadRequestException("An exam cannot move to another academic year");
		}
		grading.require(r.gradingScaleId());
		String name = r.name().trim();
		if (exams.existsBySchoolIdAndAcademicYearIdAndNameIgnoreCaseAndIdNot(TenantContext.require(),
				exam.getAcademicYearId(), name, id)) {
			throw new ConflictException("This year already has an exam named " + name);
		}
		apply(exam, name, r, yearService.require(exam.getAcademicYearId()));
		return view(exam);
	}

	@Transactional
	public void delete(Long id) {
		Exam exam = require(id);
		List<ExamSubject> list = papers.findBySchoolIdAndExamId(TenantContext.require(), id);
		if (!list.isEmpty() && marks.existsBySchoolIdAndExamSubjectIdIn(TenantContext.require(),
				list.stream().map(ExamSubject::getId).toList())) {
			throw new ConflictException("Marks have been entered for " + exam.getName() + ", so it cannot be deleted");
		}
		papers.deleteAll(list);
		exams.delete(exam);
	}

	/** Published: results visible on report cards, marks locked. */
	@Transactional
	public ExamDto.Exam setPublished(Long id, boolean published) {
		Exam exam = require(id);
		if (exam.isPublished() != published) {
			exam.setPublished(published);
			audit.log(published ? AuditAction.PUBLISH : AuditAction.UPDATE, "exam", exam.getId(),
					Map.of("published", published, "exam", exam.getName()));
		}
		return view(exam);
	}

	// --- Papers ----------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ExamDto.Paper> papers(Long examId, Long classId) {
		Exam exam = require(examId);
		List<ExamSubject> list = classId == null ? papers.findBySchoolIdAndExamId(TenantContext.require(), exam.getId())
				: papers.findBySchoolIdAndExamIdAndClassId(TenantContext.require(), exam.getId(), classService.require(classId).getId());
		return views(list);
	}

	/** Adds or changes the paper for a class + subject. */
	@Transactional
	public ExamDto.Paper savePaper(Long examId, ExamDto.PaperRequest r) {
		Exam exam = requireOpen(examId);
		classService.require(r.classId());
		subjectService.require(r.subjectId());
		if (r.passMarks() != null && r.passMarks().compareTo(r.maxMarks()) > 0) {
			throw new BadRequestException("Pass marks cannot be more than the maximum marks");
		}
		ExamSubject paper = papers.findBySchoolIdAndExamIdAndClassIdAndSubjectId(TenantContext.require(), exam.getId(),
				r.classId(), r.subjectId())
			.orElseGet(() -> {
				ExamSubject p = new ExamSubject();
				p.setExamId(exam.getId());
				p.setClassId(r.classId());
				p.setSubjectId(r.subjectId());
				return p;
			});
		paper.setMaxMarks(r.maxMarks());
		paper.setPassMarks(r.passMarks());
		paper.setExamDate(r.examDate());
		return views(List.of(papers.save(paper))).getFirst();
	}

	@Transactional
	public void deletePaper(Long paperId) {
		ExamSubject paper = requirePaper(paperId);
		requireOpen(paper.getExamId());
		if (marks.existsBySchoolIdAndExamSubjectIdIn(TenantContext.require(), List.of(paperId))) {
			throw new ConflictException("Marks have been entered for this paper");
		}
		papers.delete(paper);
	}

	/** Adds a paper for every subject each class studies this year (Class subjects), skipping existing ones. */
	@Transactional
	public ExamDto.ApplyResult applyToClasses(Long examId, ExamDto.ApplyRequest r) {
		Long schoolId = TenantContext.require();
		Exam exam = requireOpen(examId);
		if (r.passMarks() != null && r.passMarks().compareTo(r.maxMarks()) > 0) {
			throw new BadRequestException("Pass marks cannot be more than the maximum marks");
		}
		List<Long> classIds = r.classIds() == null || r.classIds().isEmpty()
				? classService.list().stream().map(ClassDto.Response::id).toList()
				: r.classIds().stream().map(id -> classService.require(id).getId()).toList();
		int created = 0;
		for (Long classId : classIds) {
			Set<Long> existing = papers.findBySchoolIdAndExamIdAndClassId(schoolId, exam.getId(), classId)
				.stream()
				.map(ExamSubject::getSubjectId)
				.collect(Collectors.toCollection(HashSet::new));
			for (ClassSubjectDto.Response cs : subjectService.classSubjects(exam.getAcademicYearId(), classId)) {
				if (existing.add(cs.subjectId())) {
					ExamSubject p = new ExamSubject();
					p.setExamId(exam.getId());
					p.setClassId(classId);
					p.setSubjectId(cs.subjectId());
					p.setMaxMarks(r.maxMarks());
					p.setPassMarks(r.passMarks());
					papers.save(p);
					created++;
				}
			}
		}
		return new ExamDto.ApplyResult(created);
	}

	public Exam require(Long id) {
		return exams.findByIdAndSchoolId(id, TenantContext.require()).orElseThrow(() -> new NotFoundException("Exam"));
	}

	public ExamSubject requirePaper(Long id) {
		return papers.findByIdAndSchoolId(id, TenantContext.require()).orElseThrow(() -> new NotFoundException("Paper"));
	}

	/** The exam, if it can still be changed (not published). */
	Exam requireOpen(Long id) {
		Exam exam = require(id);
		if (exam.isPublished()) {
			throw new ConflictException(exam.getName() + " is published. Unpublish it to make changes.");
		}
		return exam;
	}

	private static void apply(Exam exam, String name, ExamDto.ExamRequest r, AcademicYear year) {
		if (r.startDate() != null && r.endDate() != null && r.endDate().isBefore(r.startDate())) {
			throw new BadRequestException("The end date is before the start date");
		}
		if ((r.startDate() != null && !year.contains(r.startDate())) || (r.endDate() != null && !year.contains(r.endDate()))) {
			throw new BadRequestException("Exam dates must be within " + year.getName());
		}
		exam.setName(name);
		exam.setGradingScaleId(r.gradingScaleId());
		exam.setStartDate(r.startDate());
		exam.setEndDate(r.endDate());
		exam.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
		exam.setWeightage(r.weightage());
	}

	private ExamDto.Exam view(Exam e) {
		return new ExamDto.Exam(e.getId(), e.getAcademicYearId(), e.getName(), e.getGradingScaleId(),
				grading.require(e.getGradingScaleId()).getName(), e.getStartDate(), e.getEndDate(), e.getDisplayOrder(),
				e.getWeightage(), e.isPublished(), papers.findBySchoolIdAndExamId(TenantContext.require(), e.getId()).size());
	}

	List<ExamDto.Paper> views(List<ExamSubject> list) {
		Map<Long, String> classNames = classService.list().stream()
			.collect(Collectors.toMap(ClassDto.Response::id, ClassDto.Response::name));
		Map<Long, String> subjectNames = subjectService.list().stream()
			.collect(Collectors.toMap(SubjectDto.Response::id, SubjectDto.Response::name));
		return list.stream()
			.map(p -> new ExamDto.Paper(p.getId(), p.getExamId(), p.getClassId(), classNames.get(p.getClassId()),
					p.getSubjectId(), subjectNames.get(p.getSubjectId()), p.getMaxMarks(), p.getPassMarks(), p.getExamDate()))
			.sorted((a, b) -> a.className().equals(b.className()) ? a.subjectName().compareToIgnoreCase(b.subjectName())
					: a.className().compareToIgnoreCase(b.className()))
			.toList();
	}

}
