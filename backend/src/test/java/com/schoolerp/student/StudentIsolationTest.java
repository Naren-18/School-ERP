package com.schoolerp.student;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

/** School A must not read, list, change or move any of School B's students. */
class StudentIsolationTest extends StudentTestSupport {

	@Test
	void schoolACannotReachSchoolBsStudents() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		Setup b = academic.basicSetup(s.b());
		Setup a = academic.basicSetup(s.a());
		String bPhone = uniquePhone();
		long bStudent = idOf(admit(s.b().principal(), "B-1", "Lavanya", bPhone, b.sectionId(), null)
			.andExpect(status().isCreated()));
		long bEnrollment = idOf(getAs(s.b().principal(), "/api/v1/students/{id}", bStudent), "$.currentEnrollment.id");
		TestUser p = s.a().principal();

		// Read and list
		getAs(p, "/api/v1/students").andExpect(jsonPath("$.totalElements").value(0));
		getAs(p, "/api/v1/students?q={q}", bPhone).andExpect(jsonPath("$.totalElements").value(0));
		getAs(p, "/api/v1/students?q=Lavanya").andExpect(jsonPath("$.totalElements").value(0));
		getAs(p, "/api/v1/students?sectionId={s}", b.sectionId()).andExpect(jsonPath("$.totalElements").value(0));
		getAs(p, "/api/v1/students/{id}", bStudent).andExpect(status().isNotFound());
		getAs(p, "/api/v1/students/{id}/photo", bStudent).andExpect(status().isNotFound());
		getAs(p, "/api/v1/sections/{s}/students", b.sectionId()).andExpect(status().isNotFound());

		// Update, status change, enroll, photo
		putAs(p, "/api/v1/students/{id}", details("B-1", "Hacked", uniquePhone(), null), bStudent)
			.andExpect(status().isNotFound());
		postAs(p, "/api/v1/students/{id}/leave", """
				{"leftOn":"2026-09-01","reason":"x"}""", bStudent).andExpect(status().isNotFound());
		postAs(p, "/api/v1/students/{id}/enrollments", """
				{"sectionId":%d}""".formatted(a.sectionId()), bStudent).andExpect(status().isNotFound());
		mvc.perform(multipart("/api/v1/students/{id}/photo", bStudent)
			.file(new MockMultipartFile("file", "p.png", "image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G' }))
			.header(HttpHeaders.AUTHORIZATION, p.bearer())).andExpect(status().isNotFound());

		// Moving B's enrollment, or admitting into B's section
		putAs(p, "/api/v1/enrollments/{id}/section", """
				{"sectionId":%d}""".formatted(a.sectionId()), bEnrollment).andExpect(status().isNotFound());
		admit(p, "A-1", "Mallika", uniquePhone(), b.sectionId(), null).andExpect(status().isNotFound());
		postAs(p, "/api/v1/sections/{s}/renumber", null, b.sectionId()).andExpect(status().isNotFound());
		postAs(p, "/api/v1/promotions", """
				{"sourceSectionId":%d,"targetSectionId":%d,"promoteEnrollmentIds":[%d]}"""
			.formatted(b.sectionId(), a.sectionId(), bEnrollment)).andExpect(status().isNotFound());

		// Same admission number is fine in a different school.
		admit(p, "B-1", "Mallika", uniquePhone(), a.sectionId(), null).andExpect(status().isCreated());
		getAs(p, "/api/v1/students/next-admission-no").andExpect(jsonPath("$.admissionNo").value("B-2"));

		// School B is untouched.
		getAs(s.b().principal(), "/api/v1/students/{id}", bStudent)
			.andExpect(jsonPath("$.firstName").value("Lavanya"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.currentEnrollment.sectionId").value(b.sectionId()));
	}

}
