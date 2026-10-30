package com.schoolerp.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Meta's Cloud API when both keys are set (environment variables, see .env.example); otherwise log only. */
@Configuration(proxyBeanMethods = false)
class WhatsAppConfig {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppConfig.class);

	@Bean
	WhatsAppSender whatsAppSender(@Value("${app.whatsapp.access-token:}") String accessToken,
			@Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
			@Value("${app.whatsapp.api-version:v23.0}") String apiVersion,
			@Value("${app.whatsapp.base-url:https://graph.facebook.com}") String baseUrl) {
		if (accessToken.isBlank() || phoneNumberId.isBlank()) {
			log.info("WhatsApp keys not set: parent messages are written to the log only");
			return new LogOnlyWhatsAppSender();
		}
		return new CloudApiWhatsAppSender(baseUrl, apiVersion, phoneNumberId.trim(), accessToken.trim());
	}

}
