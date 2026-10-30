package com.schoolerp.notification.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.schoolerp.notification.entity.MessageType;
import com.schoolerp.notification.entity.NotificationLog;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class NotificationDto {

	private NotificationDto() {
	}

	/** Whether messages really go out, or (no WhatsApp keys configured) are only written to the server log. */
	public record Status(boolean connected) {
	}

	/** The students to message, chosen on screen. */
	public record SendRequest(@NotEmpty(message = "Choose at least one student") @Size(max = 1000)
			List<@NotNull Long> studentIds) {
	}

	public enum Result { SENT, FAILED, SKIPPED }

	/** What happened for one student; {@code detail} says why it failed or was skipped. */
	public record Outcome(Long studentId, String studentName, Result result, String detail) {
	}

	public record SendReport(MessageType type, boolean connected, int sent, int failed, int skipped,
			List<Outcome> outcomes) {
	}

	/** The latest message tried for a student about the same thing (an absence day, an exam). */
	public record History(Long studentId, NotificationLog.Status status, OffsetDateTime at) {
	}

	public record LogRow(Long id, OffsetDateTime createdAt, MessageType type, Long studentId, String studentName,
			String phone, NotificationLog.Status status, String error, String sentByName) {
	}

}
