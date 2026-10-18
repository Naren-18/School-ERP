package com.schoolerp.student;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.schoolerp.auth.entity.Role;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;

class StudentSearchTest extends StudentTestSupport {

	@Test
	void searchByNameAdmissionNumberAndGuardianPhone() throws Exception {
		TestSchool school = fixtures.school("Search School");
		Setup setup = academic.basicSetup(school);
		Long sectionB = academic.section(school, setup.yearId(), setup.classId(), "B", null);
		TestUser p = school.principal();
		admit(p, "S-100", "Gautam", "9123456780", setup.sectionId(), null).andExpect(status().isCreated());
		admitOk(p, "S-101", "Harika", sectionB);
		admitOk(p, "S-102", "Ishaan", setup.sectionId());

		getAs(p, "/api/v1/students").andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].className").value("Class 5"))
			.andExpect(jsonPath("$.content[0].guardianName").value("Suresh Reddy"));
		getAs(p, "/api/v1/students?q=harika").andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].admissionNo").value("S-101"));
		getAs(p, "/api/v1/students?q=s-102").andExpect(jsonPath("$.content[0].fullName").value("Ishaan Reddy"));
		getAs(p, "/api/v1/students?q=91234 56780").andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].fullName").value("Gautam Reddy"));
		getAs(p, "/api/v1/students?sectionId={s}", sectionB).andExpect(jsonPath("$.totalElements").value(1));
		getAs(p, "/api/v1/students?classId={c}&size=2", setup.classId())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));
		getAs(p, "/api/v1/students?size=500").andExpect(status().isBadRequest());
	}

	@Test
	void teachersOnlySeeTheirSectionsAccountantsSeeAll() throws Exception {
		TestSchool school = fixtures.school("Scope School");
		Setup setup = academic.basicSetup(school);
		TestUser classTeacher = fixtures.user(school, "ct", Role.TEACHER);
		TestUser subjectTeacher = fixtures.user(school, "st", Role.TEACHER);
		TestUser otherTeacher = fixtures.user(school, "ot", Role.TEACHER);
		Long sectionB = academic.section(school, setup.yearId(), setup.classId(), "B", classTeacher.id());
		academic.assign(school, setup.sectionId(), setup.subjectId(), subjectTeacher.id());
		long inA = admitOk(school.principal(), "T-1", "Jahnavi", setup.sectionId());
		long inB = admitOk(school.principal(), "T-2", "Karthik", sectionB);

		getAs(classTeacher, "/api/v1/students").andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(inB));
		getAs(classTeacher, "/api/v1/students/{id}", inA).andExpect(status().isNotFound());
		getAs(classTeacher, "/api/v1/sections/{s}/students", setup.sectionId()).andExpect(status().isNotFound());
		getAs(classTeacher, "/api/v1/sections/{s}/students", sectionB).andExpect(jsonPath("$.length()").value(1));

		getAs(subjectTeacher, "/api/v1/students").andExpect(jsonPath("$.content[0].id").value(inA));
		getAs(subjectTeacher, "/api/v1/students/{id}", inA).andExpect(status().isOk());

		getAs(otherTeacher, "/api/v1/students").andExpect(jsonPath("$.totalElements").value(0));
		getAs(otherTeacher, "/api/v1/students/{id}/photo", inA).andExpect(status().isNotFound());

		TestUser accountant = fixtures.user(school, "acc", Role.ACCOUNTANT);
		getAs(accountant, "/api/v1/students").andExpect(jsonPath("$.totalElements").value(2));
	}

}
