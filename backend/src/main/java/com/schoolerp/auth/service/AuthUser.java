package com.schoolerp.auth.service;

import java.util.Set;

import com.schoolerp.auth.entity.Role;

/** The authenticated principal, built from validated JWT claims. */
public record AuthUser(Long userId, Long schoolId, String name, Set<Role> roles) {

	public boolean hasRole(Role role) {
		return roles.contains(role);
	}

}
