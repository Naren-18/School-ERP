package com.schoolerp.auth.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.auth.dto.StaffOption;
import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.repository.AppUserRepository;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Read-only staff lookups for other modules (pickers, display names, teacher checks). */
@Service
@RequiredArgsConstructor
public class StaffService {

	private final AppUserRepository users;

	@Transactional(readOnly = true)
	public List<StaffOption> activeStaff(Role role) {
		return users.findBySchoolIdAndActiveTrueOrderByFullNameAsc(TenantContext.require())
			.stream()
			.filter(user -> role == null || user.getRoles().contains(role))
			.map(StaffOption::of)
			.toList();
	}

	/** Names by user id. A mutable map, so {@code get(null)} is safe for optional ids. */
	@Transactional(readOnly = true)
	public Map<Long, String> fullNames(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return new HashMap<>();
		}
		return users.findBySchoolIdAndIdIn(TenantContext.require(), ids)
			.stream()
			.collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
	}

	/**
	 * The user must be an active TEACHER in this school. Unknown ids and other
	 * schools' ids get the same error, so nothing leaks.
	 */
	@Transactional(readOnly = true)
	public AppUser requireActiveTeacher(Long userId) {
		return users.findByIdAndSchoolId(userId, TenantContext.require())
			.filter(AppUser::isActive)
			.filter(user -> user.getRoles().contains(Role.TEACHER))
			.orElseThrow(() -> new BadRequestException("Select an active staff member with the Teacher role"));
	}

}
