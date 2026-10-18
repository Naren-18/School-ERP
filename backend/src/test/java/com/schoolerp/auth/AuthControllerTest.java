package com.schoolerp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.IntegrationTest;
import com.schoolerp.audit.entity.AuditLog;
import com.schoolerp.audit.repository.AuditLogRepository;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.repository.AppUserRepository;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class AuthControllerTest extends IntegrationTest {

	@Autowired
	AppUserRepository users;

	@Autowired
	AuditLogRepository auditLogs;

	private ResultActions login(String schoolCode, String username, String password) throws Exception {
		String body = """
				{"schoolCode":"%s","username":"%s","password":"%s"}""".formatted(schoolCode, username, password);
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String token(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	@Test
	void loginSucceedsAndMeReturnsUserAndSchool() throws Exception {
		TestSchool school = fixtures.school("Sunrise School");
		ResultActions result = login(school.code(), "principal", "Secret@123")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.expiresAt").isNotEmpty());

		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token(result)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.username").value("principal"))
			.andExpect(jsonPath("$.roles[0]").value("PRINCIPAL"))
			.andExpect(jsonPath("$.school.code").value(school.code()))
			.andExpect(jsonPath("$.school.name").value("Sunrise School"));
	}

	@Test
	void schoolCodeAndUsernameAreCaseInsensitive() throws Exception {
		TestSchool school = fixtures.school("Case School");
		login(school.code().toUpperCase(), "PRINCIPAL", "Secret@123").andExpect(status().isOk());
	}

	@Test
	void loginRecordsLastLoginAndAudit() throws Exception {
		TestSchool school = fixtures.school("Audit School");
		login(school.code(), "principal", "Secret@123").andExpect(status().isOk());

		fixtures.inSchool(school, () -> {
			assertThat(users.findByIdAndSchoolId(school.principal().id(), school.id()).orElseThrow().getLastLoginAt())
				.isNotNull();
			List<AuditLog> entries = auditLogs.findBySchoolIdAndEntityAndEntityIdOrderByIdDesc(school.id(),
					"app_user", school.principal().id());
			assertThat(entries).extracting(AuditLog::getAction).contains("LOGIN");
			assertThat(entries.getFirst().getUserId()).isEqualTo(school.principal().id());
		});
	}

	@Test
	void wrongPasswordIsRejectedAndAudited() throws Exception {
		TestSchool school = fixtures.school("Wrong Password School");
		login(school.code(), "principal", "nope")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid school code, username or password"));

		fixtures.inSchool(school, () -> assertThat(auditLogs.findBySchoolIdAndEntityAndEntityIdOrderByIdDesc(
				school.id(), "app_user", school.principal().id()))
			.extracting(AuditLog::getAction)
			.containsExactly("LOGIN_FAILED"));
	}

	@Test
	void unknownUserGetsTheSameError() throws Exception {
		TestSchool school = fixtures.school("Unknown User School");
		login(school.code(), "nobody", "Secret@123")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid school code, username or password"));
	}

	@Test
	void wrongSchoolCodeGetsTheSameError() throws Exception {
		login("no-such-school", "principal", "Secret@123")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid school code, username or password"));
	}

	@Test
	void inactiveUserCannotLogIn() throws Exception {
		TestSchool school = fixtures.school("Inactive User School");
		TestUser teacher = fixtures.user(school, "teacher1", Role.TEACHER);
		fixtures.setUserActive(school, teacher, false);
		login(school.code(), "teacher1", "Secret@123").andExpect(status().isForbidden());
	}

	@Test
	void inactiveSchoolCannotLogIn() throws Exception {
		TestSchool school = fixtures.school("Inactive School");
		fixtures.setSchoolActive(school, false);
		login(school.code(), "principal", "Secret@123").andExpect(status().isForbidden());
	}

	@Test
	void sameUsernameInTwoSchoolsLogsIntoTheRightSchool() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		String tokenA = token(login(s.a().code(), "principal", "Secret@123").andExpect(status().isOk()));
		String tokenB = token(login(s.b().code(), "principal", "Secret@123").andExpect(status().isOk()));

		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenA))
			.andExpect(jsonPath("$.user.id").value(s.a().principal().id()))
			.andExpect(jsonPath("$.school.id").value(s.a().id()));
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenB))
			.andExpect(jsonPath("$.user.id").value(s.b().principal().id()))
			.andExpect(jsonPath("$.school.id").value(s.b().id()));
	}

	@Test
	void repeatedFailuresAreRateLimited() throws Exception {
		TestSchool school = fixtures.school("Rate Limit School");
		for (int i = 0; i < 10; i++) {
			login(school.code(), "principal", "wrong").andExpect(status().isUnauthorized());
		}
		// Blocked even with the right password; other usernames are unaffected.
		login(school.code(), "principal", "Secret@123").andExpect(status().isTooManyRequests());
		fixtures.user(school, "admin1", Role.ADMIN);
		login(school.code(), "admin1", "Secret@123").andExpect(status().isOk());
	}

	@Test
	void blankFieldsAreValidated() throws Exception {
		login("", "", "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.schoolCode").value("School code is required"))
			.andExpect(jsonPath("$.errors.username").value("Username is required"))
			.andExpect(jsonPath("$.errors.password").value("Password is required"));
	}

}
