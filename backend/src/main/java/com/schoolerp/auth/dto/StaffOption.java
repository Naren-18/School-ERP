package com.schoolerp.auth.dto;

import java.util.List;

import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;

/** Minimal staff info for pickers (class teacher, subject teacher). */
public record StaffOption(Long id, String fullName, String designation, List<String> roles) {

	public static StaffOption of(AppUser user) {
		return new StaffOption(user.getId(), user.getFullName(), user.getDesignation(),
				user.getRoles().stream().map(Role::name).sorted().toList());
	}

}
