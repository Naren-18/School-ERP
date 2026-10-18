package com.schoolerp.academic.dto;

import java.util.List;

import com.schoolerp.academic.entity.Subject;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ClassSubjectDto {

	private ClassSubjectDto() {
	}

	/** Replaces the class's subjects for the year; list order becomes display order. */
	public record Request(
			@NotNull(message = "Academic year is required") Long academicYearId,
			@NotNull(message = "Class is required") Long classId,
			@NotNull @Size(max = 40) List<@NotNull Long> subjectIds) {
	}

	public record Response(Long id, Long subjectId, String subjectName, String subjectCode, Subject.Type type,
			int displayOrder) {
	}

}
