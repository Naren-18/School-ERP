package com.schoolerp.notification.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no WhatsApp keys are configured (development, a demo without Meta): writes the message to the log. */
class LogOnlyWhatsAppSender implements WhatsAppSender {

	private static final Logger log = LoggerFactory.getLogger(LogOnlyWhatsAppSender.class);

	@Override
	public boolean isConnected() {
		return false;
	}

	@Override
	public SendResult send(String phone, String template, List<String> params) {
		log.info("WhatsApp not connected; would send {} to {}: {}", template, phone, params);
		return SendResult.ok(null);
	}

}
