package com.schoolerp.auth.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.auth.Access;
import com.schoolerp.auth.dto.StaffOption;
import com.schoolerp.auth.dto.UserDto;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.service.StaffService;
import com.schoolerp.auth.service.UserService;
import com.schoolerp.common.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	private final StaffService staffService;

	@GetMapping("/users")
	@PreAuthorize(Access.PRINCIPAL)
	PageResponse<UserDto.Response> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) Role role, @RequestParam(required = false) Boolean active,
			@PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
		return userService.search(q, role, active, pageable);
	}

	@GetMapping("/users/{id}")
	@PreAuthorize(Access.PRINCIPAL)
	UserDto.Response get(@PathVariable Long id) {
		return userService.get(id);
	}

	@PostMapping("/users")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Access.PRINCIPAL)
	UserDto.Response create(@Valid @RequestBody UserDto.CreateRequest request) {
		return userService.create(request);
	}

	@PutMapping("/users/{id}")
	@PreAuthorize(Access.PRINCIPAL)
	UserDto.Response update(@PathVariable Long id, @Valid @RequestBody UserDto.UpdateRequest request) {
		return userService.update(id, request);
	}

	@PostMapping("/users/{id}/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Access.PRINCIPAL)
	void resetPassword(@PathVariable Long id, @Valid @RequestBody UserDto.ResetPasswordRequest request) {
		userService.resetPassword(id, request);
	}

	@PostMapping("/users/{id}/deactivate")
	@PreAuthorize(Access.PRINCIPAL)
	UserDto.Response deactivate(@PathVariable Long id) {
		return userService.setActive(id, false);
	}

	@PostMapping("/users/{id}/activate")
	@PreAuthorize(Access.PRINCIPAL)
	UserDto.Response activate(@PathVariable Long id) {
		return userService.setActive(id, true);
	}

	/** Active staff for pickers (class teacher, subject teacher). */
	@GetMapping("/staff")
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	List<StaffOption> staff(@RequestParam(required = false) Role role) {
		return staffService.activeStaff(role);
	}

}
