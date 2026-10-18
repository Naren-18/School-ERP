package com.schoolerp.school.dto;

import com.schoolerp.school.entity.School;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSchoolProfileRequest(
		@NotBlank(message = "School name is required") @Size(max = 150) String name,
		@NotNull(message = "Board is required") School.Board board,
		@NotBlank(message = "Medium is required") @Size(max = 30) String medium,
		@Size(max = 500) String address,
		@Pattern(regexp = "^([0-9+\\- ]{6,15})?$", message = "Enter a valid phone number") String phone,
		@Email(message = "Enter a valid email") @Size(max = 120) String email,
		@NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Pick a colour") String primaryColor) {
}
