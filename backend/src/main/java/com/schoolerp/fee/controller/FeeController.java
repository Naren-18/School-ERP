package com.schoolerp.fee.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.schoolerp.common.dto.PageResponse;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.service.FeeCollectionService;
import com.schoolerp.fee.service.FeeDueService;
import com.schoolerp.fee.service.FeeMessageService;
import com.schoolerp.fee.service.ReceiptPdfService;
import com.schoolerp.notification.dto.NotificationDto;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** A student's fees, collecting payments, receipts. */
@RestController
@RequestMapping("/api/v1/fees")
@RequiresModule(Module.FEES)
@PreAuthorize(Access.FEE_STAFF)
@RequiredArgsConstructor
public class FeeController {

	private final FeeCollectionService collection;

	private final FeeDueService dueService;

	private final ReceiptPdfService pdf;

	private final FeeMessageService messages;

	@GetMapping("/students/{studentId}")
	FeeDto.StudentFees studentFees(@PathVariable Long studentId) {
		return collection.studentFees(studentId);
	}

	@PutMapping("/dues/{dueId}/concession")
	FeeDto.Due concession(@PathVariable Long dueId, @Valid @RequestBody FeeDto.ConcessionRequest request) {
		return dueService.setConcession(dueId, request);
	}

	/** Records the payment; afterwards (receipt saved) the parent gets a WhatsApp confirmation if set up. */
	@PostMapping("/receipts")
	@ResponseStatus(HttpStatus.CREATED)
	FeeDto.Receipt collect(@Valid @RequestBody FeeDto.CollectRequest request) {
		FeeDto.Receipt receipt = collection.collect(request);
		messages.confirmPayment(receipt.id());
		return receipt;
	}

	/** WhatsApp reminders to the parents of students chosen on the dues report. */
	@PostMapping("/reminders")
	NotificationDto.SendReport remind(@Valid @RequestBody FeeDto.ReminderRequest request) {
		return messages.remind(request);
	}

	/** Receipts in a date range (default: the last 30 days), newest first. */
	@GetMapping("/receipts")
	PageResponse<FeeDto.ReceiptSummary> receipts(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String q, @RequestParam(required = false) Boolean cancelled,
			@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return collection.search(from, to, q, cancelled, page, size);
	}

	@GetMapping("/receipts/{id}")
	FeeDto.Receipt receipt(@PathVariable Long id) {
		return collection.receipt(id);
	}

	@GetMapping("/receipts/{id}/pdf")
	ResponseEntity<byte[]> receiptPdf(@PathVariable Long id) {
		FeeDto.Receipt receipt = collection.receipt(id);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
				.filename("receipt-" + receipt.receiptNo().replace('/', '-') + ".pdf")
				.build()
				.toString())
			.body(pdf.receiptPdf(id));
	}

	@PostMapping("/receipts/{id}/cancel")
	@PreAuthorize(Access.FEE_CANCEL)
	FeeDto.Receipt cancel(@PathVariable Long id, @Valid @RequestBody FeeDto.CancelRequest request) {
		return collection.cancel(id, request);
	}

}
