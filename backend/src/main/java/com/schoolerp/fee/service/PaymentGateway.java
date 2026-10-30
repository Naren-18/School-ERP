package com.schoolerp.fee.service;

import java.util.Map;

/**
 * An online payment provider (Razorpay). The server creates an order for an
 * amount it worked out itself, the browser opens the provider's payment window
 * for that order, and the server checks the result before any receipt exists.
 */
public interface PaymentGateway {

	/** False when no keys are configured: online payment is not offered. */
	boolean isConfigured();

	/** Stored with each payment, e.g. RAZORPAY. */
	String name();

	/** The public key the payment window needs. */
	String keyId();

	/** @param amountPaise the amount in paise (₹1 = 100) */
	Order createOrder(long amountPaise, String receipt, Map<String, String> notes);

	/** The payment window's signature proves the payment belongs to this order. */
	boolean verifySignature(String orderId, String paymentId, String signature);

	/**
	 * Confirms with the provider that the payment is for this order and amount,
	 * and captures it if it was only authorised.
	 */
	Confirmation confirm(String orderId, String paymentId, long amountPaise);

	record Order(String id, long amountPaise, String currency) {
	}

	record Confirmation(boolean paid, String reason) {

		public static Confirmation ok() {
			return new Confirmation(true, null);
		}

		public static Confirmation refused(String reason) {
			return new Confirmation(false, reason);
		}

	}

}
