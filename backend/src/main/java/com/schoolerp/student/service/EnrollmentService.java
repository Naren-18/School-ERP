package com.schoolerp.student.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.AcademicYearDto;
import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Enrollment;
import com.schoolerp.student.entity.Student;
import com.schoolerp.student.repository.EnrollmentRepository;
import com.schoolerp.student.repository.StudentRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Placing students in sections: admission, section change, promotion, roll numbers. */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

	private final EnrollmentRepository enrollments;

	private final StudentRepository students;

	private final SectionService sectionService;

	private final AcademicYearService yearService;

	private final StudentAccess access;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	/** Places a student in a section for that section's year. Roll number defaults to the next free one. */
	@Transactional
	public Enrollment enroll(Student student, StudentDto.Placement placement, LocalDate defaultJoinedOn) {
		Long schoolId = TenantContext.require();
		Section section = sectionService.require(placement.sectionId());
		if (enrollments.findBySchoolIdAndStudentIdAndAcademicYearId(schoolId, student.getId(),
				section.getAcademicYearId()).isPresent()) {
			throw new ConflictException(student.getFullName() + " is already enrolled in this academic year");
		}
		Enrollment e = new Enrollment();
		e.setStudentId(student.getId());
		e.setAcademicYearId(section.getAcademicYearId());
		e.setSectionId(section.getId());
		e.setRollNo(resolveRollNo(section.getId(), placement.rollNo(), null));
		e.setJoinedOn(placement.joinedOn() != null ? placement.joinedOn() : defaultJoinedOn);
		enrollments.save(e);
		events.publishEvent(new StudentEnrolledEvent(schoolId, e.getId(), e.getSectionId(), e.getAcademicYearId()));
		return e;
	}

	/** Enrols an existing active student (e.g. detained, or not yet placed this year). */
	@Transactional
	public StudentDto.EnrollmentView enrollExisting(Long studentId, StudentDto.Placement placement) {
		Student student = students.findByIdAndSchoolId(studentId, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Student"));
		if (student.getStatus() != Student.Status.ACTIVE) {
			throw new ConflictException("Only active students can be enrolled");
		}
		Enrollment e = enroll(student, placement, LocalDate.now(clock));
		audit.log(AuditAction.CREATE, "enrollment", e.getId(), Map.of("studentId", studentId, "sectionId", e.getSectionId()));
		return views(List.of(e)).getFirst();
	}

	@Transactional
	public StudentDto.EnrollmentView changeSection(Long enrollmentId, StudentDto.SectionChangeRequest request) {
		Enrollment e = require(enrollmentId);
		if (e.getStatus() != Enrollment.Status.ACTIVE) {
			throw new ConflictException("Only an active enrollment can change section");
		}
		Section target = sectionService.require(request.sectionId());
		if (!target.getAcademicYearId().equals(e.getAcademicYearId())) {
			throw new BadRequestException("The new section must be in the same academic year");
		}
		if (target.getId().equals(e.getSectionId())) {
			throw new BadRequestException("The student is already in this section");
		}
		// Resolve the roll number before moving: the max-roll query auto-flushes, and
		// must not count this student as already being in the target section.
		Integer rollNo = resolveRollNo(target.getId(), request.rollNo(), e.getId());
		Long from = e.getSectionId();
		e.setSectionId(target.getId());
		e.setRollNo(rollNo);
		audit.log(AuditAction.UPDATE, "enrollment", e.getId(),
				Map.of("studentId", e.getStudentId(), "fromSectionId", from, "toSectionId", target.getId()));
		return views(List.of(e)).getFirst();
	}

	/** Students currently in a section (or, with {@code includeInactive}, everyone who was). */
	@Transactional(readOnly = true)
	public List<StudentDto.SectionStudent> sectionStudents(Long sectionId, boolean includeInactive) {
		Long schoolId = TenantContext.require();
		Section section = sectionService.require(sectionId);
		Set<Long> allowed = access.restrictedSectionIds();
		if (allowed != null && !allowed.contains(section.getId())) {
			throw new NotFoundException("Section");
		}
		List<Enrollment> list = includeInactive
				? enrollments.findBySchoolIdAndSectionIdAndStatusIn(schoolId, sectionId, Set.of(Enrollment.Status.values()))
				: enrollments.findBySchoolIdAndSectionIdAndStatus(schoolId, sectionId, Enrollment.Status.ACTIVE);
		Map<Long, Student> byId = studentsById(list);
		return list.stream().map(e -> {
			Student s = byId.get(e.getStudentId());
			return new StudentDto.SectionStudent(e.getId(), s.getId(), s.getAdmissionNo(), s.getFullName(),
					s.getGender(), e.getRollNo(), e.getStatus(), s.getPhotoKey() != null);
		})
			.sorted(Comparator.comparing(StudentDto.SectionStudent::rollNo, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(StudentDto.SectionStudent::fullName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	/**
	 * Who was in the section at some point in [from, to], by roll number. For
	 * attendance; callers apply their own access rules.
	 */
	@Transactional(readOnly = true)
	public List<StudentDto.SectionStudent> rosterDuring(Long sectionId, LocalDate from, LocalDate to) {
		List<Enrollment> list = enrollments.findInSectionDuring(TenantContext.require(), sectionId, from, to);
		Map<Long, Student> byId = studentsById(list);
		return list.stream().map(e -> {
			Student s = byId.get(e.getStudentId());
			return new StudentDto.SectionStudent(e.getId(), s.getId(), s.getAdmissionNo(), s.getFullName(),
					s.getGender(), e.getRollNo(), e.getStatus(), s.getPhotoKey() != null);
		})
			.sorted(Comparator.comparing(StudentDto.SectionStudent::rollNo, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(StudentDto.SectionStudent::fullName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	/** A student's enrollments that overlap [from, to]. */
	@Transactional(readOnly = true)
	public List<Enrollment> studentEnrollmentsDuring(Long studentId, LocalDate from, LocalDate to) {
		return enrollments.findForStudentDuring(TenantContext.require(), studentId, from, to);
	}

	/** Re-assigns roll numbers 1..n in alphabetical order of name. */
	@Transactional
	public List<StudentDto.SectionStudent> renumber(Long sectionId) {
		Long schoolId = TenantContext.require();
		sectionService.require(sectionId);
		List<Enrollment> list = enrollments.findBySchoolIdAndSectionIdAndStatus(schoolId, sectionId,
				Enrollment.Status.ACTIVE);
		Map<Long, Student> byId = studentsById(list);
		List<Enrollment> sorted = new ArrayList<>(list);
		sorted.sort(Comparator.comparing((Enrollment e) -> byId.get(e.getStudentId()).getFullName(),
				String.CASE_INSENSITIVE_ORDER));
		for (int i = 0; i < sorted.size(); i++) {
			sorted.get(i).setRollNo(i + 1);
		}
		audit.log(AuditAction.UPDATE, "section", sectionId, Map.of("change", "roll numbers renumbered"));
		return sectionStudents(sectionId, false);
	}

	/**
	 * Year-end promotion of one section. Chosen students move to the target
	 * section (next year); everyone else in the source section is detained, and
	 * optionally placed in a section of the same class next year.
	 */
	@Transactional
	public StudentDto.PromotionResult promote(StudentDto.PromotionRequest request) {
		Long schoolId = TenantContext.require();
		Section source = sectionService.require(request.sourceSectionId());
		Section target = sectionService.require(request.targetSectionId());
		AcademicYear fromYear = yearService.require(source.getAcademicYearId());
		AcademicYear toYear = yearService.require(target.getAcademicYearId());
		if (!toYear.getStartDate().isAfter(fromYear.getStartDate())) {
			throw new BadRequestException("The target section must be in a later academic year");
		}
		Section detainSection = null;
		if (request.detainSectionId() != null) {
			detainSection = sectionService.require(request.detainSectionId());
			if (!detainSection.getAcademicYearId().equals(toYear.getId())
					|| !detainSection.getClassId().equals(source.getClassId())) {
				throw new BadRequestException("Detained students must go to a section of the same class in "
						+ toYear.getName());
			}
		}

		List<Enrollment> active = enrollments.findBySchoolIdAndSectionIdAndStatus(schoolId, source.getId(),
				Enrollment.Status.ACTIVE);
		if (active.isEmpty()) {
			throw new BadRequestException("There are no active students in this section");
		}
		Set<Long> activeIds = active.stream().map(Enrollment::getId).collect(Collectors.toSet());
		Set<Long> promoteIds = new HashSet<>(request.promoteEnrollmentIds());
		if (!activeIds.containsAll(promoteIds)) {
			throw new BadRequestException("Some selected students are not active in this section");
		}

		Map<Long, Student> byId = studentsById(active);
		List<String> alreadyPlaced = enrollments
			.findBySchoolIdAndAcademicYearIdAndStudentIdIn(schoolId, toYear.getId(), byId.keySet())
			.stream()
			.map(e -> byId.get(e.getStudentId()).getFullName())
			.sorted()
			.toList();
		if (!alreadyPlaced.isEmpty()) {
			throw new ConflictException("Already enrolled in " + toYear.getName() + ": " + String.join(", ", alreadyPlaced));
		}

		Comparator<Enrollment> byName = Comparator.comparing(e -> byId.get(e.getStudentId()).getFullName(),
				String.CASE_INSENSITIVE_ORDER);
		List<Enrollment> promoted = active.stream().filter(e -> promoteIds.contains(e.getId())).sorted(byName).toList();
		List<Enrollment> detained = active.stream().filter(e -> !promoteIds.contains(e.getId())).sorted(byName).toList();

		promoted.forEach(e -> e.setStatus(Enrollment.Status.PROMOTED));
		detained.forEach(e -> e.setStatus(Enrollment.Status.DETAINED));
		placeAll(promoted, byId, target, toYear.getStartDate());
		if (detainSection != null) {
			placeAll(detained, byId, detainSection, toYear.getStartDate());
		}

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("toSectionId", target.getId());
		details.put("promoted", promoted.size());
		details.put("detained", detained.size());
		audit.log(AuditAction.UPDATE, "section", source.getId(), details);
		return new StudentDto.PromotionResult(promoted.size(), detained.size(), detainSection == null ? 0 : detained.size());
	}

	/** Called when a student leaves: closes their active enrollment. */
	void closeActive(Long studentId, LocalDate leftOn) {
		enrollments.findBySchoolIdAndStudentIdOrderByJoinedOnDesc(TenantContext.require(), studentId)
			.stream()
			.filter(e -> e.getStatus() == Enrollment.Status.ACTIVE)
			.forEach(e -> {
				e.setStatus(Enrollment.Status.LEFT);
				e.setLeftOn(leftOn);
			});
	}

	/** A student's enrollments, newest first, with year and section names. */
	@Transactional(readOnly = true)
	public List<StudentDto.EnrollmentView> history(Long studentId) {
		return views(enrollments.findBySchoolIdAndStudentIdOrderByJoinedOnDesc(TenantContext.require(), studentId));
	}

	private Enrollment require(Long id) {
		return enrollments.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Enrollment"));
	}

	private void placeAll(List<Enrollment> from, Map<Long, Student> byId, Section section, LocalDate joinedOn) {
		for (Enrollment old : from) {
			enroll(byId.get(old.getStudentId()), new StudentDto.Placement(section.getId(), null, joinedOn), joinedOn);
		}
	}

	private Integer resolveRollNo(Long sectionId, Integer requested, Long ignoreEnrollmentId) {
		Long schoolId = TenantContext.require();
		if (requested == null) {
			return enrollments.maxRollNo(schoolId, sectionId) + 1;
		}
		boolean taken = ignoreEnrollmentId == null
				? enrollments.existsBySchoolIdAndSectionIdAndRollNoAndStatus(schoolId, sectionId, requested,
						Enrollment.Status.ACTIVE)
				: enrollments.existsBySchoolIdAndSectionIdAndRollNoAndStatusAndIdNot(schoolId, sectionId, requested,
						Enrollment.Status.ACTIVE, ignoreEnrollmentId);
		if (taken) {
			throw new ConflictException("Roll number " + requested + " is already used in this section");
		}
		return requested;
	}

	private Map<Long, Student> studentsById(List<Enrollment> list) {
		if (list.isEmpty()) {
			return Map.of();
		}
		return students
			.findBySchoolIdAndIdIn(TenantContext.require(), list.stream().map(Enrollment::getStudentId).toList())
			.stream()
			.collect(Collectors.toMap(Student::getId, Function.identity()));
	}

	List<StudentDto.EnrollmentView> views(List<Enrollment> list) {
		if (list.isEmpty()) {
			return List.of();
		}
		Map<Long, SectionDto.Response> sections = sectionService
			.describe(list.stream().map(Enrollment::getSectionId).collect(Collectors.toSet()));
		Map<Long, String> yearNames = yearService.list()
			.stream()
			.collect(Collectors.toMap(AcademicYearDto.Response::id, AcademicYearDto.Response::name));
		return list.stream().map(e -> {
			SectionDto.Response s = sections.get(e.getSectionId());
			return new StudentDto.EnrollmentView(e.getId(), e.getAcademicYearId(), yearNames.get(e.getAcademicYearId()),
					e.getSectionId(), s.classId(), s.className(), s.name(), e.getRollNo(), e.getStatus(),
					e.getJoinedOn(), e.getLeftOn());
		}).toList();
	}

}
