package com.schoolerp.school.dto;

import com.schoolerp.school.entity.School;
import com.schoolerp.school.entity.SchoolSettings;

public record SchoolSummary(Long id, String code, String name, String board, String medium, String logoUrl,
		String primaryColor, SchoolSettings.Modules modules, SchoolSettings.Messages messages) {

	public static SchoolSummary of(School school) {
		SchoolSettings settings = school.getSettings();
		return new SchoolSummary(school.getId(), school.getCode(), school.getName(), school.getBoard().name(),
				school.getMedium(), school.getLogoUrl(), school.getPrimaryColor(), settings.modules(),
				settings.messages());
	}

}
