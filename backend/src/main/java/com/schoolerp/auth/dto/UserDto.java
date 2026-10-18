package com.schoolerp.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDto {

	static final String PHONE = "^([0-9+\\- ]{6,15})?$";

	private UserDto() {
	}

	public record CreateRequest(
			@NotBlank(message = "Username is required")
			@Pattern(regexp = "^[a-zA-Z0-9._-]{3,60}$",
					message = "3-60 letters, digits, dot, dash or underscore") String username,
			@NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
			@Size(max = 60) String designation,
			@Pattern(regexp = PHONE, message = "Enter a valid phone number") String phone,
			@Email(message = "Enter a valid email") @Size(max = 120) String email,
			@NotEmpty(message = "Choose at least one role") Set<Role> roles,
			@NotBlank(message = "Password is required")
			@Size(min = 8, max = 100, message = "Password must be at least 8 characters") String password) {
	}

	public record UpdateRequest(
			@NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
			@Size(max = 60) String designation,
			@Pattern(regexp = PHONE, message = "Enter a valid phone number") String phone,
			@Email(message = "Enter a valid email") @Size(max = 120) String email,
			@NotEmpty(message = "Choose at least one role") Set<Role> roles) {
	}

	public record ResetPasswordRequest(
			@NotBlank(message = "Password is required")
			@Size(min = 8, max = 100, message = "Password must be at least 8 characters") String password) {
	}

	public record Response(Long id, String username, String fullName, String designation, String phone,
			String email, List<String> roles, boolean active, OffsetDateTime lastLoginAt) {

		public static Response of(AppUser u) {
			return new Response(u.getId(), u.getUsername(), u.getFullName(), u.getDesignation(), u.getPhone(),
					u.getEmail(), u.getRoles().stream().map(Role::name).sorted().toList(), u.isActive(),
					u.getLastLoginAt());
		}

	}

}
