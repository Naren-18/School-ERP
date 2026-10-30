package com.schoolerp.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indian digit grouping, 1,23,456.00. Java's {@code NumberFormat} for en-IN
 * groups in threes (123,456.00), so this is done by hand.
 */
public final class Rupees {

	private Rupees() {
	}

	/** 1,23,456.00 (no symbol). */
	public static String format(BigDecimal amount) {
		BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
		String plain = value.abs().toPlainString();
		int dot = plain.indexOf('.');
		String whole = plain.substring(0, dot);
		StringBuilder out = new StringBuilder();
		if (whole.length() <= 3) {
			out.append(whole);
		}
		else {
			// The last three digits form one group; the rest go in pairs.
			String head = whole.substring(0, whole.length() - 3);
			int first = head.length() % 2;
			out.append(head, 0, first);
			for (int i = first; i < head.length(); i += 2) {
				if (!out.isEmpty()) {
					out.append(',');
				}
				out.append(head, i, i + 2);
			}
			out.append(',').append(whole.substring(whole.length() - 3));
		}
		return (value.signum() < 0 ? "-" : "") + out + plain.substring(dot);
	}

	/** ₹1,23,456.00 */
	public static String withSymbol(BigDecimal amount) {
		String formatted = format(amount);
		return formatted.startsWith("-") ? "-₹" + formatted.substring(1) : "₹" + formatted;
	}

}
