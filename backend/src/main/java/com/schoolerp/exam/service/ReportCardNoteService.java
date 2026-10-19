package com.schoolerp.exam.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.auth.service.CurrentUser;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.entity.CoScholasticArea;
import com.schoolerp.exam.entity.Exam;
import com.schoolerp.exam.entity.ReportCardNote;
import com.schoolerp.exam.repository.CoScholasticAreaRepository;
import com.schoolerp.exam.repository.ReportCardNoteRepository;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Report card extras: co-scholastic areas (set up once per school) and, per
 * exam, each student's class teacher remark and co-scholastic grades. Entered
 * by the class teacher (or principal/admin); locked once the exam is published.
 */
@Service
@RequiredArgsConstructor
public class ReportCardNoteService {

	private final CoScholasticAreaRepository areas;

	private final ReportCardNoteRepository notes;

	private final ExamService examService;

	private final ResultService results;

	private final SectionService sectionService;

	private final ExamAccess access;

	private final Clock clock;

	@Transactional(readOnly = true)
	public List<ExamDto.Area> areas() {
		return areas.findBySchoolIdOrderByDisplayOrderAscNameAsc(TenantContext.require()).stream().map(ReportCardNoteService::view).toList();
	}

	@Transactional
	public ExamDto.Area createArea(ExamDto.AreaRequest r) {
		if (areas.existsBySchoolIdAndNameIgnoreCase(TenantContext.require(), r.name().trim())) {
			throw new ConflictException("There is already an area called " + r.name().trim());
		}
		CoScholasticArea a = new CoScholasticArea();
		apply(a, r);
		return view(areas.save(a));
	}

	@Transactional
	public ExamDto.Area updateArea(Long id, ExamDto.AreaRequest r) {
		CoScholasticArea a = requireArea(id);
		if (areas.existsBySchoolIdAndNameIgnoreCaseAndIdNot(TenantContext.require(), r.name().trim(), id)) {
			throw new ConflictException("There is already an area called " + r.name().trim());
		}
		apply(a, r);
		return view(a);
	}

	/** Grades already given for the area stay in the notes but are no longer printed. */
	@Transactional
	public void deleteArea(Long id) {
		areas.delete(requireArea(id));
	}

	@Transactional(readOnly = true)
	public ExamDto.NoteSheet sheet(Long examId, Long sectionId) {
		Context c = context(examId, sectionId);
		Map<Long, ReportCardNote> existing = existing(c);
		List<ExamDto.NoteRow> rows = c.roster().stream().map(s -> {
			ReportCardNote n = existing.get(s.enrollmentId());
			return new ExamDto.NoteRow(s.enrollmentId(), s.studentId(), s.rollNo(), s.fullName(),
					n == null ? null : n.getTeacherRemark(), n == null ? Map.of() : grades(n));
		}).toList();
		return new ExamDto.NoteSheet(c.exam().getId(), c.exam().getName(), sectionId, results.label(c.section()).text(), areas(),
				!c.exam().isPublished(), c.exam().isPublished() ? c.exam().getName() + " is published, so remarks are locked" : null,
				rows);
	}

	/** Saves the given rows; others are untouched. Empty remark and no grades removes the row. */
	@Transactional
	public ExamDto.NoteSheet save(Long examId, Long sectionId, ExamDto.SaveNotes request) {
		Context c = context(examId, sectionId);
		if (c.exam().isPublished()) {
			throw new ConflictException(c.exam().getName() + " is published. Unpublish it to change remarks.");
		}
		Set<Long> rosterIds = c.roster().stream().map(StudentDto.SectionStudent::enrollmentId).collect(Collectors.toSet());
		Set<Long> areaIds = areas.findBySchoolIdOrderByDisplayOrderAscNameAsc(TenantContext.require())
			.stream()
			.map(CoScholasticArea::getId)
			.collect(Collectors.toSet());
		Map<Long, ReportCardNote> existing = existing(c);
		OffsetDateTime now = OffsetDateTime.now(clock);
		for (ExamDto.NoteEntry e : request.entries()) {
			if (!rosterIds.contains(e.enrollmentId())) {
				throw new BadRequestException("The class list has changed since it was opened. Reload and try again.");
			}
			Map<String, String> grades = new LinkedHashMap<>();
			if (e.grades() != null) {
				e.grades().forEach((areaId, grade) -> {
					if (!areaIds.contains(areaId)) {
						throw new BadRequestException("Unknown co-scholastic area");
					}
					if (grade != null && !grade.isBlank()) {
						grades.put(areaId.toString(), grade.trim().toUpperCase(Locale.ROOT));
					}
				});
			}
			String remark = e.remark() == null || e.remark().isBlank() ? null : e.remark().trim();
			ReportCardNote n = existing.get(e.enrollmentId());
			if (remark == null && grades.isEmpty()) {
				if (n != null) {
					notes.delete(n);
				}
				continue;
			}
			if (n == null) {
				n = new ReportCardNote();
				n.setExamId(c.exam().getId());
				n.setEnrollmentId(e.enrollmentId());
			}
			n.setTeacherRemark(remark);
			n.setCoScholastic(grades);
			n.setUpdatedBy(CurrentUser.get().userId());
			n.setUpdatedAt(now);
			notes.save(n);
		}
		notes.flush();
		return sheet(examId, sectionId);
	}

	/** For report cards: enrollment id → note, for one exam. */
	Map<Long, ReportCardNote> notesFor(Long examId, List<Long> enrollmentIds) {
		if (enrollmentIds.isEmpty()) {
			return Map.of();
		}
		return notes.findBySchoolIdAndExamIdAndEnrollmentIdIn(TenantContext.require(), examId, enrollmentIds)
			.stream()
			.collect(Collectors.toMap(ReportCardNote::getEnrollmentId, Function.identity()));
	}

	static Map<Long, String> grades(ReportCardNote n) {
		Map<Long, String> out = new HashMap<>();
		if (n.getCoScholastic() != null) {
			n.getCoScholastic().forEach((k, v) -> out.put(Long.valueOf(k), v));
		}
		return out;
	}

	private record Context(Exam exam, Section section, List<StudentDto.SectionStudent> roster) {
	}

	private Context context(Long examId, Long sectionId) {
		Exam exam = examService.require(examId);
		Section section = sectionService.require(sectionId);
		if (!section.getAcademicYearId().equals(exam.getAcademicYearId())) {
			throw new BadRequestException("This section is not in the exam's academic year");
		}
		access.requireNotesEntry(section);
		return new Context(exam, section, results.roster(sectionId));
	}

	private Map<Long, ReportCardNote> existing(Context c) {
		return notesFor(c.exam().getId(), c.roster().stream().map(StudentDto.SectionStudent::enrollmentId).toList());
	}

	private CoScholasticArea requireArea(Long id) {
		return areas.findByIdAndSchoolId(id, TenantContext.require()).orElseThrow(() -> new NotFoundException("Area"));
	}

	private static void apply(CoScholasticArea a, ExamDto.AreaRequest r) {
		a.setName(r.name().trim());
		a.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
	}

	private static ExamDto.Area view(CoScholasticArea a) {
		return new ExamDto.Area(a.getId(), a.getName(), a.getDisplayOrder());
	}

}
