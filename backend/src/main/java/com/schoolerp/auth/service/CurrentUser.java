package com.schoolerp.auth.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the signed-in user for services (ownership checks, audit). */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static AuthUser get() {
		AuthUser user = getOrNull();
		if (user == null) {
			throw new IllegalStateException("No authenticated user");
		}
		return user;
	}

	public static Long idOrNull() {
		AuthUser user = getOrNull();
		return user == null ? null : user.userId();
	}

	private static AuthUser getOrNull() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getPrincipal() instanceof AuthUser user ? user : null;
	}

}
