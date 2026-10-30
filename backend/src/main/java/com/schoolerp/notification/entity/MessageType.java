package com.schoolerp.notification.entity;

import com.schoolerp.school.entity.SchoolSettings;

/**
 * The WhatsApp messages the app sends to parents. Each is a template approved
 * by Meta under the name below (category Utility, language English); the body
 * text and placeholder order are listed in README.md.
 */
public enum MessageType {

	/** {{1}} student, {{2}} class, {{3}} school, {{4}} date. */
	ABSENCE("absence_alert", "Absence alerts"),

	/** {{1}} amount, {{2}} student, {{3}} class, {{4}} school. */
	FEE_REMINDER("fee_reminder", "Fee reminders"),

	/** {{1}} amount, {{2}} student, {{3}} class, {{4}} receipt no., {{5}} date, {{6}} school. */
	PAYMENT_RECEIVED("payment_received", "Payment confirmations"),

	/** {{1}} exam, {{2}} student, {{3}} class, {{4}} school. */
	RESULTS_PUBLISHED("results_published", "Results messages");

	private final String template;

	private final String label;

	MessageType(String template, String label) {
		this.template = template;
		this.label = label;
	}

	/** The Meta template name. */
	public String template() {
		return template;
	}

	public String label() {
		return label;
	}

	public boolean isEnabled(SchoolSettings.Messages messages) {
		return switch (this) {
			case ABSENCE -> messages.absence();
			case FEE_REMINDER -> messages.feeReminder();
			case PAYMENT_RECEIVED -> messages.paymentReceived();
			case RESULTS_PUBLISHED -> messages.resultsPublished();
		};
	}

}
