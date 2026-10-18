package com.schoolerp.academic;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.schoolerp.IntegrationTest;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class ClassSectionTest extends IntegrationTest {

	private static String section(Long yearId, Long classId, String name, Long teacherId) {
		return """
				{"academicYearId":%d,"classId":%d,"name":"%s","classTeacherId":%s,"capacity":40}"""
			.formatted(yearId, classId, name, teacherId);
	}

	@Test
	void classesAreListedInOrder() throws Exception {
		TestSchool school = fixtures.school("Class School");
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);
		postAs(admin, "/api/v1/classes", """
				{"name":"Class 2","displayOrder":2}""").andExpect(status().isCreated());
		postAs(admin, "/api/v1/classes", """
				{"name":"Nursery","displayOrder":0}""").andExpect(status().isCreated());
		postAs(admin, "/api/v1/classes", """
				{"name":"nursery","displayOrder":1}""").andExpect(status().isConflict());

		getAs(admin, "/api/v1/classes")
			.andExpect(jsonPath("$[0].name").value("Nursery"))
			.andExpect(jsonPath("$[1].name").value("Class 2"));
	}

	@Test
	void sectionsWithClassTeacher() throws Exception {
		TestSchool school = fixtures.school("Section School");
		TestUser teacher = fixtures.user(school, "teacher", Role.TEACHER);
		TestUser accountant = fixtures.user(school, "accountant", Role.ACCOUNTANT);
		Long yearId = academic.year(school, "2026-27", true);
		Long classId = academic.schoolClass(school, "Class 5", 5);
		TestUser principal = school.principal();

		long id = idOf(postAs(principal, "/api/v1/sections", section(yearId, classId, "a", teacher.id()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("A"))
			.andExpect(jsonPath("$.className").value("Class 5"))
			.andExpect(jsonPath("$.classTeacherName").value(teacher.username() + " of Section School")));
		postAs(principal, "/api/v1/sections", section(yearId, classId, "A", null)).andExpect(status().isConflict());
		postAs(principal, "/api/v1/sections", section(yearId, classId, "B", accountant.id()))
			.andExpect(status().isBadRequest());

		getAs(accountant, "/api/v1/sections").andExpect(jsonPath("$", hasSize(1)));
		getAs(accountant, "/api/v1/sections?classId={c}", classId + 999).andExpect(jsonPath("$", hasSize(0)));

		deleteAs(principal, "/api/v1/classes/{id}", classId).andExpect(status().isConflict());
		deleteAs(principal, "/api/v1/sections/{id}", id).andExpect(status().isNoContent());
		deleteAs(principal, "/api/v1/classes/{id}", classId).andExpect(status().isNoContent());
	}

	@Test
	void noCurrentYearMeansNoSections() throws Exception {
		TestSchool school = fixtures.school("Empty School");
		getAs(school.principal(), "/api/v1/sections").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void schoolACannotTouchSchoolBsClassesAndSections() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		Setup b = academic.basicSetup(s.b());
		Long aYear = academic.year(s.a(), "2026-27", true);
		TestUser a = s.a().principal();

		getAs(a, "/api/v1/classes").andExpect(jsonPath("$", hasSize(0)));
		putAs(a, "/api/v1/classes/{id}", """
				{"name":"Hacked","displayOrder":1}""", b.classId()).andExpect(status().isNotFound());
		deleteAs(a, "/api/v1/classes/{id}", b.classId()).andExpect(status().isNotFound());

		getAs(a, "/api/v1/sections").andExpect(jsonPath("$", hasSize(0)));
		getAs(a, "/api/v1/sections?academicYearId={y}", b.yearId()).andExpect(status().isNotFound());
		getAs(a, "/api/v1/sections/{id}", b.sectionId()).andExpect(status().isNotFound());
		putAs(a, "/api/v1/sections/{id}", section(b.yearId(), b.classId(), "Z", null), b.sectionId())
			.andExpect(status().isNotFound());
		deleteAs(a, "/api/v1/sections/{id}", b.sectionId()).andExpect(status().isNotFound());
		// Creating a section in A that points at B's class is refused.
		postAs(a, "/api/v1/sections", section(aYear, b.classId(), "A", null)).andExpect(status().isNotFound());
		// B's teacher cannot be made A's class teacher.
		TestUser bTeacher = fixtures.user(s.b(), "bteacher", Role.TEACHER);
		Long aClass = academic.schoolClass(s.a(), "Class 1", 1);
		postAs(a, "/api/v1/sections", section(aYear, aClass, "A", bTeacher.id())).andExpect(status().isBadRequest());

		getAs(s.b().principal(), "/api/v1/sections").andExpect(jsonPath("$", hasSize(1)));
	}

}
