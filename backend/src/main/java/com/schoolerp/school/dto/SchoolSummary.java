package com.schoolerp.school.dto;

import com.schoolerp.school.entity.School;

public record SchoolSummary(Long id, String code, String name, String board, String medium, String logoUrl,
		String primaryColor) {

	public static SchoolSummary of(School school) {
		return new SchoolSummary(school.getId(), school.getCode(), school.getName(), school.getBoard().name(),
				school.getMedium(), school.getLogoUrl(), school.getPrimaryColor());
	}

}
