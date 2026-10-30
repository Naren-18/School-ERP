package com.schoolerp.notification.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.auth.Access;
import com.schoolerp.common.dto.PageResponse;
import com.schoolerp.notification.dto.NotificationDto;
import com.schoolerp.notification.entity.MessageType;
import com.schoolerp.notification.entity.NotificationLog;
import com.schoolerp.notification.service.NotificationService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** WhatsApp status and the log of messages sent to parents. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notifications;

	@GetMapping("/status")
	@PreAuthorize(Access.ANY_STAFF)
	NotificationDto.Status status() {
		return notifications.status();
	}

	/** Messages tried in a date range (default: the last 30 days), newest first. */
	@GetMapping
	@PreAuthorize(Access.PRINCIPAL_OR_ADMIN)
	PageResponse<NotificationDto.LogRow> log(@RequestParam(required = false) MessageType type,
			@RequestParam(required = false) NotificationLog.Status status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return notifications.search(type, status, from, to, q, page, size);
	}

}
