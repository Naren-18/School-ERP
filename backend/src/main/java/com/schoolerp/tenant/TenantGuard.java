package com.schoolerp.tenant;

import java.util.Objects;
import java.util.Optional;

import com.schoolerp.common.entity.TenantEntity;
import com.schoolerp.common.error.NotFoundException;

/** Throws 404 unless the entity exists and belongs to the current school. */
public final class TenantGuard {

	private TenantGuard() {
	}

	public static <T extends TenantEntity> T require(T entity, String what) {
		if (entity == null || !Objects.equals(entity.getSchoolId(), TenantContext.require())) {
			throw new NotFoundException(what);
		}
		return entity;
	}

	public static <T extends TenantEntity> T require(Optional<T> entity, String what) {
		return require(entity.orElse(null), what);
	}

}
