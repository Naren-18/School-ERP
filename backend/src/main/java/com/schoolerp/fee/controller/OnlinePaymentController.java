package com.schoolerp.fee.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.auth.Access;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.service.OnlinePaymentService;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Taking a fee payment online (Razorpay) at the counter. */
@RestController
@RequestMapping("/api/v1/fees/online")
@RequiresModule(Module.FEES)
@PreAuthorize(Access.FEE_STAFF)
@RequiredArgsConstructor
public class OnlinePaymentController {

	private final OnlinePaymentService online;

	@GetMapping("/config")
	FeeDto.OnlineConfig config() {
		return online.config();
	}

	@PostMapping("/orders")
	@ResponseStatus(HttpStatus.CREATED)
	FeeDto.OnlineOrder start(@Valid @RequestBody FeeDto.OnlineOrderRequest request) {
		return online.start(request);
	}

	@PostMapping("/orders/{id}/complete")
	FeeDto.Receipt complete(@PathVariable Long id, @Valid @RequestBody FeeDto.OnlineCompleteRequest request) {
		return online.complete(id, request);
	}

}
