package com.schoolerp.academic.dto;

import java.time.LocalDate;

import com.schoolerp.academic.entity.Holiday;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class HolidayDto {

	private HolidayDto() {
	}

	public record Request(
			@NotNull(message = "Academic year is required") Long academicYearId,
			@NotNull(message = "Date is required") LocalDate date,
			@NotBlank(message = "Description is required") @Size(max = 100) String description,
			/** When attendance is already marked that day: true clears it, false keeps it, null asks (409). */
			Boolean clearAttendance) {
	}

	public record Response(Long id, Long academicYearId, LocalDate date, String description) {

		public static Response of(Holiday h) {
			return new Response(h.getId(), h.getAcademicYearId(), h.getDate(), h.getDescription());
		}

	}

}
