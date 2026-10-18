package com.schoolerp.academic.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.entity.SchoolClass;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.repository.SchoolClassRepository;
import com.schoolerp.academic.repository.SectionRepository;
import com.schoolerp.academic.repository.TeacherAssignmentRepository;
import com.schoolerp.auth.service.StaffService;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SectionService {

	private final SectionRepository sections;

	private final SchoolClassRepository classes;

	private final TeacherAssignmentRepository assignments;

	private final AcademicYearService yearService;

	private final ClassService classService;

	private final StaffService staff;

	/** Other modules' objections to deleting a section (e.g. students enrolled). */
	private final List<SectionUsageCheck> usageChecks;

	/** Sections of a year (the current year if none is given), ordered by class then name. */
	@Transactional(readOnly = true)
	public List<SectionDto.Response> list(Long academicYearId, Long classId) {
		Long yearId = yearService.resolve(academicYearId).orElse(null);
		if (yearId == null) {
			return List.of();
		}
		List<Section> found = sections.findBySchoolIdAndAcademicYearId(TenantContext.require(), yearId)
			.stream()
			.filter(s -> classId == null || classId.equals(s.getClassId()))
			.toList();
		return toResponses(found);
	}

	@Transactional(readOnly = true)
	public SectionDto.Response get(Long id) {
		return toResponses(List.of(require(id))).getFirst();
	}

	@Transactional
	public SectionDto.Response create(SectionDto.Request request) {
		yearService.require(request.academicYearId());
		classService.require(request.classId());
		String name = request.name().trim().toUpperCase();
		if (sections.existsBySchoolIdAndAcademicYearIdAndClassIdAndNameIgnoreCase(TenantContext.require(),
				request.academicYearId(), request.classId(), name)) {
			throw new ConflictException("Section " + name + " already exists for this class and year");
		}
		Section section = new Section();
		section.setAcademicYearId(request.academicYearId());
		section.setClassId(request.classId());
		apply(section, name, request);
		return get(sections.save(section).getId());
	}

	@Transactional
	public SectionDto.Response update(Long id, SectionDto.Request request) {
		Section section = require(id);
		if (!section.getAcademicYearId().equals(request.academicYearId())
				|| !section.getClassId().equals(request.classId())) {
			throw new ConflictException("A section cannot be moved to another year or class");
		}
		String name = request.name().trim().toUpperCase();
		if (sections.existsBySchoolIdAndAcademicYearIdAndClassIdAndNameIgnoreCaseAndIdNot(TenantContext.require(),
				section.getAcademicYearId(), section.getClassId(), name, id)) {
			throw new ConflictException("Section " + name + " already exists for this class and year");
		}
		apply(section, name, request);
		return get(id);
	}

	@Transactional
	public void delete(Long id) {
		Section section = require(id);
		SectionDto.Response described = toResponses(List.of(section)).getFirst();
		String label = described.className() + " – " + described.name();
		for (SectionUsageCheck check : usageChecks) {
			check.blockingReason(section, label).ifPresent(reason -> {
				throw new ConflictException(reason);
			});
		}
		if (assignments.existsBySchoolIdAndSectionId(TenantContext.require(), id)) {
			throw new ConflictException("This section has teacher assignments. Remove them first.");
		}
		sections.delete(section);
		sections.flush();
	}

	/** Section details (class name, year, teacher) by id, for other modules' responses. */
	@Transactional(readOnly = true)
	public Map<Long, SectionDto.Response> describe(Collection<Long> sectionIds) {
		if (sectionIds.isEmpty()) {
			return new HashMap<>();
		}
		return toResponses(sections.findBySchoolIdAndIdIn(TenantContext.require(), sectionIds)).stream()
			.collect(Collectors.toMap(SectionDto.Response::id, Function.identity()));
	}

	/** Finds a section by class and section name in a year (CSV import). */
	@Transactional(readOnly = true)
	public Optional<Section> findByNames(Long yearId, Long classId, String sectionName) {
		return sections.findBySchoolIdAndAcademicYearIdAndClassIdAndNameIgnoreCase(TenantContext.require(), yearId,
				classId, sectionName.trim());
	}

	public Section require(Long id) {
		return sections.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Section"));
	}

	private void apply(Section section, String name, SectionDto.Request request) {
		if (request.classTeacherId() != null && !Objects.equals(request.classTeacherId(), section.getClassTeacherId())) {
			staff.requireActiveTeacher(request.classTeacherId());
		}
		section.setName(name);
		section.setClassTeacherId(request.classTeacherId());
		section.setCapacity(request.capacity());
	}

	List<SectionDto.Response> toResponses(List<Section> list) {
		if (list.isEmpty()) {
			return List.of();
		}
		Long schoolId = TenantContext.require();
		Map<Long, SchoolClass> classById = classes
			.findBySchoolIdAndIdIn(schoolId, list.stream().map(Section::getClassId).collect(Collectors.toSet()))
			.stream()
			.collect(Collectors.toMap(SchoolClass::getId, Function.identity()));
		Map<Long, String> teachers = staff.fullNames(list.stream()
			.map(Section::getClassTeacherId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet()));
		return list.stream().map(s -> {
			SchoolClass c = classById.get(s.getClassId());
			return new SectionDto.Response(s.getId(), s.getAcademicYearId(), s.getClassId(), c.getName(),
					c.getDisplayOrder(), s.getName(), s.getClassTeacherId(), teachers.get(s.getClassTeacherId()),
					s.getCapacity());
		})
			.sorted(Comparator.comparingInt(SectionDto.Response::classOrder)
				.thenComparing(SectionDto.Response::className)
				.thenComparing(SectionDto.Response::name))
			.toList();
	}

}
