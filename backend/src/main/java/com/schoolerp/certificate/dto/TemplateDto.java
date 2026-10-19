package com.schoolerp.certificate.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import com.schoolerp.certificate.entity.TemplateType;

import jakarta.validation.constraints.NotBlank;

public final class TemplateDto {

	private TemplateDto() {
	}

	/** @param custom false while the school uses the built-in default */
	public record View(TemplateType type, String title, String html, boolean custom, OffsetDateTime updatedAt,
			Map<String, String> values, Map<String, String> blocks) {
	}

	public record SaveRequest(@NotBlank(message = "The template is empty") String html) {
	}

}
