package com.schoolerp.academic.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.entity.TeacherAssignment;
import com.schoolerp.academic.repository.SectionRepository;
import com.schoolerp.academic.repository.TeacherAssignmentRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Which sections a teacher works with: class teacher of, or assigned a subject in. */
@Service
@RequiredArgsConstructor
public class TeacherScopeService {

	private final SectionRepository sections;

	private final TeacherAssignmentRepository assignments;

	@Transactional(readOnly = true)
	public Set<Long> sectionIdsFor(Long teacherId) {
		Long schoolId = TenantContext.require();
		Set<Long> ids = new HashSet<>();
		sections.findBySchoolIdAndClassTeacherId(schoolId, teacherId).stream().map(Section::getId).forEach(ids::add);
		assignments.findBySchoolIdAndTeacherId(schoolId, teacherId)
			.stream()
			.map(TeacherAssignment::getSectionId)
			.forEach(ids::add);
		return ids;
	}

	/** Is the teacher assigned this subject in this section (class teacher alone is not enough)? */
	@Transactional(readOnly = true)
	public boolean teaches(Long teacherId, Long sectionId, Long subjectId) {
		return assignments.findBySchoolIdAndSectionIdAndSubjectId(TenantContext.require(), sectionId, subjectId)
			.filter(a -> a.getTeacherId().equals(teacherId))
			.isPresent();
	}

	/** (sectionId, subjectId) pairs the teacher is assigned. */
	@Transactional(readOnly = true)
	public Set<List<Long>> assignedPairs(Long teacherId) {
		Set<List<Long>> pairs = new HashSet<>();
		assignments.findBySchoolIdAndTeacherId(TenantContext.require(), teacherId)
			.forEach(a -> pairs.add(List.of(a.getSectionId(), a.getSubjectId())));
		return pairs;
	}

}
