package com.schoolerp.fee.entity;

public enum PaymentMode {

	CASH("Cash"), UPI("UPI"), CHEQUE("Cheque"), CARD("Card"), BANK("Bank transfer"), ONLINE("Online");

	private final String label;

	PaymentMode(String label) {
		this.label = label;
	}

	/** How the mode is printed on receipts. */
	public String label() {
		return label;
	}

}
