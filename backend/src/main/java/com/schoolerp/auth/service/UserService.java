package com.schoolerp.auth.service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.auth.dto.UserDto;
import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.repository.AppUserRepository;
import com.schoolerp.common.dto.PageResponse;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Staff accounts and roles (principal only). Every change is audited. */
@Service
@RequiredArgsConstructor
public class UserService {

	private static final String ENTITY = "app_user";

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AuditService audit;

	/** Columns the user list may be sorted by; anything else falls back to name. */
	private static final Set<String> SORTABLE = Set.of("fullName", "username", "lastLoginAt", "createdAt");

	@Transactional(readOnly = true)
	public PageResponse<UserDto.Response> search(String q, Role role, Boolean active, Pageable pageable) {
		String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
		Sort sort = Sort.by(pageable.getSort().filter(order -> SORTABLE.contains(order.getProperty())).toList());
		Pageable safe = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				sort.isSorted() ? sort : Sort.by("fullName"));
		return PageResponse.from(users.search(TenantContext.require(), pattern, role, active, safe),
				UserDto.Response::of);
	}

	@Transactional(readOnly = true)
	public UserDto.Response get(Long id) {
		return UserDto.Response.of(require(id));
	}

	@Transactional
	public UserDto.Response create(UserDto.CreateRequest request) {
		String username = request.username().trim();
		if (users.existsBySchoolIdAndUsernameIgnoreCase(TenantContext.require(), username)) {
			throw new ConflictException("The username " + username + " is already taken");
		}
		AppUser user = new AppUser();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		applyDetails(user, request.fullName(), request.designation(), request.phone(), request.email());
		user.setRoles(EnumSet.copyOf(request.roles()));
		users.save(user);
		audit.log(AuditAction.CREATE, ENTITY, user.getId(), Map.of("username", username, "roles", sortedRoles(user)));
		return UserDto.Response.of(user);
	}

	@Transactional
	public UserDto.Response update(Long id, UserDto.UpdateRequest request) {
		AppUser user = require(id);
		EnumSet<Role> newRoles = EnumSet.copyOf(request.roles());
		if (user.getRoles().contains(Role.PRINCIPAL) && !newRoles.contains(Role.PRINCIPAL)) {
			requireAnotherActivePrincipal(user, "remove the Principal role from");
		}
		boolean rolesChanged = !user.getRoles().equals(newRoles);
		Object before = sortedRoles(user);
		applyDetails(user, request.fullName(), request.designation(), request.phone(), request.email());
		user.setRoles(newRoles);

		Map<String, Object> details = new LinkedHashMap<>();
		if (rolesChanged) {
			details.put("rolesBefore", before);
			details.put("rolesAfter", sortedRoles(user));
		}
		audit.log(AuditAction.UPDATE, ENTITY, user.getId(), details.isEmpty() ? null : details);
		return UserDto.Response.of(user);
	}

	@Transactional
	public void resetPassword(Long id, UserDto.ResetPasswordRequest request) {
		AppUser user = require(id);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		audit.log(AuditAction.UPDATE, ENTITY, user.getId(), Map.of("change", "password reset"));
	}

	@Transactional
	public UserDto.Response setActive(Long id, boolean active) {
		AppUser user = require(id);
		if (!active) {
			if (Objects.equals(user.getId(), CurrentUser.get().userId())) {
				throw new ConflictException("You cannot deactivate your own account");
			}
			if (user.getRoles().contains(Role.PRINCIPAL)) {
				requireAnotherActivePrincipal(user, "deactivate");
			}
		}
		if (user.isActive() != active) {
			user.setActive(active);
			audit.log(AuditAction.STATUS_CHANGE, ENTITY, user.getId(), Map.of("active", active));
		}
		return UserDto.Response.of(user);
	}

	private AppUser require(Long id) {
		return users.findByIdAndSchoolId(id, TenantContext.require()).orElseThrow(() -> new NotFoundException("User"));
	}

	/** Keeps the school from losing its last way into settings and users. */
	private void requireAnotherActivePrincipal(AppUser user, String action) {
		long activePrincipals = users.countActiveWithRole(user.getSchoolId(), Role.PRINCIPAL);
		long others = user.isActive() ? activePrincipals - 1 : activePrincipals;
		if (others < 1) {
			throw new ConflictException("You cannot " + action + " the only active principal");
		}
	}

	private static void applyDetails(AppUser user, String fullName, String designation, String phone, String email) {
		user.setFullName(fullName.trim());
		user.setDesignation(blankToNull(designation));
		user.setPhone(blankToNull(phone));
		user.setEmail(blankToNull(email));
	}

	private static Object sortedRoles(AppUser user) {
		return user.getRoles().stream().map(Role::name).sorted().toList();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
