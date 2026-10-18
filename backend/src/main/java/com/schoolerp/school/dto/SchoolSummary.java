package com.schoolerp.school.dto;

import com.schoolerp.school.entity.School;
import com.schoolerp.school.entity.SchoolSettings;

public record SchoolSummary(Long id, String code, String name, String board, String medium, String logoUrl,
		String primaryColor, SchoolSettings.Modules modules) {

	public static SchoolSummary of(School school) {
		return new SchoolSummary(school.getId(), school.getCode(), school.getName(), school.getBoard().name(),
				school.getMedium(), school.getLogoUrl(), school.getPrimaryColor(), school.getSettings().modules());
	}

}
