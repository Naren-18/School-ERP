package com.schoolerp.academic.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.ClassSubjectDto;
import com.schoolerp.academic.dto.SubjectDto;
import com.schoolerp.academic.entity.ClassSubject;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.entity.Subject;
import com.schoolerp.academic.repository.ClassSubjectRepository;
import com.schoolerp.academic.repository.SectionRepository;
import com.schoolerp.academic.repository.SubjectRepository;
import com.schoolerp.academic.repository.TeacherAssignmentRepository;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Subjects, and which subjects each class studies in a year. */
@Service
@RequiredArgsConstructor
public class SubjectService {

	private final SubjectRepository subjects;

	private final ClassSubjectRepository classSubjects;

	private final SectionRepository sections;

	private final TeacherAssignmentRepository assignments;

	private final AcademicYearService yearService;

	private final ClassService classService;

	@Transactional(readOnly = true)
	public List<SubjectDto.Response> list() {
		return subjects.findBySchoolIdOrderByNameAsc(TenantContext.require())
			.stream()
			.map(SubjectDto.Response::of)
			.toList();
	}

	@Transactional
	public SubjectDto.Response create(SubjectDto.Request request) {
		String name = request.name().trim();
		if (subjects.existsBySchoolIdAndNameIgnoreCase(TenantContext.require(), name)) {
			throw new ConflictException("A subject named " + name + " already exists");
		}
		Subject subject = new Subject();
		apply(subject, name, request);
		return SubjectDto.Response.of(subjects.save(subject));
	}

	@Transactional
	public SubjectDto.Response update(Long id, SubjectDto.Request request) {
		Subject subject = require(id);
		String name = request.name().trim();
		if (subjects.existsBySchoolIdAndNameIgnoreCaseAndIdNot(TenantContext.require(), name, id)) {
			throw new ConflictException("A subject named " + name + " already exists");
		}
		apply(subject, name, request);
		return SubjectDto.Response.of(subject);
	}

	@Transactional
	public void delete(Long id) {
		Subject subject = require(id);
		if (classSubjects.existsBySchoolIdAndSubjectId(TenantContext.require(), id)) {
			throw new ConflictException("This subject is assigned to classes. Remove it from them first.");
		}
		subjects.delete(subject);
		subjects.flush();
	}

	@Transactional(readOnly = true)
	public List<ClassSubjectDto.Response> classSubjects(Long academicYearId, Long classId) {
		yearService.require(academicYearId);
		classService.require(classId);
		return toResponses(classSubjects.findBySchoolIdAndAcademicYearIdAndClassIdOrderByDisplayOrderAsc(
				TenantContext.require(), academicYearId, classId));
	}

	/**
	 * Replaces the class's subject list for the year. Teacher assignments for
	 * subjects that are removed are removed too.
	 */
	@Transactional
	public List<ClassSubjectDto.Response> setClassSubjects(ClassSubjectDto.Request request) {
		Long schoolId = TenantContext.require();
		yearService.require(request.academicYearId());
		classService.require(request.classId());
		List<Long> wanted = request.subjectIds().stream().distinct().toList();
		if (subjects.findBySchoolIdAndIdIn(schoolId, wanted).size() != wanted.size()) {
			throw new BadRequestException("One or more subjects were not found");
		}

		List<ClassSubject> existing = classSubjects.findBySchoolIdAndAcademicYearIdAndClassIdOrderByDisplayOrderAsc(
				schoolId, request.academicYearId(), request.classId());
		Map<Long, ClassSubject> bySubject = existing.stream()
			.collect(Collectors.toMap(ClassSubject::getSubjectId, Function.identity()));

		Set<Long> removed = new HashSet<>(bySubject.keySet());
		removed.removeAll(wanted);
		if (!removed.isEmpty()) {
			List<Long> sectionIds = sections.findBySchoolIdAndAcademicYearId(schoolId, request.academicYearId())
				.stream()
				.filter(s -> s.getClassId().equals(request.classId()))
				.map(Section::getId)
				.toList();
			if (!sectionIds.isEmpty()) {
				assignments.deleteAll(
						assignments.findBySchoolIdAndSectionIdInAndSubjectIdIn(schoolId, sectionIds, removed));
			}
			classSubjects.deleteAll(existing.stream().filter(cs -> removed.contains(cs.getSubjectId())).toList());
		}

		List<ClassSubject> result = new ArrayList<>();
		for (int i = 0; i < wanted.size(); i++) {
			ClassSubject cs = bySubject.get(wanted.get(i));
			if (cs == null) {
				cs = new ClassSubject();
				cs.setAcademicYearId(request.academicYearId());
				cs.setClassId(request.classId());
				cs.setSubjectId(wanted.get(i));
			}
			cs.setDisplayOrder(i + 1);
			result.add(classSubjects.save(cs));
		}
		return toResponses(result);
	}

	public Subject require(Long id) {
		return subjects.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Subject"));
	}

	private void apply(Subject subject, String name, SubjectDto.Request request) {
		subject.setName(name);
		subject.setCode(request.code() == null || request.code().isBlank() ? null : request.code().trim().toUpperCase());
		subject.setType(request.type());
	}

	private List<ClassSubjectDto.Response> toResponses(List<ClassSubject> list) {
		if (list.isEmpty()) {
			return List.of();
		}
		Map<Long, Subject> byId = subjects
			.findBySchoolIdAndIdIn(TenantContext.require(),
					list.stream().map(ClassSubject::getSubjectId).collect(Collectors.toSet()))
			.stream()
			.collect(Collectors.toMap(Subject::getId, Function.identity()));
		return list.stream().map(cs -> {
			Subject s = byId.get(cs.getSubjectId());
			return new ClassSubjectDto.Response(cs.getId(), s.getId(), s.getName(), s.getCode(), s.getType(),
					cs.getDisplayOrder());
		}).toList();
	}

}
