package com.schoolerp.support;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.repository.AppUserRepository;
import com.schoolerp.auth.service.JwtService;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.repository.SchoolRepository;
import com.schoolerp.tenant.TenantContext;

/**
 * Tenant isolation test harness, reused by every module.
 *
 * <pre>
 * TwoSchools s = fixtures.twoSchools();
 * Long bStudentId = fixtures.inSchool(s.b(), () -&gt; createStudent(...).getId());
 * mvc.perform(get("/api/v1/students/" + bStudentId).header(AUTHORIZATION, s.a().principal().bearer()))
 *    .andExpect(status().isNotFound());
 * </pre>
 *
 * Every call creates fresh schools with unique codes, so tests never share data.
 */
@TestComponent
public class TenantFixtures {

	public static final String PASSWORD = "Secret@123";

	private final SchoolRepository schools;

	private final AppUserRepository users;

	private final JwtService jwtService;

	private final TransactionTemplate tx;

	private final String passwordHash;

	public TenantFixtures(SchoolRepository schools, AppUserRepository users, PasswordEncoder passwordEncoder,
			JwtService jwtService, TransactionTemplate tx) {
		this.schools = schools;
		this.users = users;
		this.jwtService = jwtService;
		this.tx = tx;
		this.passwordHash = passwordEncoder.encode(PASSWORD);
	}

	public record TestUser(Long id, String username, String password, List<Role> roles, String token) {

		public String bearer() {
			return "Bearer " + token;
		}

	}

	public record TestSchool(Long id, String code, String name, TestUser principal) {
	}

	public record TwoSchools(TestSchool a, TestSchool b) {
	}

	/** School A and School B, each with a {@code principal} user (same username in both). */
	public TwoSchools twoSchools() {
		return new TwoSchools(school("School A"), school("School B"));
	}

	public TestSchool school(String name) {
		School school = new School();
		school.setCode("t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
		school.setName(name);
		school.setBoard(School.Board.STATE);
		school.setPrimaryColor("#123456");
		schools.save(school);
		TestSchool partial = new TestSchool(school.getId(), school.getCode(), name, null);
		return new TestSchool(school.getId(), school.getCode(), name, user(partial, "principal", Role.PRINCIPAL));
	}

	public TestUser user(TestSchool school, String username, Role... roles) {
		AppUser user = inSchool(school, () -> {
			AppUser u = new AppUser();
			u.setUsername(username);
			u.setFullName(username + " of " + school.name());
			u.setPasswordHash(passwordHash);
			u.setRoles(EnumSet.copyOf(List.of(roles)));
			return users.save(u);
		});
		return new TestUser(user.getId(), username, PASSWORD, List.of(roles), jwtService.issue(user));
	}

	/** Runs {@code work} in a transaction as {@code school}, e.g. to create that school's data. */
	public <T> T inSchool(TestSchool school, Supplier<T> work) {
		return TenantContext.runAs(school.id(), () -> tx.execute(status -> work.get()));
	}

	public void inSchool(TestSchool school, Runnable work) {
		inSchool(school, () -> {
			work.run();
			return null;
		});
	}

	public void setSchoolActive(TestSchool school, boolean active) {
		School entity = schools.findById(school.id()).orElseThrow();
		entity.setActive(active);
		schools.save(entity);
	}

	public void setUserActive(TestSchool school, TestUser user, boolean active) {
		inSchool(school, () -> {
			AppUser entity = users.findByIdAndSchoolId(user.id(), school.id()).orElseThrow();
			entity.setActive(active);
		});
	}

}
