package com.schoolerp.attendance.service;

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
 * Attendance permissions: principal and admin work with every section and any
 * past day; a teacher only with sections they class-teach or teach a subject in,
 * and may change only today's attendance.
 */
@Component
@RequiredArgsConstructor
class AttendanceAccess {

	private final TeacherScopeService teacherScope;

	boolean isAdminLike() {
		AuthUser user = CurrentUser.get();
		return user.hasRole(Role.PRINCIPAL) || user.hasRole(Role.ADMIN);
	}

	/** Sections the user is limited to, or null for all. */
	Set<Long> sectionScope() {
		return isAdminLike() ? null : teacherScope.sectionIdsFor(CurrentUser.get().userId());
	}

	/** 404 (not 403) outside the user's sections, as for other schools' records. */
	void requireSection(Section section) {
		Set<Long> scope = sectionScope();
		if (scope != null && !scope.contains(section.getId())) {
			throw new NotFoundException("Section");
		}
	}

}
