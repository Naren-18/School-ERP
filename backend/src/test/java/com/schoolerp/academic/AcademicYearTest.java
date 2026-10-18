package com.schoolerp.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.schoolerp.IntegrationTest;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class AcademicYearTest extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	private static String year(String name, String start, String end) {
		return """
				{"name":"%s","startDate":"%s","endDate":"%s"}""".formatted(name, start, end);
	}

	@Test
	void firstYearBecomesCurrentAndOnlyOneIsEverCurrent() throws Exception {
		TestSchool school = fixtures.school("Year School");
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);

		long first = idOf(postAs(admin, "/api/v1/academic-years", year("2025-26", "2025-06-01", "2026-04-30"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.current").value(true)));
		long second = idOf(postAs(admin, "/api/v1/academic-years", year("2026-27", "2026-06-01", "2027-04-30"))
			.andExpect(jsonPath("$.current").value(false)));

		postAs(admin, "/api/v1/academic-years/{id}/make-current", null, second)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current").value(true));
		getAs(admin, "/api/v1/academic-years/current").andExpect(jsonPath("$.id").value(second));
		getAs(admin, "/api/v1/academic-years/{id}", first).andExpect(jsonPath("$.current").value(false));

		Integer current = jdbc.queryForObject(
				"SELECT count(*) FROM academic_year WHERE school_id = ? AND is_current", Integer.class, school.id());
		assertThat(current).isEqualTo(1);
	}

	@Test
	void validationAndConflicts() throws Exception {
		TestSchool school = fixtures.school("Year Rules School");
		TestUser principal = school.principal();
		postAs(principal, "/api/v1/academic-years", year("2026-27", "2026-06-01", "2026-05-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.endDateValid").value("End date must be after start date"));

		long id = idOf(postAs(principal, "/api/v1/academic-years", year("2026-27", "2026-06-01", "2027-04-30")));
		postAs(principal, "/api/v1/academic-years", year("2026-27", "2026-06-01", "2027-04-30"))
			.andExpect(status().isConflict());
		deleteAs(principal, "/api/v1/academic-years/{id}", id)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The current academic year cannot be deleted"));

		academic.holiday(school, id, LocalDate.of(2026, 8, 15), "Independence Day");
		putAs(principal, "/api/v1/academic-years/{id}", year("2026-27", "2026-09-01", "2027-04-30"), id)
			.andExpect(status().isConflict());
	}

	@Test
	void nonCurrentUnusedYearCanBeDeleted() throws Exception {
		TestSchool school = fixtures.school("Delete Year School");
		academic.year(school, "2026-27", true);
		Long old = academic.year(school, "2024-25", false);
		deleteAs(school.principal(), "/api/v1/academic-years/{id}", old).andExpect(status().isNoContent());
		getAs(school.principal(), "/api/v1/academic-years").andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void teachersAndAccountantsCanReadButNotChange() throws Exception {
		TestSchool school = fixtures.school("Year Roles School");
		Long id = academic.year(school, "2026-27", true);
		for (Role role : new Role[] { Role.TEACHER, Role.ACCOUNTANT }) {
			TestUser user = fixtures.user(school, role.name().toLowerCase(), role);
			getAs(user, "/api/v1/academic-years").andExpect(status().isOk());
			postAs(user, "/api/v1/academic-years", year("2027-28", "2027-06-01", "2028-04-30"))
				.andExpect(status().isForbidden());
			postAs(user, "/api/v1/academic-years/{id}/make-current", null, id).andExpect(status().isForbidden());
		}
	}

	@Test
	void schoolACannotReadListUpdateDeleteOrActivateSchoolBsYears() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		academic.year(s.b(), "2026-27", true);
		Long bOld = academic.year(s.b(), "2025-26", false);
		TestUser a = s.a().principal();

		getAs(a, "/api/v1/academic-years").andExpect(jsonPath("$", hasSize(0)));
		getAs(a, "/api/v1/academic-years/current").andExpect(status().isNotFound());
		getAs(a, "/api/v1/academic-years/{id}", bOld).andExpect(status().isNotFound());
		putAs(a, "/api/v1/academic-years/{id}", year("x", "2025-06-01", "2026-04-30"), bOld)
			.andExpect(status().isNotFound());
		postAs(a, "/api/v1/academic-years/{id}/make-current", null, bOld).andExpect(status().isNotFound());
		deleteAs(a, "/api/v1/academic-years/{id}", bOld).andExpect(status().isNotFound());

		getAs(s.b().principal(), "/api/v1/academic-years").andExpect(jsonPath("$", hasSize(2)));
	}

}
