package com.schoolerp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SchoolErpApplicationTests extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void flywayCreatesTheSchema() {
		Integer tables = jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
				""", Integer.class);
		// V1: 28, V2: certificate_counter, co_scholastic_area, report_card_note, V3: notification_log, online_payment
		assertThat(tables).isEqualTo(33);
	}

	@Test
	void healthIsPublicAndUp() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void apiRequiresAuthentication() throws Exception {
		mvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Please sign in to continue"));
	}

	@Test
	void invalidTokenIsRejected() throws Exception {
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void nonApiPathsAreDenied() throws Exception {
		mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}

}
