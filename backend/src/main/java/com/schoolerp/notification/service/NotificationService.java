package com.schoolerp.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.auth.service.CurrentUser;
import com.schoolerp.common.dto.PageResponse;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.notification.dto.NotificationDto;
import com.schoolerp.notification.entity.MessageType;
import com.schoolerp.notification.entity.NotificationLog;
import com.schoolerp.notification.repository.NotificationLogRepository;
import com.schoolerp.notification.repository.NotificationSearchRepository;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.service.StudentService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * WhatsApp messages to parents. Callers (attendance, fees, exams) check what
 * the user may see and build the template values; this service decides who can
 * be messaged (consent, a mobile number, the school's switches), sends, and
 * keeps the log.
 *
 * <p>Not transactional on purpose: sending takes time, so no database
 * transaction is held open while waiting for WhatsApp. Only sending runs on
 * worker threads; everything that needs the school context stays on the
 * request thread.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	/** Messages sent at the same time; WhatsApp allows far more, this keeps a big batch to seconds. */
	private static final int PARALLEL = 8;

	private final WhatsAppSender sender;

	private final NotificationLogRepository logs;

	private final NotificationSearchRepository search;

	private final StudentService students;

	private final SchoolService schoolService;

	private final AuditService audit;

	private final Clock clock;

	/**
	 * One message about one student. {@code params} are the template's values in
	 * order; {@code refDate} / {@code refId} say what it is about (an absence
	 * day, a receipt, an exam), so the screen can show it was already sent.
	 */
	public record Message(Long studentId, LocalDate refDate, Long refId, List<String> params) {
	}

	public NotificationDto.Status status() {
		return new NotificationDto.Status(sender.isConnected());
	}

	public boolean isEnabled(MessageType type) {
		return type.isEnabled(schoolService.requireCurrent().getSettings().messages());
	}

	/** 409 when the school has switched this kind of message off. */
	public void requireEnabled(MessageType type) {
		if (!isEnabled(type)) {
			throw new ConflictException(type.label() + " are switched off. The principal can switch them on under "
					+ "Setup → Modules & preferences.");
		}
	}

	/**
	 * Messages chosen on screen (absences, fee reminders, results): sends to
	 * every student whose parent agreed and has a mobile number, and reports
	 * what happened to each. Audited as one entry.
	 */
	public NotificationDto.SendReport send(MessageType type, List<Message> messages) {
		requireEnabled(type);
		NotificationDto.SendReport report = deliver(type, messages);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("type", type.name());
		details.put("sent", report.sent());
		details.put("failed", report.failed());
		details.put("skipped", report.skipped());
		audit.log(AuditAction.NOTIFY, "notification", null, details);
		return report;
	}

	/**
	 * An automatic message (a payment confirmation). Does nothing when the school
	 * switched it off or the parent cannot be messaged, and never throws: the
	 * payment it follows is already saved.
	 */
	public void sendQuietly(MessageType type, Message message) {
		try {
			if (isEnabled(type)) {
				deliver(type, List.of(message));
			}
		}
		catch (RuntimeException ex) {
			log.warn("Could not send {} for student {}", type, message.studentId(), ex);
		}
	}

	/** The latest message tried per student for an absence day (by date) or a receipt or exam (by id). */
	public Map<Long, NotificationDto.History> history(MessageType type, LocalDate refDate, Long refId,
			Collection<Long> studentIds) {
		if (studentIds.isEmpty()) {
			return Map.of();
		}
		Long schoolId = TenantContext.require();
		List<NotificationLog> found = refDate != null
				? logs.findBySchoolIdAndTypeAndRefDateAndStudentIdInOrderByIdDesc(schoolId, type, refDate, studentIds)
				: logs.findBySchoolIdAndTypeAndRefIdAndStudentIdInOrderByIdDesc(schoolId, type, refId, studentIds);
		Map<Long, NotificationDto.History> latest = new HashMap<>();
		for (NotificationLog n : found) {
			latest.putIfAbsent(n.getStudentId(),
					new NotificationDto.History(n.getStudentId(), n.getStatus(), n.getCreatedAt()));
		}
		return latest;
	}

	/** The message log, newest first; dates are school days in India time. */
	public PageResponse<NotificationDto.LogRow> search(MessageType type, NotificationLog.Status status,
			LocalDate from, LocalDate to, String q, int page, int size) {
		ZoneId zone = clock.getZone();
		LocalDate today = LocalDate.now(clock);
		LocalDate start = from != null ? from : today.minusDays(30);
		LocalDate end = to != null ? to : today;
		NotificationSearchRepository.Result result = search.search(new NotificationSearchRepository.Criteria(
				TenantContext.require(), type, status, start.atStartOfDay(zone).toOffsetDateTime(),
				end.plusDays(1).atStartOfDay(zone).toOffsetDateTime(), q, page, size));
		return new PageResponse<>(result.rows(), page, size, result.total(),
				(int) Math.ceil(result.total() / (double) size));
	}

	private NotificationDto.SendReport deliver(MessageType type, List<Message> messages) {
		Map<Long, StudentDto.Contact> contacts = students
			.contacts(messages.stream().map(Message::studentId).distinct().toList());
		List<NotificationDto.Outcome> outcomes = new ArrayList<>();
		List<Message> sendable = new ArrayList<>();
		for (Message m : messages) {
			StudentDto.Contact c = contacts.get(m.studentId());
			if (c == null) {
				outcomes.add(new NotificationDto.Outcome(m.studentId(), null, NotificationDto.Result.SKIPPED,
						"Student not found"));
			}
			else if (!c.whatsappConsent()) {
				outcomes.add(new NotificationDto.Outcome(c.studentId(), c.fullName(), NotificationDto.Result.SKIPPED,
						"Parent has not agreed to WhatsApp messages"));
			}
			else if (c.phone() == null) {
				outcomes.add(new NotificationDto.Outcome(c.studentId(), c.fullName(), NotificationDto.Result.SKIPPED,
						"No mobile number"));
			}
			else {
				sendable.add(m);
			}
		}

		List<WhatsAppSender.SendResult> results = sendAll(type.template(), sendable, contacts);
		Long userId = CurrentUser.idOrNull();
		List<NotificationLog> entries = new ArrayList<>();
		for (int i = 0; i < sendable.size(); i++) {
			Message m = sendable.get(i);
			StudentDto.Contact c = contacts.get(m.studentId());
			WhatsAppSender.SendResult r = results.get(i);
			NotificationLog entry = new NotificationLog();
			entry.setType(type);
			entry.setStudentId(m.studentId());
			entry.setPhone(c.phone());
			entry.setRefDate(m.refDate());
			entry.setRefId(m.refId());
			entry.setStatus(r.sent() ? NotificationLog.Status.SENT : NotificationLog.Status.FAILED);
			entry.setError(truncate(r.error()));
			entry.setProviderMessageId(r.messageId());
			entry.setSentBy(userId);
			entries.add(entry);
			outcomes.add(new NotificationDto.Outcome(c.studentId(), c.fullName(),
					r.sent() ? NotificationDto.Result.SENT : NotificationDto.Result.FAILED, r.error()));
		}
		logs.saveAll(entries);

		int sent = (int) outcomes.stream().filter(o -> o.result() == NotificationDto.Result.SENT).count();
		int failed = (int) outcomes.stream().filter(o -> o.result() == NotificationDto.Result.FAILED).count();
		return new NotificationDto.SendReport(type, sender.isConnected(), sent, failed, outcomes.size() - sent - failed,
				outcomes);
	}

	/** Sends in parallel; results come back in the same order as {@code sendable}. */
	private List<WhatsAppSender.SendResult> sendAll(String template, List<Message> sendable,
			Map<Long, StudentDto.Contact> contacts) {
		if (sendable.isEmpty()) {
			return List.of();
		}
		try (ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLEL, sendable.size()))) {
			List<Future<WhatsAppSender.SendResult>> futures = sendable.stream()
				.map(m -> pool.submit(() -> sendOne(template, contacts.get(m.studentId()).phone(), m.params())))
				.toList();
			List<WhatsAppSender.SendResult> results = new ArrayList<>();
			for (Future<WhatsAppSender.SendResult> f : futures) {
				results.add(await(f));
			}
			return results;
		}
	}

	private WhatsAppSender.SendResult sendOne(String template, String phone, List<String> params) {
		try {
			return sender.send(phone, template, params);
		}
		catch (RuntimeException ex) {
			log.warn("WhatsApp send failed", ex);
			return WhatsAppSender.SendResult.failed("Could not send the message");
		}
	}

	private static WhatsAppSender.SendResult await(Future<WhatsAppSender.SendResult> f) {
		try {
			return f.get();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return WhatsAppSender.SendResult.failed("Sending was interrupted");
		}
		catch (ExecutionException ex) {
			return WhatsAppSender.SendResult.failed("Could not send the message");
		}
	}

	private static String truncate(String s) {
		return s == null || s.length() <= 300 ? s : s.substring(0, 300);
	}

}
