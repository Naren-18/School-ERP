package com.schoolerp.auth;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.schoolerp.IntegrationTest;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class UserManagementTest extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	private static final String NEW_TEACHER = """
			{"username":"lakshmi.t","fullName":"Lakshmi T","designation":"Maths Teacher","phone":"9000000002",
			 "roles":["TEACHER"],"password":"Welcome@123"}""";

	private ResultActions login(String code, String username, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"schoolCode":"%s","username":"%s","password":"%s"}""".formatted(code, username, password)));
	}

	@Test
	void principalCreatesStaffWhoCanThenLogIn() throws Exception {
		TestSchool school = fixtures.school("Staff School");
		long id = idOf(postAs(school.principal(), "/api/v1/users", NEW_TEACHER)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.roles[0]").value("TEACHER"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist()));
		postAs(school.principal(), "/api/v1/users", NEW_TEACHER.replace("lakshmi.t", "LAKSHMI.T"))
			.andExpect(status().isConflict());
		login(school.code(), "lakshmi.t", "Welcome@123").andExpect(status().isOk());

		getAs(school.principal(), "/api/v1/users?q=laksh")
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(id));
		getAs(school.principal(), "/api/v1/users?role=TEACHER&sort=passwordHash")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(1)));

		Integer audits = jdbc.queryForObject("""
				SELECT count(*) FROM audit_log WHERE school_id = ? AND entity = 'app_user' AND entity_id = ?
				AND action = 'CREATE'""", Integer.class, school.id(), id);
		org.assertj.core.api.Assertions.assertThat(audits).isEqualTo(1);
	}

	@Test
	void validation() throws Exception {
		TestSchool school = fixtures.school("Validate Staff School");
		postAs(school.principal(), "/api/v1/users", """
				{"username":"a b","fullName":"","roles":[],"password":"short"}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.username").exists())
			.andExpect(jsonPath("$.errors.fullName").exists())
			.andExpect(jsonPath("$.errors.roles").exists())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void resetPasswordAndDeactivateTakeEffectImmediately() throws Exception {
		TestSchool school = fixtures.school("Revoke School");
		TestUser teacher = fixtures.user(school, "teacher", Role.TEACHER);
		getAs(teacher, "/api/v1/auth/me").andExpect(status().isOk());

		postAs(school.principal(), "/api/v1/users/{id}/reset-password", """
				{"password":"NewPass@123"}""", teacher.id()).andExpect(status().isNoContent());
		login(school.code(), "teacher", "Secret@123").andExpect(status().isUnauthorized());
		login(school.code(), "teacher", "NewPass@123").andExpect(status().isOk());

		postAs(school.principal(), "/api/v1/users/{id}/deactivate", null, teacher.id())
			.andExpect(jsonPath("$.active").value(false));
		// The teacher's existing token stops working at once.
		getAs(teacher, "/api/v1/auth/me").andExpect(status().isUnauthorized());
		login(school.code(), "teacher", "NewPass@123").andExpect(status().isForbidden());

		postAs(school.principal(), "/api/v1/users/{id}/activate", null, teacher.id())
			.andExpect(jsonPath("$.active").value(true));
		getAs(teacher, "/api/v1/auth/me").andExpect(status().isOk());
	}

	@Test
	void roleChangesApplyToExistingTokens() throws Exception {
		TestSchool school = fixtures.school("Role Change School");
		TestUser user = fixtures.user(school, "staff", Role.ADMIN);
		postAs(user, "/api/v1/classes", """
				{"name":"Class 1","displayOrder":1}""").andExpect(status().isCreated());

		putAs(school.principal(), "/api/v1/users/{id}", """
				{"fullName":"Staff","roles":["TEACHER"]}""", user.id()).andExpect(status().isOk());
		postAs(user, "/api/v1/classes", """
				{"name":"Class 2","displayOrder":2}""").andExpect(status().isForbidden());
		getAs(user, "/api/v1/auth/me").andExpect(jsonPath("$.roles[0]").value("TEACHER"));
	}

	@Test
	void theLastPrincipalCannotBeLockedOut() throws Exception {
		TestSchool school = fixtures.school("Lockout School");
		TestUser principal = school.principal();
		postAs(principal, "/api/v1/users/{id}/deactivate", null, principal.id())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You cannot deactivate your own account"));
		putAs(principal, "/api/v1/users/{id}", """
				{"fullName":"Me","roles":["ADMIN"]}""", principal.id())
			.andExpect(status().isConflict());

		TestUser second = fixtures.user(school, "principal2", Role.PRINCIPAL);
		putAs(second, "/api/v1/users/{id}", """
				{"fullName":"Former Principal","roles":["ADMIN"]}""", principal.id()).andExpect(status().isOk());
	}

	@Test
	void onlyThePrincipalManagesUsers() throws Exception {
		TestSchool school = fixtures.school("Admin Users School");
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);
		getAs(admin, "/api/v1/users").andExpect(status().isForbidden());
		postAs(admin, "/api/v1/users", NEW_TEACHER).andExpect(status().isForbidden());
		postAs(admin, "/api/v1/users/{id}/deactivate", null, school.principal().id()).andExpect(status().isForbidden());
		// Admin can still use the staff picker for academic setup.
		fixtures.user(school, "teacher", Role.TEACHER);
		getAs(admin, "/api/v1/staff?role=TEACHER").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
		TestUser teacher = fixtures.user(school, "teacher2", Role.TEACHER);
		getAs(teacher, "/api/v1/staff").andExpect(status().isForbidden());
	}

	@Test
	void schoolACannotSeeOrChangeSchoolBsUsers() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		TestUser bTeacher = fixtures.user(s.b(), "bteacher", Role.TEACHER);
		TestUser a = s.a().principal();

		getAs(a, "/api/v1/users").andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(a.id()));
		getAs(a, "/api/v1/staff").andExpect(jsonPath("$", hasSize(1)));
		getAs(a, "/api/v1/users/{id}", bTeacher.id()).andExpect(status().isNotFound());
		putAs(a, "/api/v1/users/{id}", """
				{"fullName":"Hacked","roles":["PRINCIPAL"]}""", bTeacher.id()).andExpect(status().isNotFound());
		postAs(a, "/api/v1/users/{id}/reset-password", """
				{"password":"Hacked@123"}""", bTeacher.id()).andExpect(status().isNotFound());
		postAs(a, "/api/v1/users/{id}/deactivate", null, bTeacher.id()).andExpect(status().isNotFound());
		getAs(bTeacher, "/api/v1/auth/me").andExpect(status().isOk());
		// Same username in A is independent of B's.
		postAs(a, "/api/v1/users", NEW_TEACHER.replace("lakshmi.t", "bteacher")).andExpect(status().isCreated());
	}

}
