package com.schoolerp.common.entity;

import java.util.Objects;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import com.schoolerp.tenant.TenantContext;
import com.schoolerp.tenant.TenantIdResolver;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

/**
 * Base class for every row owned by a school.
 *
 * <p>The tenant filter is enabled automatically on every Hibernate session and
 * resolves {@code schoolId} from {@link TenantContext}; with no tenant in context,
 * any query on a tenant entity fails. It also applies to load-by-id, but code must
 * still use {@code findByIdAndSchoolId} or {@code TenantGuard} (see CLAUDE.md).
 */
@Getter
@MappedSuperclass
@FilterDef(
		name = TenantEntity.TENANT_FILTER,
		defaultCondition = "school_id = :schoolId",
		parameters = @ParamDef(name = "schoolId", type = Long.class, resolver = TenantIdResolver.class),
		autoEnabled = true,
		applyToLoadByKey = true)
@Filter(name = TenantEntity.TENANT_FILTER)
public abstract class TenantEntity extends BaseEntity {

	public static final String TENANT_FILTER = "tenantFilter";

	@Column(name = "school_id", nullable = false, updatable = false)
	private Long schoolId;

	@PrePersist
	void assignSchool() {
		Long current = TenantContext.require();
		if (schoolId != null && !Objects.equals(schoolId, current)) {
			throw new IllegalStateException("Entity schoolId does not match the current tenant");
		}
		schoolId = current;
	}

}
