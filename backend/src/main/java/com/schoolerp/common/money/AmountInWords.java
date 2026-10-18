package com.schoolerp.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indian-style amount in words for receipts: lakh and crore grouping.
 * 1,23,456.50 becomes "Rupees One Lakh Twenty Three Thousand Four Hundred Fifty Six and Fifty Paise Only".
 */
public final class AmountInWords {

	private static final String[] ONES = { "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
			"Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen",
			"Nineteen" };

	private static final String[] TENS = { "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty",
			"Ninety" };

	private AmountInWords() {
	}

	public static String rupees(BigDecimal amount) {
		BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
		if (value.signum() < 0) {
			throw new IllegalArgumentException("Amount cannot be negative");
		}
		long rupees = value.longValue();
		int paise = value.remainder(BigDecimal.ONE).movePointRight(2).intValue();
		StringBuilder out = new StringBuilder("Rupees ").append(rupees == 0 ? "Zero" : words(rupees));
		if (paise > 0) {
			out.append(" and ").append(words(paise)).append(" Paise");
		}
		return out.append(" Only").toString();
	}

	private static String words(long n) {
		StringBuilder sb = new StringBuilder();
		long crore = n / 1_00_00_000;
		n %= 1_00_00_000;
		long lakh = n / 1_00_000;
		n %= 1_00_000;
		long thousand = n / 1000;
		n %= 1000;
		long hundred = n / 100;
		n %= 100;
		if (crore > 0) {
			sb.append(words(crore)).append(" Crore ");
		}
		if (lakh > 0) {
			sb.append(twoDigits((int) lakh)).append(" Lakh ");
		}
		if (thousand > 0) {
			sb.append(twoDigits((int) thousand)).append(" Thousand ");
		}
		if (hundred > 0) {
			sb.append(ONES[(int) hundred]).append(" Hundred ");
		}
		if (n > 0) {
			sb.append(twoDigits((int) n));
		}
		return sb.toString().trim();
	}

	private static String twoDigits(int n) {
		if (n < 20) {
			return ONES[n];
		}
		return (TENS[n / 10] + " " + ONES[n % 10]).trim();
	}

}
