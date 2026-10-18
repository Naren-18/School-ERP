package com.schoolerp.academic.dto;

import java.time.LocalDate;

import com.schoolerp.academic.entity.AcademicYear;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AcademicYearDto {

	private AcademicYearDto() {
	}

	public record Request(
			@NotBlank(message = "Name is required") @Size(max = 20) String name,
			@NotNull(message = "Start date is required") LocalDate startDate,
			@NotNull(message = "End date is required") LocalDate endDate) {

		@AssertTrue(message = "End date must be after start date")
		public boolean isEndDateValid() {
			return startDate == null || endDate == null || endDate.isAfter(startDate);
		}

	}

	public record Response(Long id, String name, LocalDate startDate, LocalDate endDate, boolean current) {

		public static Response of(AcademicYear y) {
			return new Response(y.getId(), y.getName(), y.getStartDate(), y.getEndDate(), y.isCurrent());
		}

	}

}
