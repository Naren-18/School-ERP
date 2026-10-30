package com.schoolerp.support;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.schoolerp.fee.service.PaymentGateway;
import com.schoolerp.notification.service.WhatsAppSender;

/**
 * Stand-ins for WhatsApp and Razorpay, so tests never call the internet. They
 * replace the real beans (which would be the log-only sender and "not set up").
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeIntegrations {

	@Bean
	@Primary
	public FakeWhatsApp fakeWhatsApp() {
		return new FakeWhatsApp();
	}

	@Bean
	@Primary
	public FakePaymentGateway fakePaymentGateway() {
		return new FakePaymentGateway();
	}

	/** Records every message; numbers in {@link #failFor} fail like an unknown WhatsApp number. */
	public static class FakeWhatsApp implements WhatsAppSender {

		public record Sent(String phone, String template, List<String> params) {
		}

		private final List<Sent> sent = new CopyOnWriteArrayList<>();

		private final Set<String> failFor = ConcurrentHashMap.newKeySet();

		@Override
		public boolean isConnected() {
			return true;
		}

		@Override
		public SendResult send(String phone, String template, List<String> params) {
			if (failFor.contains(phone)) {
				return SendResult.failed("WhatsApp: recipient is not a WhatsApp user");
			}
			sent.add(new Sent(phone, template, List.copyOf(params)));
			return SendResult.ok("wamid.fake." + sent.size());
		}

		/** Messages sent to this number, oldest first (tests share one context, so filter by a unique number). */
		public List<Sent> to(String phone) {
			return sent.stream().filter(s -> s.phone().equals(phone)).toList();
		}

		public void failFor(String phone) {
			failFor.add(phone);
		}

	}

	/**
	 * Orders are {@code order_fake_N}; a valid signature is
	 * {@link #signature(String, String)}; payments in {@link #refuse} are
	 * reported as failed by the "provider".
	 */
	public static class FakePaymentGateway implements PaymentGateway {

		private final AtomicInteger orders = new AtomicInteger();

		private final Map<String, Long> amounts = new ConcurrentHashMap<>();

		private final Set<String> refused = ConcurrentHashMap.newKeySet();

		public static String signature(String orderId, String paymentId) {
			return "sig:" + orderId + "|" + paymentId;
		}

		public void refuse(String paymentId) {
			refused.add(paymentId);
		}

		/** The amount (paise) an order was created for. */
		public Long amountOf(String orderId) {
			return amounts.get(orderId);
		}

		@Override
		public boolean isConfigured() {
			return true;
		}

		@Override
		public String name() {
			return "RAZORPAY";
		}

		@Override
		public String keyId() {
			return "rzp_test_fake";
		}

		@Override
		public Order createOrder(long amountPaise, String receipt, Map<String, String> notes) {
			String id = "order_fake_" + orders.incrementAndGet();
			amounts.put(id, amountPaise);
			return new Order(id, amountPaise, "INR");
		}

		@Override
		public boolean verifySignature(String orderId, String paymentId, String signature) {
			return signature(orderId, paymentId).equals(signature);
		}

		@Override
		public Confirmation confirm(String orderId, String paymentId, long amountPaise) {
			if (refused.contains(paymentId)) {
				return Confirmation.refused("Razorpay reports the payment as failed");
			}
			return amounts.containsKey(orderId) && amounts.get(orderId) == amountPaise ? Confirmation.ok()
					: Confirmation.refused("The amount paid does not match the order");
		}

	}

}
