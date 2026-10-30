package com.schoolerp.fee.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.schoolerp.auth.service.CurrentUser;
import com.schoolerp.common.error.ApiException;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.entity.OnlinePayment;
import com.schoolerp.fee.entity.PaymentMode;
import com.schoolerp.fee.repository.OnlinePaymentRepository;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.service.StudentService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Online payment at the counter: the server fixes the dues and amount in an
 * order, the payment window takes the money, and the receipt is created only
 * after the signature and Razorpay's own record of the payment check out. The
 * receipt is a normal one (gap-free number, mode Online, the Razorpay payment
 * id as reference).
 *
 * <p>Calls to Razorpay happen outside database transactions.
 */
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

	private final PaymentGateway gateway;

	private final OnlinePaymentRepository payments;

	private final FeeCollectionService collection;

	private final FeeMessageService messages;

	private final StudentService studentService;

	private final SchoolService schoolService;

	private final PlatformTransactionManager transactionManager;

	private final Clock clock;

	public FeeDto.OnlineConfig config() {
		return new FeeDto.OnlineConfig(gateway.isConfigured(), gateway.isConfigured() ? gateway.keyId() : null);
	}

	/** Checks the dues like a cash payment would, then creates the gateway order for exactly that amount. */
	public FeeDto.OnlineOrder start(FeeDto.OnlineOrderRequest request) {
		requireConfigured();
		BigDecimal total = collection.checkPayable(request.studentId(), request.lines());
		long paise = paise(total);
		School school = schoolService.requireCurrent();
		StudentDto.Contact contact = studentService.contacts(List.of(request.studentId())).get(request.studentId());
		PaymentGateway.Order order = gateway.createOrder(paise,
				"s" + school.getId() + "-" + request.studentId() + "-" + clock.millis(),
				Map.of("school", school.getCode(), "studentId", String.valueOf(request.studentId())));

		Map<String, String> lines = new LinkedHashMap<>();
		request.lines()
			.forEach(l -> lines.put(String.valueOf(l.dueId()), l.amount().setScale(2, RoundingMode.HALF_UP).toPlainString()));
		OnlinePayment p = new OnlinePayment();
		p.setStudentId(request.studentId());
		p.setGateway(gateway.name());
		p.setOrderId(order.id());
		p.setAmount(total);
		p.setLines(lines);
		p.setCreatedBy(CurrentUser.get().userId());
		payments.save(p);
		return new FeeDto.OnlineOrder(p.getId(), order.id(), paise, order.currency(), gateway.keyId(), school.getName(),
				contact == null ? null : contact.fullName(), "School fees", contact == null ? null : contact.phone());
	}

	/**
	 * After the payment window reports success: verifies it, then records the
	 * receipt. Calling it again for a paid order returns the same receipt.
	 */
	public FeeDto.Receipt complete(Long id, FeeDto.OnlineCompleteRequest request) {
		requireConfigured();
		Long schoolId = TenantContext.require();
		OnlinePayment p = payments.findByIdAndSchoolId(id, schoolId)
			.orElseThrow(() -> new NotFoundException("Online payment"));
		if (!p.getOrderId().equals(request.razorpayOrderId())) {
			throw new BadRequestException("The payment does not match this order");
		}
		if (p.getStatus() == OnlinePayment.Status.PAID) {
			return collection.receipt(p.getReceiptId());
		}
		if (p.getStatus() == OnlinePayment.Status.FAILED) {
			throw new ConflictException(p.getFailure());
		}
		String paymentId = request.razorpayPaymentId();
		if (!gateway.verifySignature(p.getOrderId(), paymentId, request.razorpaySignature())) {
			throw new BadRequestException("The payment could not be verified. No receipt was created.");
		}
		PaymentGateway.Confirmation confirmation = gateway.confirm(p.getOrderId(), paymentId, paise(p.getAmount()));
		if (!confirmation.paid()) {
			fail(id, paymentId, confirmation.reason());
			throw new ConflictException(confirmation.reason() + ". No receipt was created.");
		}

		Completed done;
		try {
			done = new TransactionTemplate(transactionManager).execute(status -> recordReceipt(id, schoolId, paymentId));
		}
		catch (ApiException ex) {
			String reason = "Paid online (Razorpay " + paymentId + ") but no receipt could be made: " + ex.getMessage();
			fail(id, paymentId, reason);
			throw new ConflictException(reason + " Refund it from the Razorpay dashboard.");
		}
		if (done.created()) {
			messages.confirmPayment(done.receiptId());
		}
		return collection.receipt(done.receiptId());
	}

	private record Completed(Long receiptId, boolean created) {
	}

	/** In one transaction with the payment row locked: the receipt, and the payment marked paid. */
	private Completed recordReceipt(Long id, Long schoolId, String paymentId) {
		OnlinePayment locked = payments.lockByIdAndSchoolId(id, schoolId).orElseThrow();
		if (locked.getStatus() == OnlinePayment.Status.PAID) {
			return new Completed(locked.getReceiptId(), false);
		}
		List<FeeDto.CollectLine> lines = locked.getLines()
			.entrySet()
			.stream()
			.map(e -> new FeeDto.CollectLine(Long.valueOf(e.getKey()), new BigDecimal(e.getValue())))
			.toList();
		FeeDto.Receipt receipt = collection.collect(new FeeDto.CollectRequest(locked.getStudentId(),
				LocalDate.now(clock), PaymentMode.ONLINE, paymentId, lines));
		locked.setStatus(OnlinePayment.Status.PAID);
		locked.setPaymentId(paymentId);
		locked.setReceiptId(receipt.id());
		locked.setCompletedAt(OffsetDateTime.now(clock));
		return new Completed(receipt.id(), true);
	}

	private void fail(Long id, String paymentId, String reason) {
		Long schoolId = TenantContext.require();
		new TransactionTemplate(transactionManager)
			.executeWithoutResult(status -> payments.lockByIdAndSchoolId(id, schoolId).ifPresent(p -> {
				if (p.getStatus() == OnlinePayment.Status.CREATED) {
					p.setStatus(OnlinePayment.Status.FAILED);
					p.setPaymentId(paymentId);
					p.setFailure(reason.length() <= 300 ? reason : reason.substring(0, 300));
					p.setCompletedAt(OffsetDateTime.now(clock));
				}
			}));
	}

	private void requireConfigured() {
		if (!gateway.isConfigured()) {
			throw new ConflictException("Online payment is not set up on this server");
		}
	}

	private static long paise(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
	}

}
