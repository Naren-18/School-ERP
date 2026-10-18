package com.schoolerp.academic.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.dto.TeacherAssignmentDto;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.entity.Subject;
import com.schoolerp.academic.entity.TeacherAssignment;
import com.schoolerp.academic.repository.ClassSubjectRepository;
import com.schoolerp.academic.repository.SectionRepository;
import com.schoolerp.academic.repository.SubjectRepository;
import com.schoolerp.academic.repository.TeacherAssignmentRepository;
import com.schoolerp.auth.service.StaffService;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeacherAssignmentService {

	private final TeacherAssignmentRepository assignments;

	private final SectionRepository sections;

	private final SubjectRepository subjects;

	private final ClassSubjectRepository classSubjects;

	private final SectionService sectionService;

	private final SubjectService subjectService;

	private final AcademicYearService yearService;

	private final StaffService staff;

	/** Assignments for one section, or for every section of a year (current year by default). */
	@Transactional(readOnly = true)
	public List<TeacherAssignmentDto.Response> list(Long academicYearId, Long sectionId, Long teacherId) {
		Long schoolId = TenantContext.require();
		List<Section> scope;
		if (sectionId != null) {
			scope = List.of(sectionService.require(sectionId));
		}
		else {
			Long yearId = yearService.resolve(academicYearId).orElse(null);
			scope = yearId == null ? List.of() : sections.findBySchoolIdAndAcademicYearId(schoolId, yearId);
		}
		if (scope.isEmpty()) {
			return List.of();
		}
		List<TeacherAssignment> found = assignments
			.findBySchoolIdAndSectionIdIn(schoolId, scope.stream().map(Section::getId).toList())
			.stream()
			.filter(a -> teacherId == null || teacherId.equals(a.getTeacherId()))
			.toList();
		return toResponses(found, scope);
	}

	@Transactional
	public TeacherAssignmentDto.Response assign(TeacherAssignmentDto.Request request) {
		Long schoolId = TenantContext.require();
		Section section = sectionService.require(request.sectionId());
		subjectService.require(request.subjectId());
		if (!classSubjects.existsBySchoolIdAndAcademicYearIdAndClassIdAndSubjectId(schoolId,
				section.getAcademicYearId(), section.getClassId(), request.subjectId())) {
			throw new BadRequestException("This subject is not taught in this class. Add it to the class first.");
		}
		staff.requireActiveTeacher(request.teacherId());

		TeacherAssignment assignment = assignments
			.findBySchoolIdAndSectionIdAndSubjectId(schoolId, section.getId(), request.subjectId())
			.orElseGet(() -> {
				TeacherAssignment a = new TeacherAssignment();
				a.setSectionId(section.getId());
				a.setSubjectId(request.subjectId());
				return a;
			});
		assignment.setTeacherId(request.teacherId());
		return toResponses(List.of(assignments.save(assignment)), List.of(section)).getFirst();
	}

	@Transactional
	public void delete(Long id) {
		TeacherAssignment assignment = assignments.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Teacher assignment"));
		assignments.delete(assignment);
	}

	private List<TeacherAssignmentDto.Response> toResponses(List<TeacherAssignment> list, List<Section> scope) {
		if (list.isEmpty()) {
			return List.of();
		}
		Long schoolId = TenantContext.require();
		Map<Long, SectionDto.Response> sectionById = sectionService.toResponses(scope)
			.stream()
			.collect(Collectors.toMap(SectionDto.Response::id, Function.identity()));
		Map<Long, Subject> subjectById = subjects
			.findBySchoolIdAndIdIn(schoolId, list.stream().map(TeacherAssignment::getSubjectId).collect(Collectors.toSet()))
			.stream()
			.collect(Collectors.toMap(Subject::getId, Function.identity()));
		Map<Long, String> teachers = staff
			.fullNames(list.stream().map(TeacherAssignment::getTeacherId).collect(Collectors.toSet()));
		return list.stream().map(a -> {
			SectionDto.Response s = sectionById.get(a.getSectionId());
			Subject subject = subjectById.get(a.getSubjectId());
			return new TeacherAssignmentDto.Response(a.getId(), s.id(), s.name(), s.classId(), s.className(),
					subject.getId(), subject.getName(), a.getTeacherId(), teachers.get(a.getTeacherId()));
		})
			.sorted(Comparator.comparing((TeacherAssignmentDto.Response r) -> sectionById.get(r.sectionId()).classOrder())
				.thenComparing(TeacherAssignmentDto.Response::sectionName)
				.thenComparing(TeacherAssignmentDto.Response::subjectName))
			.toList();
	}

}
