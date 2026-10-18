package com.schoolerp.tenant;

import java.util.function.Supplier;

/** Supplies the tenant filter's {@code schoolId}; fails if no tenant is set. */
public class TenantIdResolver implements Supplier<Long> {

	@Override
	public Long get() {
		return TenantContext.require();
	}

}
