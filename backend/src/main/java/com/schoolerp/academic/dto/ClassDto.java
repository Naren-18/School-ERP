package com.schoolerp.academic.dto;

import com.schoolerp.academic.entity.SchoolClass;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ClassDto {

	private ClassDto() {
	}

	public record Request(
			@NotBlank(message = "Name is required") @Size(max = 30) String name,
			@NotNull(message = "Order is required") @Min(0) @Max(999) Integer displayOrder) {
	}

	public record Response(Long id, String name, int displayOrder) {

		public static Response of(SchoolClass c) {
			return new Response(c.getId(), c.getName(), c.getDisplayOrder());
		}

	}

}
