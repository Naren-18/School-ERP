package com.schoolerp.dashboard.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.auth.Access;
import com.schoolerp.dashboard.dto.DashboardDto;
import com.schoolerp.dashboard.service.DashboardService;

import lombok.RequiredArgsConstructor;

/** The first screen after login; what it contains depends on the role (see {@link DashboardDto}). */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboard;

	@GetMapping
	@PreAuthorize(Access.ANY_STAFF)
	DashboardDto get() {
		return dashboard.get();
	}

}
