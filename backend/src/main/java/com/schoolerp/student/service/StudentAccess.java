package com.schoolerp.student.service;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.schoolerp.academic.service.TeacherScopeService;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.service.AuthUser;
import com.schoolerp.auth.service.CurrentUser;

import lombok.RequiredArgsConstructor;

/**
 * Who may see which students and fields (CLAUDE.md roles):
 * principal, admin and accountant see every student; a teacher only students
 * enrolled in sections they teach. Sensitive fields are principal/admin only.
 */
@Component
@RequiredArgsConstructor
public class StudentAccess {

	private final TeacherScopeService teacherScope;

	/** Section ids the current user is limited to, or null when they may see all students. */
	public Set<Long> restrictedSectionIds() {
		AuthUser user = CurrentUser.get();
		if (user.hasRole(Role.PRINCIPAL) || user.hasRole(Role.ADMIN) || user.hasRole(Role.ACCOUNTANT)) {
			return null;
		}
		return teacherScope.sectionIdsFor(user.userId());
	}

	public boolean canSeeSensitive() {
		AuthUser user = CurrentUser.get();
		return user.hasRole(Role.PRINCIPAL) || user.hasRole(Role.ADMIN);
	}

}
