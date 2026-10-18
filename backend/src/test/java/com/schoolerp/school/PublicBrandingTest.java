package com.schoolerp.school;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.schoolerp.IntegrationTest;
import com.schoolerp.support.TenantFixtures.TestSchool;

class PublicBrandingTest extends IntegrationTest {

	@Test
	void returnsBrandingWithoutAuth() throws Exception {
		TestSchool school = fixtures.school("Brand School");
		mvc.perform(get("/api/v1/public/schools/{code}/branding", school.code().toUpperCase()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Brand School"))
			.andExpect(jsonPath("$.primaryColor").value("#123456"))
			.andExpect(jsonPath("$.id").doesNotExist());
	}

	@Test
	void unknownSchoolIs404() throws Exception {
		mvc.perform(get("/api/v1/public/schools/{code}/branding", "missing")).andExpect(status().isNotFound());
	}

	@Test
	void inactiveSchoolIs404() throws Exception {
		TestSchool school = fixtures.school("Closed School");
		fixtures.setSchoolActive(school, false);
		mvc.perform(get("/api/v1/public/schools/{code}/branding", school.code())).andExpect(status().isNotFound());
	}

}
