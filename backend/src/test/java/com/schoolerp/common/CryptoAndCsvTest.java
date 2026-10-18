package com.schoolerp.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.schoolerp.common.crypto.Aadhaar;
import com.schoolerp.common.crypto.FieldCipher;
import com.schoolerp.common.csv.Csv;

class CryptoAndCsvTest {

	private static final String KEY = Base64.getEncoder()
		.encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

	@Test
	void aadhaarVerhoeff() {
		String base = "23456789012";
		String valid = base + Aadhaar.checkDigit(base);
		assertThat(Aadhaar.isValid(valid)).isTrue();
		String wrong = base + ((Aadhaar.checkDigit(base) + 1) % 10);
		assertThat(Aadhaar.isValid(wrong)).isFalse();
		assertThat(Aadhaar.isValid("123456789012")).as("cannot start with 1").isFalse();
		assertThat(Aadhaar.isValid("2345678901")).isFalse();
		assertThat(Aadhaar.normalize("2345 6789-0124")).isEqualTo("234567890124");
	}

	@Test
	void cipherRoundTripsWithRandomIv() {
		FieldCipher cipher = new FieldCipher(KEY);
		byte[] a = cipher.encrypt("234567890124");
		byte[] b = cipher.encrypt("234567890124");
		assertThat(a).isNotEqualTo(b);
		assertThat(new String(a, StandardCharsets.ISO_8859_1)).doesNotContain("234567890124");
		assertThat(cipher.decrypt(a)).isEqualTo("234567890124");

		a[a.length - 1] ^= 1;
		assertThatThrownBy(() -> cipher.decrypt(a)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new FieldCipher("c2hvcnQ=")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void csvParsesQuotesBomAndLineEndings() {
		String text = "﻿name,address\r\n\"Reddy, Aarav\",\"Plot 5, \"\"Green\"\" Lane\"\r\n\r\nSita,\nRam,Hyd";
		List<List<String>> rows = Csv.parse(text);
		assertThat(rows).containsExactly(List.of("name", "address"), List.of("Reddy, Aarav", "Plot 5, \"Green\" Lane"),
				List.of("Sita", ""), List.of("Ram", "Hyd"));
		assertThat(Csv.parse(Csv.write(rows))).isEqualTo(rows);
	}

}
