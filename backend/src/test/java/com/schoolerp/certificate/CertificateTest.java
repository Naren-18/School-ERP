package com.schoolerp.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.schoolerp.auth.entity.Role;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.student.StudentTestSupport;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class CertificateTest extends StudentTestSupport {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

	private TestSchool school;

	private Setup setup;

	private TestUser p;

	private long asha;

	@BeforeEach
	void schoolWithStudent() throws Exception {
		school = fixtures.school("Certificate School");
		setup = academic.todaySetup(school);
		p = school.principal();
		asha = admitOk(p, "C1", "Asha", setup.sectionId());
	}

	private static final String BONAFIDE = """
			{"type":"BONAFIDE","purpose":"a scholarship"}""";

	private static String tc(String leavingDate) {
		return """
				{"type":"TC","tc":{"leavingDate":%s,"reason":"Parent's transfer","conduct":"Good","promotion":"Yes"}}"""
			.formatted(leavingDate == null ? "null" : "\"" + leavingDate + "\"");
	}

	private ResultActions issue(TestUser user, long studentId, String body) throws Exception {
		return postAs(user, "/api/v1/certificates/students/{id}", body, studentId);
	}

	private static String text(byte[] pdf) throws Exception {
		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
		try (var doc = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(doc);
		}
	}

	private static String json(String html) {
		String escaped = html.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
		return "{\"html\":\"" + escaped + "\"}";
	}

	@Test
	void bonafideIsNumberedStoredAndListed() throws Exception {
		byte[] preview = postAs(p, "/api/v1/certificates/students/{id}/preview", BONAFIDE, asha)
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		assertThat(text(preview)).contains("BONAFIDE CERTIFICATE", "Asha Reddy", "Class 5 – A", "a scholarship", "(preview)");

		long first = idOf(issue(p, asha, BONAFIDE).andExpect(status().isCreated())
			.andExpect(jsonPath("$.serialNo", endsWith("/BON/0001")))
			.andExpect(jsonPath("$.studentName").value("Asha Reddy")));
		issue(p, asha, BONAFIDE).andExpect(jsonPath("$.serialNo", endsWith("/BON/0002")));

		byte[] pdf = getAs(p, "/api/v1/certificates/{id}/pdf", first).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(text(pdf)).contains("/BON/0001", "Asha Reddy");

		getAs(p, "/api/v1/certificates?type=BONAFIDE&q=asha").andExpect(jsonPath("$.totalElements").value(2));
		getAs(p, "/api/v1/certificates?q=nobody").andExpect(jsonPath("$.totalElements").value(0));
		getAs(p, "/api/v1/certificates/students/{id}", asha).andExpect(jsonPath("$.length()").value(2));

		TestUser admin = fixtures.user(school, "adm", Role.ADMIN);
		issue(admin, asha, BONAFIDE).andExpect(status().isCreated());
		TestUser accountant = fixtures.user(school, "acc", Role.ACCOUNTANT);
		TestUser teacher = fixtures.user(school, "tch", Role.TEACHER);
		issue(accountant, asha, BONAFIDE).andExpect(status().isForbidden());
		getAs(teacher, "/api/v1/certificates").andExpect(status().isForbidden());
	}

	@Test
	void tcMarksTheStudentLeftAndCannotBeIssuedTwice() throws Exception {
		issue(p, asha, tc(null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("Date of leaving")));
		long id = idOf(issue(p, asha, tc(TODAY.toString())).andExpect(status().isCreated())
			.andExpect(jsonPath("$.serialNo", endsWith("/TC/0001"))));
		getAs(p, "/api/v1/students/{id}", asha)
			.andExpect(jsonPath("$.status").value("TC_ISSUED"))
			.andExpect(jsonPath("$.leftOn").value(TODAY.toString()))
			.andExpect(jsonPath("$.currentEnrollment.status").value("LEFT"));

		String printed = text(getAs(p, "/api/v1/certificates/{id}/pdf", id).andReturn().getResponse().getContentAsByteArray());
		assertThat(printed).contains("TRANSFER CERTIFICATE", "Asha Reddy", "Parent's transfer", "Class 5 – A");

		issue(p, asha, tc(TODAY.toString())).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("/TC/0001")));
		issue(p, asha, BONAFIDE).andExpect(status().isConflict());

		// A student who already left gets a TC without a new leaving date.
		long bala = admitOk(p, "C2", "Bala", setup.sectionId());
		postAs(p, "/api/v1/students/{id}/leave", """
				{"leftOn":"%s","reason":"Moved away"}""".formatted(TODAY), bala).andExpect(status().isOk());
		issue(p, bala, tc(null)).andExpect(status().isCreated()).andExpect(jsonPath("$.serialNo", endsWith("/TC/0002")));
	}

	@Test
	void schoolsEditTheirTemplates() throws Exception {
		getAs(p, "/api/v1/certificates/templates/BONAFIDE")
			.andExpect(jsonPath("$.custom").value(false))
			.andExpect(jsonPath("$.html", containsString("{{student.fullName}}")))
			.andExpect(jsonPath("$.values['student.dateOfBirthInWords']").exists());

		String url = "/api/v1/certificates/templates/BONAFIDE";
		putAs(p, url, json("<p>{{student.nickname}}</p>")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("{{student.nickname}}")));
		putAs(p, url, json("<p>Unclosed<br></p>")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("not valid XHTML")));
		putAs(p, url, json("<script>x</script>")).andExpect(status().isBadRequest());
		putAs(p, url, json("<p>{{{student.fullName}}}</p>")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("two braces")));
		getAs(p, url).andExpect(jsonPath("$.custom").value(false));

		// External resources are never fetched; the template still renders.
		putAs(p, url, json("""
				<div>{{{school.logo}}}<img src="http://169.254.169.254/latest/meta-data"/>
				<p class="title">Our own bonafide for {{student.fullName}} ({{certificate.serialNo}})</p></div>"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.custom").value(true));
		long id = idOf(issue(p, asha, BONAFIDE));
		assertThat(text(getAs(p, "/api/v1/certificates/{id}/pdf", id).andReturn().getResponse().getContentAsByteArray()))
			.contains("Our own bonafide for Asha Reddy");

		postAs(p, url + "/preview", json("<p>{{student.fullName}}</p>")).andExpect(status().isOk());
		deleteAs(p, url).andExpect(status().isOk()).andExpect(jsonPath("$.custom").value(false));

		// The issued certificate does not change with the template.
		assertThat(text(getAs(p, "/api/v1/certificates/{id}/pdf", id).andReturn().getResponse().getContentAsByteArray()))
			.contains("Our own bonafide for Asha Reddy");

		TestUser admin = fixtures.user(school, "adm2", Role.ADMIN);
		getAs(admin, url).andExpect(status().isForbidden());
		getAs(p, "/api/v1/certificates/templates/REPORT_CARD").andExpect(status().isNotFound());
	}

	@Test
	void switchedOffModuleIs404() throws Exception {
		fixtures.setModules(school, new SchoolSettings.Modules(true, true, true, false));
		getAs(p, "/api/v1/certificates").andExpect(status().isNotFound());
		issue(p, asha, BONAFIDE).andExpect(status().isNotFound());
	}

	@Test
	void schoolACannotReachSchoolBsCertificates() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		TestUser aP = s.a().principal();
		long bCert = idOf(issue(p, asha, BONAFIDE));

		getAs(aP, "/api/v1/certificates").andExpect(jsonPath("$.totalElements").value(0));
		getAs(aP, "/api/v1/certificates/{id}/pdf", bCert).andExpect(status().isNotFound());
		getAs(aP, "/api/v1/certificates/students/{id}", asha).andExpect(status().isNotFound());
		issue(aP, asha, BONAFIDE).andExpect(status().isNotFound());
		issue(aP, asha, tc(TODAY.toString())).andExpect(status().isNotFound());
		postAs(aP, "/api/v1/certificates/students/{id}/preview", BONAFIDE, asha).andExpect(status().isNotFound());
		getAs(aP, "/api/v1/certificates/templates/BONAFIDE").andExpect(jsonPath("$.custom").value(false));

		getAs(p, "/api/v1/students/{id}", asha).andExpect(jsonPath("$.status").value("ACTIVE"));
		getAs(p, "/api/v1/certificates").andExpect(jsonPath("$.totalElements").value(1));
	}

}
