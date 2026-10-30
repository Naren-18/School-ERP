package com.schoolerp.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.schoolerp.common.money.Rupees;

class RupeesTest {

	private static String f(String amount) {
		return Rupees.format(new BigDecimal(amount));
	}

	@Test
	void groupsTheIndianWay() {
		assertThat(f("0")).isEqualTo("0.00");
		assertThat(f("999.5")).isEqualTo("999.50");
		assertThat(f("1000")).isEqualTo("1,000.00");
		assertThat(f("45000")).isEqualTo("45,000.00");
		assertThat(f("123456")).isEqualTo("1,23,456.00");
		assertThat(f("1234567.505")).isEqualTo("12,34,567.51");
		assertThat(f("123456789")).isEqualTo("12,34,56,789.00");
		assertThat(f("-123456")).isEqualTo("-1,23,456.00");
		assertThat(Rupees.withSymbol(new BigDecimal("123456"))).isEqualTo("₹1,23,456.00");
	}

}
