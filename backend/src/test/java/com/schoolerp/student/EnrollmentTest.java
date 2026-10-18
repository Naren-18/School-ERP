package com.schoolerp.student;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;

class EnrollmentTest extends StudentTestSupport {

	private long enrollmentId(TestUser user, long studentId) throws Exception {
		return idOf(getAs(user, "/api/v1/students/{id}", studentId), "$.currentEnrollment.id");
	}

	@Test
	void sectionChangeLeaveAndRenumber() throws Exception {
		TestSchool school = fixtures.school("Enrollment School");
		Setup setup = academic.basicSetup(school);
		Long sectionB = academic.section(school, setup.yearId(), setup.classId(), "B", null);
		TestUser p = school.principal();
		long zoya = admitOk(p, "E-1", "Zoya", setup.sectionId());
		long arjun = admitOk(p, "E-2", "Arjun", setup.sectionId());
		long mani = admitOk(p, "E-3", "Mani", setup.sectionId());

		putAs(p, "/api/v1/enrollments/{id}/section", """
				{"sectionId":%d}""".formatted(sectionB), enrollmentId(p, mani))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sectionName").value("B"))
			.andExpect(jsonPath("$.rollNo").value(1));
		putAs(p, "/api/v1/enrollments/{id}/section", """
				{"sectionId":%d,"rollNo":1}""".formatted(sectionB), enrollmentId(p, arjun))
			.andExpect(status().isConflict());

		postAs(p, "/api/v1/sections/{s}/renumber", null, setup.sectionId())
			.andExpect(jsonPath("$[0].fullName").value("Arjun Reddy"))
			.andExpect(jsonPath("$[0].rollNo").value(1))
			.andExpect(jsonPath("$[1].fullName").value("Zoya Reddy"))
			.andExpect(jsonPath("$[1].rollNo").value(2));

		postAs(p, "/api/v1/students/{id}/leave", """
				{"leftOn":"2026-09-30","reason":"Family moved to Pune"}""", zoya)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LEFT"))
			.andExpect(jsonPath("$.enrollments[0].status").value("LEFT"))
			.andExpect(jsonPath("$.enrollments[0].leftOn").value("2026-09-30"));
		postAs(p, "/api/v1/students/{id}/leave", """
				{"leftOn":"2026-09-30","reason":"again"}""", zoya).andExpect(status().isConflict());
		getAs(p, "/api/v1/sections/{s}/students", setup.sectionId()).andExpect(jsonPath("$.length()").value(1));
		getAs(p, "/api/v1/students").andExpect(jsonPath("$.totalElements").value(2));
		getAs(p, "/api/v1/students?status=LEFT").andExpect(jsonPath("$.content[0].id").value(zoya));
	}

	@Test
	void promotionMovesSelectedAndDetainsTheRest() throws Exception {
		TestSchool school = fixtures.school("Promotion School");
		Setup setup = academic.basicSetup(school);
		TestUser p = school.principal();
		long a = admitOk(p, "P-1", "Anil", setup.sectionId());
		long b = admitOk(p, "P-2", "Bindu", setup.sectionId());
		long c = admitOk(p, "P-3", "Chandu", setup.sectionId());

		Long nextYear = academic.year(school, "2027-28", false);
		Long class6 = academic.schoolClass(school, "Class 6", 6);
		Long sixA = academic.section(school, nextYear, class6, "A", null);
		Long fiveAnext = academic.section(school, nextYear, setup.classId(), "A", null);

		String body = """
				{"sourceSectionId":%d,"targetSectionId":%d,"promoteEnrollmentIds":[%d,%d],"detainSectionId":%d}"""
			.formatted(setup.sectionId(), sixA, enrollmentId(p, a), enrollmentId(p, c), fiveAnext);
		postAs(p, "/api/v1/promotions", body)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.promoted").value(2))
			.andExpect(jsonPath("$.detained").value(1))
			.andExpect(jsonPath("$.placedInDetainSection").value(1));

		String sixAStudents = getAs(p, "/api/v1/sections/{s}/students", sixA).andReturn().getResponse()
			.getContentAsString();
		org.assertj.core.api.Assertions.assertThat(JsonPath.<java.util.List<String>>read(sixAStudents, "$[*].fullName"))
			.containsExactly("Anil Reddy", "Chandu Reddy");
		getAs(p, "/api/v1/sections/{s}/students", fiveAnext).andExpect(jsonPath("$[0].fullName").value("Bindu Reddy"));
		String source = getAs(p, "/api/v1/sections/{s}/students?includeInactive=true", setup.sectionId()).andReturn()
			.getResponse()
			.getContentAsString();
		org.assertj.core.api.Assertions.assertThat(JsonPath.<java.util.List<String>>read(source, "$[*].status"))
			.containsExactlyInAnyOrder("PROMOTED", "PROMOTED", "DETAINED");
		getAs(p, "/api/v1/students/{id}", a).andExpect(jsonPath("$.enrollments.length()").value(2))
			.andExpect(jsonPath("$.enrollments[0].academicYearName").value("2027-28"))
			.andExpect(jsonPath("$.enrollments[1].status").value("PROMOTED"));

		// Running it again is refused: nobody is active in the source section any more.
		postAs(p, "/api/v1/promotions", body).andExpect(status().isBadRequest());
		getAs(p, "/api/v1/students/{id}", b).andExpect(status().isOk());
	}

	@Test
	void deletingASectionWithStudentsSaysHowMany() throws Exception {
		TestSchool school = fixtures.school("Delete Section School");
		Setup setup = academic.basicSetup(school);
		TestUser p = school.principal();
		long a = admitOk(p, "D-1", "Asha", setup.sectionId());
		admitOk(p, "D-2", "Bala", setup.sectionId());

		deleteAs(p, "/api/v1/sections/{id}", setup.sectionId())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail")
				.value("2 students are enrolled in Class 5 – A. Move them to another section before deleting it."));

		// Only history left: still blocked, with a different explanation.
		Long sectionB = academic.section(school, setup.yearId(), setup.classId(), "B", null);
		long b = idOf(getAs(p, "/api/v1/students?q=Bala"), "$.content[0].id");
		putAs(p, "/api/v1/enrollments/{id}/section", """
				{"sectionId":%d}""".formatted(sectionB), enrollmentId(p, b)).andExpect(status().isOk());
		postAs(p, "/api/v1/students/{id}/leave", """
				{"leftOn":"2026-09-30","reason":"Moved"}""", a).andExpect(status().isOk());
		deleteAs(p, "/api/v1/sections/{id}", setup.sectionId())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail")
				.value("Class 5 – A has class records for 1 student (promoted, detained or left), so it cannot be deleted."));
	}

	@Test
	void promotionRules() throws Exception {
		TestSchool school = fixtures.school("Promotion Rules School");
		Setup setup = academic.basicSetup(school);
		TestUser p = school.principal();
		long a = admitOk(p, "R-1", "Ravi", setup.sectionId());
		Long sameYearSection = academic.section(school, setup.yearId(), setup.classId(), "B", null);
		Long nextYear = academic.year(school, "2027-28", false);
		Long class6 = academic.schoolClass(school, "Class 6", 6);
		Long sixA = academic.section(school, nextYear, class6, "A", null);

		postAs(p, "/api/v1/promotions", """
				{"sourceSectionId":%d,"targetSectionId":%d,"promoteEnrollmentIds":[]}"""
			.formatted(setup.sectionId(), sameYearSection)).andExpect(status().isBadRequest());
		postAs(p, "/api/v1/promotions", """
				{"sourceSectionId":%d,"targetSectionId":%d,"promoteEnrollmentIds":[],"detainSectionId":%d}"""
			.formatted(setup.sectionId(), sixA, sixA)).andExpect(status().isBadRequest());

		// Already placed in next year (e.g. enrolled manually): refused with names.
		postAs(p, "/api/v1/students/{id}/enrollments", """
				{"sectionId":%d}""".formatted(sixA), a).andExpect(status().isCreated());
		postAs(p, "/api/v1/promotions", """
				{"sourceSectionId":%d,"targetSectionId":%d,"promoteEnrollmentIds":[%d]}"""
			.formatted(setup.sectionId(), sixA, enrollmentId(p, a)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Already enrolled in 2027-28: Ravi Reddy"));
	}

}
