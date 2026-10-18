package com.schoolerp.auth.dto;

import java.util.List;

import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.school.dto.SchoolSummary;
import com.schoolerp.school.entity.School;

public record MeResponse(UserSummary user, List<String> roles, SchoolSummary school) {

	public record UserSummary(Long id, String username, String fullName, String designation) {
	}

	public static MeResponse of(AppUser user, School school) {
		return new MeResponse(
				new UserSummary(user.getId(), user.getUsername(), user.getFullName(), user.getDesignation()),
				user.getRoles().stream().map(Role::name).sorted().toList(), SchoolSummary.of(school));
	}

}
