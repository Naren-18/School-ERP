package com.schoolerp.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.schoolerp.IntegrationTest;
import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.repository.AppUserRepository;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.support.TenantFixtures.TwoSchools;

/** Proves the tenancy plumbing itself; module tests prove their own endpoints. */
class TenantIsolationTest extends IntegrationTest {

	@Autowired
	AppUserRepository users;

	@Test
	void queriesOnlySeeTheCurrentSchool() {
		TwoSchools s = fixtures.twoSchools();
		fixtures.user(s.b(), "b-teacher", Role.TEACHER);

		fixtures.inSchool(s.a(), () -> assertThat(users.findAll())
			.extracting(AppUser::getSchoolId)
			.containsOnly(s.a().id()));
	}

	@Test
	void loadByIdCannotReachAnotherSchool() {
		TwoSchools s = fixtures.twoSchools();
		Long bUserId = s.b().principal().id();

		fixtures.inSchool(s.a(), () -> {
			assertThat(users.findByIdAndSchoolId(bUserId, s.a().id())).isEmpty();
			// Defence in depth: the filter also applies to plain findById.
			assertThat(users.findById(bUserId)).isEmpty();
			assertThatThrownBy(() -> TenantGuard.require(users.findById(bUserId), "User"))
				.isInstanceOf(NotFoundException.class);
		});
	}

	@Test
	void tenantGuardRejectsAnEntityFromAnotherSchool() {
		TwoSchools s = fixtures.twoSchools();
		AppUser bUser = fixtures.inSchool(s.b(), () -> users.findByIdAndSchoolId(s.b().principal().id(), s.b().id())
			.orElseThrow());

		fixtures.inSchool(s.a(), () -> assertThatThrownBy(() -> TenantGuard.require(bUser, "User"))
			.isInstanceOf(NotFoundException.class));
		fixtures.inSchool(s.b(), () -> assertThat(TenantGuard.require(bUser, "User")).isSameAs(bUser));
	}

	@Test
	void insertSetsSchoolIdFromContext() {
		TwoSchools s = fixtures.twoSchools();
		Long id = fixtures.user(s.a(), "new-user", Role.ADMIN).id();
		fixtures.inSchool(s.a(),
				() -> assertThat(users.findByIdAndSchoolId(id, s.a().id()).orElseThrow().getSchoolId())
					.isEqualTo(s.a().id()));
	}

	@Test
	void insertWithoutTenantFails() {
		AppUser user = new AppUser();
		user.setUsername("orphan");
		user.setFullName("Orphan");
		user.setPasswordHash("x");
		assertThatThrownBy(() -> users.save(user)).hasRootCauseMessage("No tenant in context");
	}

	@Test
	void queryWithoutTenantFails() {
		assertThat(TenantContext.get()).isNull();
		assertThatThrownBy(() -> users.findAll()).hasRootCauseMessage("No tenant in context");
	}

}
