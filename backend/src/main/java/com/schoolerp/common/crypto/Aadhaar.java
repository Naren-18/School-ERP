package com.schoolerp.common.crypto;

/** Aadhaar number checks: 12 digits, not starting with 0 or 1, valid Verhoeff check digit. */
public final class Aadhaar {

	private static final int[][] D = {
			{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, { 1, 2, 3, 4, 0, 6, 7, 8, 9, 5 }, { 2, 3, 4, 0, 1, 7, 8, 9, 5, 6 },
			{ 3, 4, 0, 1, 2, 8, 9, 5, 6, 7 }, { 4, 0, 1, 2, 3, 9, 5, 6, 7, 8 }, { 5, 9, 8, 7, 6, 0, 4, 3, 2, 1 },
			{ 6, 5, 9, 8, 7, 1, 0, 4, 3, 2 }, { 7, 6, 5, 9, 8, 2, 1, 0, 4, 3 }, { 8, 7, 6, 5, 9, 3, 2, 1, 0, 4 },
			{ 9, 8, 7, 6, 5, 4, 3, 2, 1, 0 } };

	private static final int[][] P = {
			{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, { 1, 5, 7, 6, 2, 8, 3, 0, 9, 4 }, { 5, 8, 0, 3, 7, 9, 6, 1, 4, 2 },
			{ 8, 9, 1, 6, 0, 4, 3, 5, 2, 7 }, { 9, 4, 5, 3, 1, 2, 6, 8, 7, 0 }, { 4, 2, 8, 6, 5, 7, 3, 9, 0, 1 },
			{ 2, 7, 9, 3, 8, 0, 6, 4, 1, 5 }, { 7, 0, 4, 6, 9, 1, 3, 2, 5, 8 } };

	private static final int[] INV = { 0, 4, 3, 2, 1, 5, 6, 7, 8, 9 };

	private Aadhaar() {
	}

	/** Strips spaces and dashes: "2345 6789 0124" becomes "234567890124". */
	public static String normalize(String input) {
		return input == null ? null : input.replaceAll("[\\s-]", "");
	}

	public static boolean isValid(String digits) {
		if (digits == null || !digits.matches("^[2-9]\\d{11}$")) {
			return false;
		}
		int c = 0;
		for (int i = 0; i < digits.length(); i++) {
			int digit = digits.charAt(digits.length() - 1 - i) - '0';
			c = D[c][P[i % 8][digit]];
		}
		return c == 0;
	}

	/** The check digit to append to 11 digits (used to generate fake demo data). */
	public static int checkDigit(String elevenDigits) {
		int c = 0;
		for (int i = 0; i < elevenDigits.length(); i++) {
			int digit = elevenDigits.charAt(elevenDigits.length() - 1 - i) - '0';
			c = D[c][P[(i + 1) % 8][digit]];
		}
		return INV[c];
	}

}
