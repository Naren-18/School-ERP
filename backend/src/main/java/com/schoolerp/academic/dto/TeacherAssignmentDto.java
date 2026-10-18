package com.schoolerp.academic.dto;

import jakarta.validation.constraints.NotNull;

public final class TeacherAssignmentDto {

	private TeacherAssignmentDto() {
	}

	/** Sets (or replaces) the teacher for a section + subject. */
	public record Request(
			@NotNull(message = "Section is required") Long sectionId,
			@NotNull(message = "Subject is required") Long subjectId,
			@NotNull(message = "Teacher is required") Long teacherId) {
	}

	public record Response(Long id, Long sectionId, String sectionName, Long classId, String className,
			Long subjectId, String subjectName, Long teacherId, String teacherName) {
	}

}
