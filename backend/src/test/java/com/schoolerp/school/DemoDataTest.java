package com.schoolerp.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.IntegrationTest;
import com.schoolerp.school.seed.demo.DemoDataService;

/** The demo seeder at a small size: everything a demo screen needs exists, per school, and a rerun adds nothing. */
class DemoDataTest extends IntegrationTest {

	private static final String PASSWORD = "Test@demo-123";

	@Autowired
	DemoDataService demoData;

	@Autowired
	JdbcTemplate jdbc;

	private String login(String school, String username) throws Exception {
		String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"schoolCode\":\"%s\",\"username\":\"%s\",\"password\":\"%s\"}".formatted(school, username, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	@Test
	void seedsThreeCompleteSchoolsOnceOnly() throws Exception {
		DemoDataService.Result first = demoData.seed(new DemoDataService.Options(PASSWORD, 2, 6));
		assertThat(first.created()).containsExactly("vjhs", "lvps", "sems");

		for (String code : first.created()) {
			Long schoolId = jdbc.queryForObject("SELECT id FROM school WHERE code = ?", Long.class, code);
			assertThat(count("SELECT count(*) FROM school_class WHERE school_id = ?", schoolId)).isEqualTo(13);
			assertThat(count("SELECT count(*) FROM section WHERE school_id = ?", schoolId)).isEqualTo(26);
			assertThat(count("SELECT count(*) FROM student WHERE school_id = ?", schoolId)).isEqualTo(52);
			assertThat(count("SELECT count(*) FROM app_user WHERE school_id = ?", schoolId)).isEqualTo(29);
			assertThat(count("SELECT count(*) FROM attendance WHERE school_id = ?", schoolId)).isGreaterThan(0);
			assertThat(count("SELECT count(*) FROM fee_receipt WHERE school_id = ?", schoolId)).isGreaterThan(10);
			assertThat(count("SELECT count(*) FROM exam WHERE school_id = ? AND is_published", schoolId)).isEqualTo(1);
			assertThat(count("SELECT count(*) FROM mark WHERE school_id = ?", schoolId)).isGreaterThan(50);
			assertThat(count("SELECT count(*) FROM report_card_note WHERE school_id = ?", schoolId)).isGreaterThan(0);
			assertThat(count("SELECT count(*) FROM issued_certificate WHERE school_id = ? AND type = 'TC'", schoolId)).isEqualTo(2);
			assertThat(count("SELECT count(*) FROM student WHERE school_id = ? AND aadhaar_encrypted IS NOT NULL", schoolId))
				.isZero();
			// Every phone number is in the reserved-looking 900x range.
			assertThat(count("SELECT count(*) FROM guardian WHERE school_id = ? AND phone IS NOT NULL AND phone NOT LIKE '900%'",
					schoolId)).isZero();

			// The documented logins work, and each school sees only its own numbers.
			String principal = login(code, "principal");
			mvc.perform(get("/api/v1/dashboard").header("Authorization", principal))
				.andExpect(jsonPath("$.students.total").value(greaterThan(40)))
				.andExpect(jsonPath("$.fees.collectedThisMonth").exists())
				.andExpect(jsonPath("$.recentAdmissions.length()").value(5));
			mvc.perform(get("/api/v1/school").header("Authorization", principal))
				.andExpect(jsonPath("$.logoUrl", startsWith("/api/v1/public/schools/" + code + "/logo/")));
			for (String user : new String[] { "admin", "accountant", "teacher1", "teacher2" }) {
				login(code, user);
			}
			mvc.perform(get("/api/v1/dashboard").header("Authorization", login(code, "teacher1")))
				.andExpect(jsonPath("$.teacher.sections.length()").value(greaterThan(1)));
		}

		DemoDataService.Result second = demoData.seed(new DemoDataService.Options(PASSWORD, 2, 6));
		assertThat(second.created()).isEmpty();
		assertThat(second.skipped()).containsExactly("vjhs", "lvps", "sems");
	}

}
