package com.schoolerp.exam.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.TeacherScopeService;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.service.AuthUser;
import com.schoolerp.auth.service.CurrentUser;
import com.schoolerp.common.error.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Exam permissions: principal and admin everything; a teacher enters marks only
 * for subjects assigned to them in that section, and sees results of sections
 * they teach or class-teach. Outside that: 404, as for other schools' records.
 */
@Component
@RequiredArgsConstructor
class ExamAccess {

	private final TeacherScopeService teacherScope;

	boolean isAdminLike() {
		AuthUser user = CurrentUser.get();
		return user.hasRole(Role.PRINCIPAL) || user.hasRole(Role.ADMIN);
	}

	/** (sectionId, subjectId) pairs the user may enter marks for, or null for all. */
	Set<List<Long>> markPairs() {
		return isAdminLike() ? null : teacherScope.assignedPairs(CurrentUser.get().userId());
	}

	void requireMarksEntry(Section section, Long subjectId) {
		if (!isAdminLike() && !teacherScope.teaches(CurrentUser.get().userId(), section.getId(), subjectId)) {
			throw new NotFoundException("Section");
		}
	}

	/** Sections whose results the user may see (taught or class-teacher), or null for all. */
	Set<Long> resultSectionIds() {
		return isAdminLike() ? null : teacherScope.sectionIdsFor(CurrentUser.get().userId());
	}

	void requireResultsView(Section section) {
		Set<Long> allowed = resultSectionIds();
		if (allowed != null && !allowed.contains(section.getId())) {
			throw new NotFoundException("Section");
		}
	}

}
