package com.schoolerp.fee.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.service.FeeSetupService;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Fee heads and per-class plans. Principal, admin and accountant. */
@RestController
@RequestMapping("/api/v1/fees")
@RequiresModule(Module.FEES)
@PreAuthorize(Access.FEE_STAFF)
@RequiredArgsConstructor
public class FeeSetupController {

	private final FeeSetupService setup;

	@GetMapping("/heads")
	List<FeeDto.Head> heads() {
		return setup.heads();
	}

	@PostMapping("/heads")
	@ResponseStatus(HttpStatus.CREATED)
	FeeDto.Head createHead(@Valid @RequestBody FeeDto.HeadRequest request) {
		return setup.createHead(request);
	}

	@PutMapping("/heads/{id}")
	FeeDto.Head updateHead(@PathVariable Long id, @Valid @RequestBody FeeDto.HeadRequest request) {
		return setup.updateHead(id, request);
	}

	@DeleteMapping("/heads/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteHead(@PathVariable Long id) {
		setup.deleteHead(id);
	}

	@GetMapping("/plan")
	List<FeeDto.Installment> plan(@RequestParam Long academicYearId, @RequestParam(required = false) Long classId) {
		return setup.plan(academicYearId, classId);
	}

	@PostMapping("/plan")
	@ResponseStatus(HttpStatus.CREATED)
	FeeDto.Installment createInstallment(@Valid @RequestBody FeeDto.InstallmentRequest request) {
		return setup.createInstallment(request);
	}

	@PutMapping("/plan/{id}")
	FeeDto.Installment updateInstallment(@PathVariable Long id, @Valid @RequestBody FeeDto.InstallmentRequest request) {
		return setup.updateInstallment(id, request);
	}

	@DeleteMapping("/plan/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteInstallment(@PathVariable Long id) {
		setup.deleteInstallment(id);
	}

	@PostMapping("/plan/copy")
	FeeDto.CopyPlanResult copyPlan(@Valid @RequestBody FeeDto.CopyPlanRequest request) {
		return setup.copyPlan(request);
	}

	@PostMapping("/dues/generate")
	FeeDto.GenerateDuesResult generateDues(@Valid @RequestBody FeeDto.GenerateDuesRequest request) {
		return setup.generateDues(request);
	}

}
