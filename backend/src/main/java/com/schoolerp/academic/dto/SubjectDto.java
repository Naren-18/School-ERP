package com.schoolerp.academic.dto;

import com.schoolerp.academic.entity.Subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SubjectDto {

	private SubjectDto() {
	}

	public record Request(
			@NotBlank(message = "Name is required") @Size(max = 60) String name,
			@Size(max = 15) String code,
			@NotNull(message = "Type is required") Subject.Type type) {
	}

	public record Response(Long id, String name, String code, Subject.Type type) {

		public static Response of(Subject s) {
			return new Response(s.getId(), s.getName(), s.getCode(), s.getType());
		}

	}

}
