package com.schoolerp.school.dto;

import com.schoolerp.school.entity.School;
import com.schoolerp.school.entity.SchoolSettings;

public record SchoolProfileResponse(Long id, String code, String name, String board, String medium,
		String address, String phone, String email, String logoUrl, String primaryColor, SchoolSettings settings) {

	public static SchoolProfileResponse of(School s) {
		return new SchoolProfileResponse(s.getId(), s.getCode(), s.getName(), s.getBoard().name(), s.getMedium(),
				s.getAddress(), s.getPhone(), s.getEmail(), s.getLogoUrl(), s.getPrimaryColor(), s.getSettings());
	}

}
