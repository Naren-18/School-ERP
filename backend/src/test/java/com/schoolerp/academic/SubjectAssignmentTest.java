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

class SubjectAssignmentTest extends IntegrationTest {

	private static String mapping(Long yearId, Long classId, Long... subjectIds) {
		String ids = String.join(",", java.util.Arrays.stream(subjectIds).map(String::valueOf).toList());
		return """
				{"academicYearId":%d,"classId":%d,"subjectIds":[%s]}""".formatted(yearId, classId, ids);
	}

	private static String assignment(Long sectionId, Long subjectId, Long teacherId) {
		return """
				{"sectionId":%d,"subjectId":%d,"teacherId":%d}""".formatted(sectionId, subjectId, teacherId);
	}

	@Test
	void subjectsCrud() throws Exception {
		TestSchool school = fixtures.school("Subject School");
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);
		long id = idOf(postAs(admin, "/api/v1/subjects", """
				{"name":"Telugu","code":"tel","type":"LANGUAGE"}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("TEL")));
		postAs(admin, "/api/v1/subjects", """
				{"name":"telugu","type":"CORE"}""").andExpect(status().isConflict());
		putAs(admin, "/api/v1/subjects/{id}", """
				{"name":"Telugu (First Language)","type":"LANGUAGE"}""", id)
			.andExpect(jsonPath("$.name").value("Telugu (First Language)"));
		deleteAs(admin, "/api/v1/subjects/{id}", id).andExpect(status().isNoContent());
	}

	@Test
	void classSubjectMappingAndTeacherAssignments() throws Exception {
		TestSchool school = fixtures.school("Mapping School");
		TestUser teacher = fixtures.user(school, "teacher", Role.TEACHER);
		TestUser principal = school.principal();
		Setup setup = academic.basicSetup(school);
		Long english = academic.subject(school, "English");
		Long science = academic.subject(school, "Science");

		putAs(principal, "/api/v1/class-subjects", mapping(setup.yearId(), setup.classId(), english, setup.subjectId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].subjectName").value("English"))
			.andExpect(jsonPath("$[1].displayOrder").value(2));

		// A subject not taught in the class cannot be assigned.
		putAs(principal, "/api/v1/teacher-assignments", assignment(setup.sectionId(), science, teacher.id()))
			.andExpect(status().isBadRequest());
		putAs(principal, "/api/v1/teacher-assignments", assignment(setup.sectionId(), english, teacher.id()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subjectName").value("English"))
			.andExpect(jsonPath("$.className").value("Class 5"));
		// Re-assigning replaces rather than duplicates.
		TestUser teacher2 = fixtures.user(school, "teacher2", Role.TEACHER);
		putAs(principal, "/api/v1/teacher-assignments", assignment(setup.sectionId(), english, teacher2.id()))
			.andExpect(status().isOk());
		getAs(teacher, "/api/v1/teacher-assignments?sectionId={s}", setup.sectionId())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].teacherId").value(teacher2.id()));

		// Dropping English from the class also drops its assignment.
		putAs(principal, "/api/v1/class-subjects", mapping(setup.yearId(), setup.classId(), setup.subjectId()))
			.andExpect(jsonPath("$", hasSize(1)));
		getAs(principal, "/api/v1/teacher-assignments").andExpect(jsonPath("$", hasSize(0)));
		deleteAs(principal, "/api/v1/subjects/{id}", setup.subjectId()).andExpect(status().isConflict());
	}

	@Test
	void schoolACannotTouchSchoolBsSubjectsMappingsOrAssignments() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		Setup b = academic.basicSetup(s.b());
		TestUser bTeacher = fixtures.user(s.b(), "bteacher", Role.TEACHER);
		Long bAssignment = academic.assign(s.b(), b.sectionId(), b.subjectId(), bTeacher.id());
		Setup a = academic.basicSetup(s.a());
		TestUser aTeacher = fixtures.user(s.a(), "ateacher", Role.TEACHER);
		TestUser p = s.a().principal();

		getAs(p, "/api/v1/subjects").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(a.subjectId()));
		putAs(p, "/api/v1/subjects/{id}", """
				{"name":"Hacked","type":"CORE"}""", b.subjectId()).andExpect(status().isNotFound());
		deleteAs(p, "/api/v1/subjects/{id}", b.subjectId()).andExpect(status().isNotFound());

		getAs(p, "/api/v1/class-subjects?academicYearId={y}&classId={c}", b.yearId(), b.classId())
			.andExpect(status().isNotFound());
		putAs(p, "/api/v1/class-subjects", mapping(b.yearId(), b.classId(), b.subjectId()))
			.andExpect(status().isNotFound());
		putAs(p, "/api/v1/class-subjects", mapping(a.yearId(), a.classId(), b.subjectId()))
			.andExpect(status().isBadRequest());

		getAs(p, "/api/v1/teacher-assignments?sectionId={s}", b.sectionId()).andExpect(status().isNotFound());
		putAs(p, "/api/v1/teacher-assignments", assignment(b.sectionId(), b.subjectId(), aTeacher.id()))
			.andExpect(status().isNotFound());
		putAs(p, "/api/v1/teacher-assignments", assignment(a.sectionId(), a.subjectId(), bTeacher.id()))
			.andExpect(status().isBadRequest());
		deleteAs(p, "/api/v1/teacher-assignments/{id}", bAssignment).andExpect(status().isNotFound());

		getAs(s.b().principal(), "/api/v1/teacher-assignments").andExpect(jsonPath("$", hasSize(1)));
	}

}
