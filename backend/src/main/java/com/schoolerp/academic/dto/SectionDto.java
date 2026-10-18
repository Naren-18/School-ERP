package com.schoolerp.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SectionDto {

	private SectionDto() {
	}

	public record Request(
			@NotNull(message = "Academic year is required") Long academicYearId,
			@NotNull(message = "Class is required") Long classId,
			@NotBlank(message = "Name is required") @Size(max = 10) String name,
			Long classTeacherId,
			@Min(1) @Max(200) Integer capacity) {
	}

	public record Response(Long id, Long academicYearId, Long classId, String className, int classOrder,
			String name, Long classTeacherId, String classTeacherName, Integer capacity) {
	}

}
