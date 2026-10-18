package com.schoolerp.tenant;

import java.util.function.Supplier;

/**
 * The school the current thread is working for.
 *
 * <p>For HTTP requests it is set only by the JWT filter from a validated token and
 * cleared in a {@code finally}. {@link #runAs} exists for trusted server-side code
 * (seeding, onboarding, login bookkeeping after credentials are verified); never call
 * it with a school id taken from request input.
 */
public final class TenantContext {

	private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void set(Long schoolId) {
		if (schoolId == null) {
			throw new IllegalArgumentException("schoolId is required");
		}
		CURRENT.set(schoolId);
	}

	public static Long get() {
		return CURRENT.get();
	}

	public static Long require() {
		Long schoolId = CURRENT.get();
		if (schoolId == null) {
			throw new IllegalStateException("No tenant in context");
		}
		return schoolId;
	}

	public static void clear() {
		CURRENT.remove();
	}

	public static <T> T runAs(Long schoolId, Supplier<T> work) {
		Long previous = CURRENT.get();
		set(schoolId);
		try {
			return work.get();
		}
		finally {
			if (previous == null) {
				CURRENT.remove();
			}
			else {
				CURRENT.set(previous);
			}
		}
	}

	public static void runAs(Long schoolId, Runnable work) {
		runAs(schoolId, () -> {
			work.run();
			return null;
		});
	}

}
