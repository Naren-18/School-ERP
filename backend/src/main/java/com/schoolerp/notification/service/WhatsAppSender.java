package com.schoolerp.notification.service;

import java.util.List;

/**
 * Sends one WhatsApp template message. Meta's Cloud API when keys are
 * configured, otherwise a stand-in that only writes to the server log.
 * Implementations never touch the database, so they can run on worker threads.
 */
public interface WhatsAppSender {

	/** True when messages really reach phones. */
	boolean isConnected();

	/**
	 * @param phone 10-digit Indian mobile number
	 * @param template approved template name
	 * @param params the template's {{1}}, {{2}}, ... in order
	 */
	SendResult send(String phone, String template, List<String> params);

	record SendResult(boolean sent, String messageId, String error) {

		public static SendResult ok(String messageId) {
			return new SendResult(true, messageId, null);
		}

		public static SendResult failed(String error) {
			return new SendResult(false, null, error);
		}

	}

}
