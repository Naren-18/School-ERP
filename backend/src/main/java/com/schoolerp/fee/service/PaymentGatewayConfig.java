package com.schoolerp.fee.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.schoolerp.common.error.ConflictException;

/** Razorpay when both keys are set (environment variables, see .env.example); otherwise online payment is off. */
@Configuration(proxyBeanMethods = false)
class PaymentGatewayConfig {

	@Bean
	PaymentGateway paymentGateway(@Value("${app.razorpay.key-id:}") String keyId,
			@Value("${app.razorpay.key-secret:}") String keySecret,
			@Value("${app.razorpay.base-url:https://api.razorpay.com/v1}") String baseUrl) {
		if (keyId.isBlank() || keySecret.isBlank()) {
			return new NotConfigured();
		}
		return new RazorpayGateway(baseUrl, keyId.trim(), keySecret.trim());
	}

	/** No keys: the collect screen hides online payment, and the API refuses it. */
	static final class NotConfigured implements PaymentGateway {

		@Override
		public boolean isConfigured() {
			return false;
		}

		@Override
		public String name() {
			return "NONE";
		}

		@Override
		public String keyId() {
			return null;
		}

		@Override
		public Order createOrder(long amountPaise, String receipt, Map<String, String> notes) {
			throw notSetUp();
		}

		@Override
		public boolean verifySignature(String orderId, String paymentId, String signature) {
			throw notSetUp();
		}

		@Override
		public Confirmation confirm(String orderId, String paymentId, long amountPaise) {
			throw notSetUp();
		}

		private static ConflictException notSetUp() {
			return new ConflictException("Online payment is not set up on this server");
		}

	}

}
