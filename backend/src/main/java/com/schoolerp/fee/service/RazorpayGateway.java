package com.schoolerp.fee.service;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.schoolerp.common.error.ApiException;

/**
 * Razorpay Orders API (test or live keys; test keys start with {@code rzp_test_}).
 * Signature: HMAC-SHA256 of {@code orderId|paymentId} with the key secret.
 */
class RazorpayGateway implements PaymentGateway {

	private final String keyId;

	private final String keySecret;

	private final RestClient client;

	RazorpayGateway(String baseUrl, String keyId, String keySecret) {
		this.keyId = keyId;
		this.keySecret = keySecret;
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		requests.setReadTimeout(Duration.ofSeconds(20));
		this.client = RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(requests)
			.defaultHeaders(h -> h.setBasicAuth(keyId, keySecret, StandardCharsets.UTF_8))
			.build();
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
		return keyId;
	}

	@Override
	public Order createOrder(long amountPaise, String receipt, Map<String, String> notes) {
		Map<?, ?> order = call(() -> client.post()
			.uri("/orders")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes))
			.retrieve()
			.body(Map.class));
		if (order == null || order.get("id") == null) {
			throw unavailable();
		}
		return new Order(String.valueOf(order.get("id")), amountPaise, "INR");
	}

	@Override
	public boolean verifySignature(String orderId, String paymentId, String signature) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String expected = HexFormat.of()
				.formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
			return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
					signature.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HMAC-SHA256 is not available", ex);
		}
	}

	@Override
	public Confirmation confirm(String orderId, String paymentId, long amountPaise) {
		Map<?, ?> payment = fetch(paymentId);
		if (!orderId.equals(String.valueOf(payment.get("order_id")))) {
			return Confirmation.refused("The payment does not belong to this order");
		}
		if (!(payment.get("amount") instanceof Number amount) || amount.longValue() != amountPaise) {
			return Confirmation.refused("The amount paid does not match the order");
		}
		String status = String.valueOf(payment.get("status"));
		if ("authorized".equals(status)) {
			try {
				client.post()
					.uri("/payments/{id}/capture", paymentId)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("amount", amountPaise, "currency", "INR"))
					.retrieve()
					.toBodilessEntity();
			}
			catch (RestClientException ex) {
				// Another request may have captured it meanwhile; the status decides.
			}
			status = String.valueOf(fetch(paymentId).get("status"));
		}
		return "captured".equals(status) ? Confirmation.ok()
				: Confirmation.refused("Razorpay reports the payment as " + status);
	}

	private Map<?, ?> fetch(String paymentId) {
		Map<?, ?> payment = call(() -> client.get().uri("/payments/{id}", paymentId).retrieve().body(Map.class));
		if (payment == null) {
			throw unavailable();
		}
		return payment;
	}

	private static <T> T call(Supplier<T> request) {
		try {
			return request.get();
		}
		catch (RestClientResponseException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Razorpay refused the request (" + ex.getStatusCode().value()
					+ "). Check the Razorpay keys.");
		}
		catch (RestClientException ex) {
			throw unavailable();
		}
	}

	private static ApiException unavailable() {
		return new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach Razorpay. Try again in a minute.");
	}

}
