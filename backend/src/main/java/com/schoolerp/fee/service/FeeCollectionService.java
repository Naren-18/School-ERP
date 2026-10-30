package com.schoolerp.fee.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.auth.service.CurrentUser;
import com.schoolerp.auth.service.StaffService;
import com.schoolerp.common.dto.PageResponse;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.common.money.AmountInWords;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.entity.FeeReceipt;
import com.schoolerp.fee.entity.FeeReceiptLine;
import com.schoolerp.fee.entity.PaymentMode;
import com.schoolerp.fee.entity.StudentFeeDue;
import com.schoolerp.fee.repository.FeeReceiptLineRepository;
import com.schoolerp.fee.repository.FeeReceiptRepository;
import com.schoolerp.fee.repository.ReceiptCounterRepository;
import com.schoolerp.fee.repository.StudentFeeDueRepository;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Enrollment;
import com.schoolerp.student.entity.Student;
import com.schoolerp.student.repository.StudentRepository;
import com.schoolerp.student.service.EnrollmentService;
import com.schoolerp.student.service.StudentService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Taking payments into receipts, cancelling them, and a student's fee account. */
@Service
@RequiredArgsConstructor
public class FeeCollectionService {

	private final StudentFeeDueRepository dues;

	private final FeeReceiptRepository receipts;

	private final FeeReceiptLineRepository lines;

	private final ReceiptCounterRepository counter;

	private final FeeViews views;

	private final StudentService studentService;

	private final StudentRepository students;

	private final EnrollmentService enrollmentService;

	private final SectionService sectionService;

	private final AcademicYearService yearService;

	private final SchoolService schoolService;

	private final StaffService staff;

	private final AuditService audit;

	private final Clock clock;

	/** Everything the collect screen and the profile's Fees tab need: dues of every year, and receipts. */
	@Transactional(readOnly = true)
	public FeeDto.StudentFees studentFees(Long studentId) {
		StudentDto.Detail s = studentService.get(studentId);
		List<Long> enrollmentIds = s.enrollments().stream().map(StudentDto.EnrollmentView::id).toList();
		List<FeeDto.Due> dueViews = enrollmentIds.isEmpty() ? List.of()
				: views.dues(dues.findBySchoolIdAndEnrollmentIdIn(TenantContext.require(), enrollmentIds));
		List<FeeDto.ReceiptSummary> receiptList = enrollmentIds.isEmpty() ? List.of()
				: summaries(receipts.findBySchoolIdAndEnrollmentIdInOrderByPaidOnDescIdDesc(TenantContext.require(),
						enrollmentIds));
		StudentDto.EnrollmentView current = s.currentEnrollment() != null ? s.currentEnrollment()
				: s.enrollments().stream().findFirst().orElse(null);
		StudentDto.GuardianView guardian = s.guardians().stream().findFirst().orElse(null);
		return new FeeDto.StudentFees(s.id(), s.fullName(), s.admissionNo(),
				current == null ? null : current.className() + " – " + current.sectionName(),
				current == null ? null : current.rollNo(), guardian == null ? null : guardian.fullName(),
				guardian == null ? null : guardian.phone(), FeeViews.totals(dueViews), dueViews, receiptList);
	}

	/**
	 * Records a payment against one or more of a student's dues (partial amounts
	 * allowed) in a single transaction, under the next gap-free receipt number.
	 */
	@Transactional
	public FeeDto.Receipt collect(FeeDto.CollectRequest request) {
		Long schoolId = TenantContext.require();
		Student student = studentService.requireVisible(request.studentId());
		Map<Long, BigDecimal> wanted = wanted(request.lines());
		Checked checked = check(student, wanted, true);
		Map<Long, StudentFeeDue> byId = checked.dues();
		Enrollment enrollment = checked.enrollment();
		BigDecimal total = checked.total();
		if (request.paymentMode() == PaymentMode.CHEQUE && blank(request.referenceNo())) {
			throw new BadRequestException("Enter the cheque number");
		}

		AcademicYear year = yearService.require(enrollment.getAcademicYearId());
		FeeReceipt receipt = new FeeReceipt();
		receipt.setAcademicYearId(year.getId());
		receipt.setReceiptNo(receiptNo(year, counter.next(schoolId, year.getId())));
		receipt.setEnrollmentId(enrollment.getId());
		receipt.setPaidOn(request.paidOn() != null ? request.paidOn() : LocalDate.now(clock));
		receipt.setTotalAmount(total);
		receipt.setPaymentMode(request.paymentMode());
		receipt.setReferenceNo(blank(request.referenceNo()) ? null : request.referenceNo().trim());
		receipt.setCollectedBy(CurrentUser.get().userId());
		receipts.save(receipt);

		for (Map.Entry<Long, BigDecimal> e : wanted.entrySet()) {
			FeeReceiptLine line = new FeeReceiptLine();
			line.setReceiptId(receipt.getId());
			line.setStudentFeeDueId(e.getKey());
			line.setAmount(e.getValue());
			lines.save(line);
			StudentFeeDue due = byId.get(e.getKey());
			due.setPaidAmount(due.getPaidAmount().add(e.getValue()));
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("receiptNo", receipt.getReceiptNo());
		details.put("studentId", student.getId());
		details.put("total", total);
		details.put("mode", receipt.getPaymentMode().name());
		audit.log(AuditAction.CREATE, "fee_receipt", receipt.getId(), details);
		return receipt(receipt.getId());
	}

	/**
	 * Checks a payment the way {@link #collect} would, without taking it: used
	 * before starting an online payment. Returns the total to pay.
	 */
	@Transactional(readOnly = true)
	public BigDecimal checkPayable(Long studentId, List<FeeDto.CollectLine> lines) {
		return check(studentService.requireVisible(studentId), wanted(lines), false).total();
	}

	/** The dues being paid (locked when collecting), their academic year's enrollment, and the total. */
	private record Checked(Map<Long, StudentFeeDue> dues, Enrollment enrollment, BigDecimal total) {
	}

	private static Map<Long, BigDecimal> wanted(List<FeeDto.CollectLine> lines) {
		Map<Long, BigDecimal> wanted = new LinkedHashMap<>();
		for (FeeDto.CollectLine line : lines) {
			if (wanted.put(line.dueId(), line.amount()) != null) {
				throw new BadRequestException("The same due appears twice");
			}
		}
		return wanted;
	}

	/**
	 * The dues must exist, belong to this student and one academic year, and
	 * each amount must not exceed what is still due.
	 */
	private Checked check(Student student, Map<Long, BigDecimal> wanted, boolean lock) {
		Long schoolId = TenantContext.require();
		Map<Long, StudentFeeDue> byId = (lock ? dues.lockBySchoolIdAndIdIn(schoolId, wanted.keySet())
				: dues.findBySchoolIdAndIdIn(schoolId, wanted.keySet()))
			.stream()
			.collect(Collectors.toMap(StudentFeeDue::getId, Function.identity()));
		if (byId.size() != wanted.size()) {
			throw new NotFoundException("Due");
		}
		Set<Long> enrollmentIds = byId.values().stream().map(StudentFeeDue::getEnrollmentId).collect(Collectors.toSet());
		if (enrollmentIds.size() != 1) {
			throw new BadRequestException("Pay one academic year's dues per receipt");
		}
		Enrollment enrollment = enrollmentService.require(enrollmentIds.iterator().next());
		if (!enrollment.getStudentId().equals(student.getId())) {
			throw new NotFoundException("Due");
		}

		Map<Long, FeeDto.Due> dueViews = views.dues(byId.values())
			.stream()
			.collect(Collectors.toMap(FeeDto.Due::id, Function.identity()));
		BigDecimal total = BigDecimal.ZERO;
		for (Map.Entry<Long, BigDecimal> e : wanted.entrySet()) {
			StudentFeeDue due = byId.get(e.getKey());
			if (e.getValue().compareTo(due.getBalance()) > 0) {
				FeeDto.Due v = dueViews.get(due.getId());
				throw new ConflictException("₹" + e.getValue() + " is more than the ₹" + due.getBalance() + " still due for "
						+ v.feeHeadName() + " – " + v.installmentName());
			}
			total = total.add(e.getValue());
		}
		return new Checked(byId, enrollment, total);
	}

	/** Cancels a receipt: the dues it paid become payable again. The receipt and its number stay. */
	@Transactional
	public FeeDto.Receipt cancel(Long id, FeeDto.CancelRequest request) {
		FeeReceipt receipt = require(id);
		if (receipt.isCancelled()) {
			throw new ConflictException("Receipt " + receipt.getReceiptNo() + " is already cancelled");
		}
		List<FeeReceiptLine> receiptLines = lines.findByReceiptIdIn(List.of(receipt.getId()));
		Map<Long, StudentFeeDue> byId = dues
			.lockBySchoolIdAndIdIn(TenantContext.require(), receiptLines.stream().map(FeeReceiptLine::getStudentFeeDueId).toList())
			.stream()
			.collect(Collectors.toMap(StudentFeeDue::getId, Function.identity()));
		for (FeeReceiptLine line : receiptLines) {
			StudentFeeDue due = byId.get(line.getStudentFeeDueId());
			due.setPaidAmount(due.getPaidAmount().subtract(line.getAmount()));
		}
		receipt.setCancelled(true);
		receipt.setCancelledBy(CurrentUser.get().userId());
		receipt.setCancelledAt(OffsetDateTime.now(clock));
		receipt.setCancelReason(request.reason().trim());
		audit.log(AuditAction.CANCEL, "fee_receipt", receipt.getId(),
				Map.of("receiptNo", receipt.getReceiptNo(), "reason", receipt.getCancelReason()));
		return receipt(receipt.getId());
	}

	@Transactional(readOnly = true)
	public FeeDto.Receipt receipt(Long id) {
		FeeReceipt r = require(id);
		Enrollment e = enrollmentService.require(r.getEnrollmentId());
		Student s = students.findByIdAndSchoolId(e.getStudentId(), TenantContext.require()).orElseThrow();
		List<FeeReceiptLine> receiptLines = lines.findByReceiptIdIn(List.of(r.getId()));
		Map<Long, FeeDto.Due> dueViews = views
			.dues(dues.findBySchoolIdAndIdIn(TenantContext.require(),
					receiptLines.stream().map(FeeReceiptLine::getStudentFeeDueId).toList()))
			.stream()
			.collect(Collectors.toMap(FeeDto.Due::id, Function.identity()));
		Map<Long, String> names = staff.fullNames(
				Stream.of(r.getCollectedBy(), r.getCancelledBy()).filter(Objects::nonNull).collect(Collectors.toSet()));
		SectionDto.Response section = sectionService.describe(List.of(e.getSectionId())).get(e.getSectionId());
		return new FeeDto.Receipt(r.getId(), r.getReceiptNo(), s.getId(), s.getFullName(), s.getAdmissionNo(),
				section.className() + " – " + section.name(), views.yearNames().get(r.getAcademicYearId()), r.getPaidOn(),
				r.getTotalAmount(), AmountInWords.rupees(r.getTotalAmount()), r.getPaymentMode(), r.getReferenceNo(),
				names.get(r.getCollectedBy()), r.getCreatedAt(), r.isCancelled(), r.getCancelReason(),
				names.get(r.getCancelledBy()), r.getCancelledAt(),
				receiptLines.stream().map(l -> {
					FeeDto.Due d = dueViews.get(l.getStudentFeeDueId());
					return new FeeDto.ReceiptLine(d.id(), d.installmentName(), d.feeHeadName(), l.getAmount());
				}).toList());
	}

	@Transactional(readOnly = true)
	public PageResponse<FeeDto.ReceiptSummary> search(LocalDate from, LocalDate to, String q, Boolean cancelled, int page,
			int size) {
		LocalDate today = LocalDate.now(clock);
		String pattern = blank(q) ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
		var result = receipts.search(TenantContext.require(), from != null ? from : today.minusDays(30),
				to != null ? to : today, pattern, cancelled, PageRequest.of(page, size));
		List<FeeDto.ReceiptSummary> content = summaries(result.getContent());
		return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages());
	}

	FeeReceipt require(Long id) {
		return receipts.findByIdAndSchoolId(id, TenantContext.require()).orElseThrow(() -> new NotFoundException("Receipt"));
	}

	/** PREFIX/YY-YY/000123, with the school's receipt prefix and the academic year's start and end years. */
	private String receiptNo(AcademicYear year, long number) {
		String prefix = schoolService.requireCurrent().getSettings().receiptPrefix();
		String years = String.format("%02d-%02d", year.getStartDate().getYear() % 100, year.getEndDate().getYear() % 100);
		return String.format("%s/%s/%06d", prefix, years, number);
	}

	private List<FeeDto.ReceiptSummary> summaries(List<FeeReceipt> list) {
		if (list.isEmpty()) {
			return List.of();
		}
		Long schoolId = TenantContext.require();
		Map<Long, Enrollment> enrollments = new LinkedHashMap<>();
		for (Long eid : new HashSet<>(list.stream().map(FeeReceipt::getEnrollmentId).toList())) {
			enrollments.put(eid, enrollmentService.require(eid));
		}
		Map<Long, Student> studentById = students
			.findBySchoolIdAndIdIn(schoolId, enrollments.values().stream().map(Enrollment::getStudentId).toList())
			.stream()
			.collect(Collectors.toMap(Student::getId, Function.identity()));
		Map<Long, SectionDto.Response> sections = sectionService
			.describe(enrollments.values().stream().map(Enrollment::getSectionId).collect(Collectors.toSet()));
		return list.stream().map(r -> {
			Enrollment e = enrollments.get(r.getEnrollmentId());
			Student s = studentById.get(e.getStudentId());
			SectionDto.Response sec = sections.get(e.getSectionId());
			return new FeeDto.ReceiptSummary(r.getId(), r.getReceiptNo(), r.getPaidOn(), s.getId(), s.getFullName(),
					s.getAdmissionNo(), sec.className() + " – " + sec.name(), r.getTotalAmount(), r.getPaymentMode(),
					r.isCancelled());
		}).toList();
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

}
