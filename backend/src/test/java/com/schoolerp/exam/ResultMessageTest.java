package com.schoolerp.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.schoolerp.auth.entity.Role;
import com.schoolerp.student.StudentTestSupport;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.FakeIntegrations.FakeWhatsApp;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;

/** "Results are out" WhatsApp messages, once an exam is published. */
class ResultMessageTest extends StudentTestSupport {

	@Autowired
	private FakeWhatsApp whatsApp;

	private TestSchool school;

	private Setup setup;

	private TestUser p;

	private String ashaPhone;

	private long asha;

	private long exam;

	@BeforeEach
	void examWithPapers() throws Exception {
		school = fixtures.school("Result Message School");
		setup = academic.todaySetup(school);
		p = school.principal();
		ashaPhone = uniquePhone();
		asha = idOf(admit(p, "R1", "Asha", ashaPhone, setup.sectionId(), "\"whatsappConsent\":true")
			.andExpect(status().isCreated()));
		long scale = idOf(postAs(p, "/api/v1/exams/grading-scales/presets/CBSE_9_POINT", null)
			.andExpect(status().isCreated()));
		exam = idOf(postAs(p, "/api/v1/exams", """
				{"academicYearId":%d,"name":"FA1","gradingScaleId":%d,"displayOrder":1}"""
			.formatted(setup.yearId(), scale)).andExpect(status().isCreated()));
		postAs(p, "/api/v1/exams/{id}/papers/apply", "{\"maxMarks\":50}", exam).andExpect(status().isOk());
	}

	private String url(String action) {
		return "/api/v1/exams/" + exam + "/results/" + action + "?sectionId=" + setup.sectionId();
	}

	private ResultActions notifyParents(TestUser user, long studentId) throws Exception {
		return postAs(user, url("notify"), "{\"studentIds\":[%d]}".formatted(studentId));
	}

	@Test
	void onlyPublishedResultsAreAnnounced() throws Exception {
		notifyParents(p, asha).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("Publish FA1")));

		postAs(p, "/api/v1/exams/{id}/publish", null, exam).andExpect(status().isOk());
		notifyParents(p, asha).andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(1));
		List<FakeWhatsApp.Sent> sent = whatsApp.to(ashaPhone);
		assertThat(sent).hasSize(1);
		assertThat(sent.getFirst().template()).isEqualTo("results_published");
		assertThat(sent.getFirst().params()).containsExactly("FA1", "Asha Reddy", "Class 5 – A", "Result Message School");
		getAs(p, url("notifications"))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].studentId").value(asha))
			.andExpect(jsonPath("$[0].status").value("SENT"));
	}

	@Test
	void principalAndAdminOnlyAndOnlyTheirSchool() throws Exception {
		postAs(p, "/api/v1/exams/{id}/publish", null, exam).andExpect(status().isOk());
		TestUser teacher = fixtures.user(school, "t", Role.TEACHER);
		academic.assign(school, setup.sectionId(), setup.subjectId(), teacher.id());
		notifyParents(teacher, asha).andExpect(status().isForbidden());
		notifyParents(fixtures.user(school, "adm", Role.ADMIN), asha).andExpect(status().isOk());

		TestSchool other = fixtures.school("Other Result School");
		long stranger = admitOk(other.principal(), "Z1", "Zara", academic.todaySetup(other).sectionId());
		notifyParents(p, stranger).andExpect(status().isBadRequest());
		notifyParents(other.principal(), asha).andExpect(status().isNotFound());
		assertThat(whatsApp.to(ashaPhone)).hasSize(1);
	}

}
