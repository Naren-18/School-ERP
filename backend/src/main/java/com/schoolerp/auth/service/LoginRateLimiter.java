package com.schoolerp.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory limit on failed logins per school code + username: after
 * {@value #MAX_FAILURES} failures within 15 minutes, further attempts are refused
 * until the oldest failure ages out. Single-instance only, which is fine for the MVP.
 */
@Component
public class LoginRateLimiter {

	static final int MAX_FAILURES = 10;

	static final Duration WINDOW = Duration.ofMinutes(15);

	private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

	private final Clock clock;

	public LoginRateLimiter(Clock clock) {
		this.clock = clock;
	}

	public boolean isBlocked(String schoolCode, String username) {
		Deque<Instant> attempts = failures.get(key(schoolCode, username));
		if (attempts == null) {
			return false;
		}
		synchronized (attempts) {
			prune(attempts);
			return attempts.size() >= MAX_FAILURES;
		}
	}

	public void recordFailure(String schoolCode, String username) {
		Deque<Instant> attempts = failures.computeIfAbsent(key(schoolCode, username), k -> new ArrayDeque<>());
		synchronized (attempts) {
			prune(attempts);
			attempts.addLast(clock.instant());
		}
	}

	public void reset(String schoolCode, String username) {
		failures.remove(key(schoolCode, username));
	}

	private void prune(Deque<Instant> attempts) {
		Instant cutoff = clock.instant().minus(WINDOW);
		while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
			attempts.removeFirst();
		}
	}

	private static String key(String schoolCode, String username) {
		return (schoolCode.trim() + "|" + username.trim()).toLowerCase(Locale.ROOT);
	}

}
