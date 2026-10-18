package com.schoolerp.school.dto;

import com.schoolerp.school.entity.School;

public record BrandingResponse(String code, String name, String logoUrl, String primaryColor) {

	public static BrandingResponse of(School school) {
		return new BrandingResponse(school.getCode(), school.getName(), school.getLogoUrl(),
				school.getPrimaryColor());
	}

}
