package com.schoolerp.fee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.schoolerp.fee.entity.PaymentMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class FeeDto {

	private FeeDto() {
	}

	// --- Setup -------------------------------------------------------------

	public record HeadRequest(@NotBlank(message = "Name is required") @Size(max = 60) String name, Boolean active) {
	}

	public record Head(Long id, String name, boolean active) {
	}

	public record InstallmentRequest(
			@NotNull(message = "Academic year is required") Long academicYearId,
			@NotNull(message = "Class is required") Long classId,
			@NotNull(message = "Fee head is required") Long feeHeadId,
			@NotBlank(message = "Name is required") @Size(max = 40) String name,
			@NotNull(message = "Amount is required") @DecimalMin(value = "0.00", message = "Cannot be negative")
			@Digits(integer = 8, fraction = 2, message = "Up to 2 decimal places") BigDecimal amount,
			@NotNull(message = "Due date is required") LocalDate dueDate) {
	}

	public record Installment(Long id, Long academicYearId, Long classId, String className, Long feeHeadId,
			String feeHeadName, String name, BigDecimal amount, LocalDate dueDate, int students) {
	}

	public record CopyPlanRequest(@NotNull Long fromAcademicYearId, @NotNull Long toAcademicYearId) {
	}

	public record CopyPlanResult(int copied, int skipped) {
	}

	/** Creates missing dues for every enrolled student of the class (or of every class). */
	public record GenerateDuesRequest(@NotNull(message = "Academic year is required") Long academicYearId,
			Long classId) {
	}

	public record GenerateDuesResult(int created) {
	}

	public record ConcessionRequest(
			@NotNull @DecimalMin(value = "0.00", message = "Cannot be negative")
			@Digits(integer = 8, fraction = 2, message = "Up to 2 decimal places") BigDecimal concession,
			@Size(max = 100) String reason) {
	}

	// --- A student's fees ----------------------------------------------------

	public record Due(Long id, Long enrollmentId, Long academicYearId, String academicYearName, Long installmentId,
			String installmentName, String feeHeadName, LocalDate dueDate, BigDecimal amount, BigDecimal concession,
			String concessionReason, BigDecimal paid, BigDecimal balance, boolean overdue) {
	}

	public record Totals(BigDecimal net, BigDecimal paid, BigDecimal balance, BigDecimal overdue) {
	}

	public record StudentFees(Long studentId, String fullName, String admissionNo, String classLabel, Integer rollNo,
			String guardianName, String guardianPhone, Totals totals, List<Due> dues, List<ReceiptSummary> receipts) {
	}

	// --- Collecting ----------------------------------------------------------

	public record CollectLine(@NotNull Long dueId,
			@NotNull @DecimalMin(value = "0.01", message = "Must be more than zero")
			@Digits(integer = 8, fraction = 2, message = "Up to 2 decimal places") BigDecimal amount) {
	}

	public record CollectRequest(
			@NotNull(message = "Student is required") Long studentId,
			@PastOrPresent(message = "Cannot be in the future") LocalDate paidOn,
			@NotNull(message = "Payment mode is required") PaymentMode paymentMode,
			@Size(max = 60) String referenceNo,
			@NotEmpty(message = "Choose at least one due to pay") @Size(max = 50) List<@Valid CollectLine> lines) {
	}

	public record CancelRequest(@NotBlank(message = "A reason is required") @Size(max = 200) String reason) {
	}

	public record ReceiptLine(Long dueId, String installmentName, String feeHeadName, BigDecimal amount) {
	}

	public record Receipt(Long id, String receiptNo, Long studentId, String studentName, String admissionNo,
			String classLabel, String academicYearName, LocalDate paidOn, BigDecimal totalAmount, String amountInWords,
			PaymentMode paymentMode, String referenceNo, String collectedByName, OffsetDateTime createdAt,
			boolean cancelled, String cancelReason, String cancelledByName, OffsetDateTime cancelledAt,
			List<ReceiptLine> lines) {
	}

	public record ReceiptSummary(Long id, String receiptNo, LocalDate paidOn, Long studentId, String studentName,
			String admissionNo, String classLabel, BigDecimal totalAmount, PaymentMode paymentMode,
			boolean cancelled) {
	}

	// --- Reports -------------------------------------------------------------

	public record CollectionDay(LocalDate date, PaymentMode paymentMode, int receipts, BigDecimal total) {
	}

	public record CollectionReport(LocalDate from, LocalDate to, BigDecimal total, int receipts,
			Map<PaymentMode, BigDecimal> byMode, List<CollectionDay> days) {
	}

	public record DueRow(Long studentId, String admissionNo, String fullName, String classLabel, Integer rollNo,
			String guardianPhone, BigDecimal net, BigDecimal paid, BigDecimal balance, BigDecimal overdue) {
	}

	public record ClassSummary(Long classId, String className, int students, BigDecimal expected, BigDecimal collected,
			BigDecimal outstanding, Double collectedPercent) {
	}

}
