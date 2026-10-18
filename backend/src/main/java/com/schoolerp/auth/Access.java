package com.schoolerp.auth;

/** {@code @PreAuthorize} expressions shared by controllers (see the roles table in CLAUDE.md). */
public final class Access {

	/** Any signed-in staff member. */
	public static final String ANY_STAFF = "isAuthenticated()";

	/** School settings, users and roles. */
	public static final String PRINCIPAL = "hasRole('PRINCIPAL')";

	/** Everything else administrative (academic setup, students, ...). */
	public static final String PRINCIPAL_OR_ADMIN = "hasAnyRole('PRINCIPAL','ADMIN')";

	/** Fee setup, collection, concessions and reports. */
	public static final String FEE_STAFF = "hasAnyRole('PRINCIPAL','ADMIN','ACCOUNTANT')";

	/** Cancelling a receipt (PLAN.md: principal or accountant, with a reason). */
	public static final String FEE_CANCEL = "hasAnyRole('PRINCIPAL','ACCOUNTANT')";

	/** Attendance and marks: principal, admin, and teachers (services limit teachers to their sections). */
	public static final String TEACHING_STAFF = "hasAnyRole('PRINCIPAL','ADMIN','TEACHER')";

	private Access() {
	}

}
