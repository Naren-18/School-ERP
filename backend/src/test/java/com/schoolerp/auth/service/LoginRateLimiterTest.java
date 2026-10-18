package com.schoolerp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

	private Instant now = Instant.parse("2026-06-01T10:00:00Z");

	private final Clock clock = new Clock() {
		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	};

	private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

	@Test
	void blocksAfterTenFailuresAndUnblocksAfterWindow() {
		for (int i = 0; i < 9; i++) {
			limiter.recordFailure("SVS", "principal");
		}
		assertThat(limiter.isBlocked("svs", "PRINCIPAL")).isFalse();
		limiter.recordFailure("svs", "principal");
		assertThat(limiter.isBlocked("svs", "principal")).isTrue();
		assertThat(limiter.isBlocked("other", "principal")).isFalse();

		now = now.plus(Duration.ofMinutes(15));
		assertThat(limiter.isBlocked("svs", "principal")).isFalse();
	}

	@Test
	void resetClearsFailures() {
		for (int i = 0; i < 10; i++) {
			limiter.recordFailure("svs", "principal");
		}
		limiter.reset("svs", "principal");
		assertThat(limiter.isBlocked("svs", "principal")).isFalse();
	}

}
