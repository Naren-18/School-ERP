package com.schoolerp.fee.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.money.Rupees;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.notification.dto.NotificationDto;
import com.schoolerp.notification.entity.MessageType;
import com.schoolerp.notification.service.NotificationService;
import com.schoolerp.school.service.SchoolService;

import lombok.RequiredArgsConstructor;

/** WhatsApp messages about fees: reminders chosen on the dues report, and a confirmation for every receipt. */
@Service
@RequiredArgsConstructor
public class FeeMessageService {

	private static final Logger log = LoggerFactory.getLogger(FeeMessageService.class);

	private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private final FeeReportService reports;

	private final FeeCollectionService collection;

	private final NotificationService notifications;

	private final SchoolService schoolService;

	/** Reminds the parents of the chosen students of their outstanding balance; each must have something due. */
	public NotificationDto.SendReport remind(FeeDto.ReminderRequest request) {
		notifications.requireEnabled(MessageType.FEE_REMINDER);
		Map<Long, FeeDto.DueRow> due = reports.dues(request.academicYearId(), null, null, false)
			.stream()
			.collect(Collectors.toMap(FeeDto.DueRow::studentId, Function.identity(), (a, b) -> a));
		if (!due.keySet().containsAll(Set.copyOf(request.studentIds()))) {
			throw new BadRequestException("Only students with fees due can be reminded. Reload the report and try again.");
		}
		String school = schoolService.requireCurrent().getName();
		List<NotificationService.Message> messages = request.studentIds().stream().distinct().map(id -> {
			FeeDto.DueRow r = due.get(id);
			return new NotificationService.Message(id, null, null,
					List.of(Rupees.format(r.balance()), r.fullName(), r.classLabel(), school));
		}).toList();
		return notifications.send(MessageType.FEE_REMINDER, messages);
	}

	/**
	 * Tells the parent a payment was received. Called after the receipt is saved
	 * (never inside its transaction); does nothing when the school switched it
	 * off or the parent cannot be messaged, and never throws.
	 */
	public void confirmPayment(Long receiptId) {
		try {
			if (!notifications.isEnabled(MessageType.PAYMENT_RECEIVED)) {
				return;
			}
			FeeDto.Receipt r = collection.receipt(receiptId);
			notifications.sendQuietly(MessageType.PAYMENT_RECEIVED,
					new NotificationService.Message(r.studentId(), null, r.id(),
							List.of(Rupees.format(r.totalAmount()), r.studentName(), r.classLabel(), r.receiptNo(),
									r.paidOn().format(DMY), schoolService.requireCurrent().getName())));
		}
		catch (RuntimeException ex) {
			log.warn("Could not send the payment confirmation for receipt {}", receiptId, ex);
		}
	}

}
