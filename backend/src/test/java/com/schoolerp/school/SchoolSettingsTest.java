package com.schoolerp.school;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.IntegrationTest;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class SchoolSettingsTest extends IntegrationTest {

	private static final String SETTINGS = """
			{"receiptPrefix":"SVS","certificatePrefix":"SVC","weekOffDays":["SUNDAY","SATURDAY"],
			 "modules":{"attendance":true,"fees":false,"exams":true,"certificates":true},
			 "reportCard":{"showRank":true,"showAttendance":false}}""";

	private static final String PROFILE = """
			{"name":"Renamed School","board":"CBSE","medium":"english","address":"Hyderabad",
			 "phone":"9000000001","email":"office@example.test","primaryColor":"#AA3300"}""";

	/** A minimal valid PNG header; enough for type detection. */
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	@Test
	void newSchoolGetsDefaultSettings() throws Exception {
		TestSchool school = fixtures.school("Defaults School");
		getAs(school.principal(), "/api/v1/school")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(school.code()))
			.andExpect(jsonPath("$.settings.weekOffDays[0]").value("SUNDAY"))
			.andExpect(jsonPath("$.settings.modules.fees").value(true))
			.andExpect(jsonPath("$.settings.reportCard.showAttendance").value(true));
	}

	@Test
	void principalUpdatesSettingsAndProfile() throws Exception {
		TestSchool school = fixtures.school("Settings School");
		putAs(school.principal(), "/api/v1/school/settings", SETTINGS)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.settings.receiptPrefix").value("SVS"))
			.andExpect(jsonPath("$.settings.weekOffDays[0]").value("SATURDAY"))
			.andExpect(jsonPath("$.settings.modules.fees").value(false));
		putAs(school.principal(), "/api/v1/school/profile", PROFILE)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Renamed School"))
			.andExpect(jsonPath("$.medium").value("ENGLISH"))
			.andExpect(jsonPath("$.primaryColor").value("#aa3300"));

		getAs(school.principal(), "/api/v1/auth/me").andExpect(jsonPath("$.school.modules.fees").value(false));
	}

	@Test
	void settingsAreValidated() throws Exception {
		TestSchool school = fixtures.school("Validation School");
		putAs(school.principal(), "/api/v1/school/settings", SETTINGS.replace("\"SVS\"", "\"bad prefix\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.receiptPrefix").exists());
		String allDays = """
				["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]""";
		putAs(school.principal(), "/api/v1/school/settings",
				SETTINGS.replace("[\"SUNDAY\",\"SATURDAY\"]", allDays))
			.andExpect(status().isBadRequest());
	}

	@Test
	void adminAndOthersCannotChangeSettings() throws Exception {
		TestSchool school = fixtures.school("Roles School");
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);
		TestUser teacher = fixtures.user(school, "teacher", Role.TEACHER);
		putAs(admin, "/api/v1/school/settings", SETTINGS).andExpect(status().isForbidden());
		putAs(admin, "/api/v1/school/profile", PROFILE).andExpect(status().isForbidden());
		getAs(teacher, "/api/v1/school").andExpect(status().isOk());
	}

	@Test
	void eachSchoolOnlyEverSeesAndChangesItsOwnProfile() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		putAs(s.a().principal(), "/api/v1/school/profile", PROFILE).andExpect(status().isOk());
		getAs(s.b().principal(), "/api/v1/school")
			.andExpect(jsonPath("$.id").value(s.b().id()))
			.andExpect(jsonPath("$.name").value("School B"));
	}

	@Test
	void logoUploadIsServedPubliclyAndReplacesTheOldOne() throws Exception {
		TestSchool school = fixtures.school("Logo School");
		String firstUrl = logoUrl(uploadLogo(school.principal(), PNG).andExpect(status().isOk()));
		mvc.perform(get(firstUrl))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
			.andExpect(content().bytes(PNG));

		Thread.sleep(2);
		String secondUrl = logoUrl(uploadLogo(school.principal(), PNG));
		mvc.perform(get(firstUrl)).andExpect(status().isNotFound());
		mvc.perform(get(secondUrl)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/public/schools/{code}/branding", school.code()))
			.andExpect(jsonPath("$.logoUrl").value(secondUrl));
	}

	@Test
	void logoMustBeARealImage() throws Exception {
		TestSchool school = fixtures.school("Bad Logo School");
		uploadLogo(school.principal(), "<svg onload=alert(1)>".getBytes()).andExpect(status().isBadRequest());
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);
		uploadLogo(admin, PNG).andExpect(status().isForbidden());
	}

	@Test
	void logoOfAnotherSchoolCannotBeFetchedThroughYourCode() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		String bUrl = logoUrl(uploadLogo(s.b().principal(), PNG));
		String fileName = bUrl.substring(bUrl.lastIndexOf('/') + 1);
		mvc.perform(get("/api/v1/public/schools/{code}/logo/{file}", s.a().code(), fileName))
			.andExpect(status().isNotFound());
		// Path tricks never reach storage: rejected by the firewall (400) or the name check (404).
		mvc.perform(get("/api/v1/public/schools/{code}/logo/{file}", s.b().code(), "..%2F..%2Fsecret.png"))
			.andExpect(status().is4xxClientError());
		mvc.perform(get("/api/v1/public/schools/{code}/logo/{file}", s.b().code(), "logo-1.svg"))
			.andExpect(status().isNotFound());
	}

	private ResultActions uploadLogo(TestUser user, byte[] bytes) throws Exception {
		return mvc.perform(multipart("/api/v1/school/logo")
			.file(new MockMultipartFile("file", "logo.png", "image/png", bytes))
			.header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	private static String logoUrl(ResultActions result) throws Exception {
		result.andExpect(jsonPath("$.logoUrl", startsWith("/api/v1/public/schools/")));
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.logoUrl");
	}

}
