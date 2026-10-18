package com.schoolerp.academic;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.schoolerp.IntegrationTest;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class HolidayTest extends IntegrationTest {

	private static String holiday(Long yearId, String date, String description) {
		return """
				{"academicYearId":%d,"date":"%s","description":"%s"}""".formatted(yearId, date, description);
	}

	@Test
	void holidaysMustFallInTheYearAndNotRepeat() throws Exception {
		TestSchool school = fixtures.school("Holiday School");
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);
		Long yearId = academic.year(school, "2026-27", true);

		long id = idOf(postAs(admin, "/api/v1/holidays", holiday(yearId, "2026-08-15", "Independence Day"))
			.andExpect(status().isCreated()));
		postAs(admin, "/api/v1/holidays", holiday(yearId, "2026-08-15", "Duplicate")).andExpect(status().isConflict());
		postAs(admin, "/api/v1/holidays", holiday(yearId, "2028-01-26", "Out of year"))
			.andExpect(status().isBadRequest());
		postAs(admin, "/api/v1/holidays", holiday(yearId, "2026-10-02", "Gandhi Jayanti"));

		getAs(admin, "/api/v1/holidays")
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].date").value("2026-08-15"));
		putAs(admin, "/api/v1/holidays/{id}", holiday(yearId, "2026-08-16", "Moved"), id)
			.andExpect(jsonPath("$.date").value("2026-08-16"));
		deleteAs(admin, "/api/v1/holidays/{id}", id).andExpect(status().isNoContent());

		TestUser teacher = fixtures.user(school, "teacher", Role.TEACHER);
		getAs(teacher, "/api/v1/holidays").andExpect(jsonPath("$", hasSize(1)));
		postAs(teacher, "/api/v1/holidays", holiday(yearId, "2026-11-01", "x")).andExpect(status().isForbidden());
	}

	@Test
	void schoolACannotTouchSchoolBsHolidays() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		Long bYear = academic.year(s.b(), "2026-27", true);
		Long bHoliday = academic.holiday(s.b(), bYear, LocalDate.of(2026, 8, 15), "Independence Day");
		Long aYear = academic.year(s.a(), "2026-27", true);
		TestUser a = s.a().principal();

		getAs(a, "/api/v1/holidays").andExpect(jsonPath("$", hasSize(0)));
		getAs(a, "/api/v1/holidays?academicYearId={y}", bYear).andExpect(status().isNotFound());
		putAs(a, "/api/v1/holidays/{id}", holiday(aYear, "2026-08-16", "x"), bHoliday).andExpect(status().isNotFound());
		deleteAs(a, "/api/v1/holidays/{id}", bHoliday).andExpect(status().isNotFound());
		postAs(a, "/api/v1/holidays", holiday(bYear, "2026-09-01", "x")).andExpect(status().isNotFound());
		// The same date is fine in a different school.
		postAs(a, "/api/v1/holidays", holiday(aYear, "2026-08-15", "Independence Day")).andExpect(status().isCreated());
	}

}
