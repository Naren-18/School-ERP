package com.schoolerp.student;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.schoolerp.auth.entity.Role;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;

class StudentImportTest extends StudentTestSupport {

	private static final String HEADER = "admission_no,admission_date,first_name,last_name,gender,date_of_birth,"
			+ "class,section,roll_no,father_name,father_phone,mother_name,mother_phone,aadhaar\n";

	private ResultActions upload(TestUser user, String csv, boolean dryRun) throws Exception {
		return mvc.perform(multipart("/api/v1/students/import")
			.file(new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
			.param("dryRun", String.valueOf(dryRun))
			.header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	@Test
	void templateDownloads() throws Exception {
		TestSchool school = fixtures.school("Template School");
		getAs(school.principal(), "/api/v1/students/import/template")
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("admission_no,admission_date,first_name")));
	}

	@Test
	void invalidRowsAreReportedAndNothingIsCreated() throws Exception {
		TestSchool school = fixtures.school("Bad Import School");
		Setup setup = academic.basicSetup(school);
		admitOk(school.principal(), "900", "Existing", setup.sectionId());
		String csv = HEADER
				+ "901,10-06-2026,Navya,Rao,F,05-01-2016,Class 5,A,,Rao,9876500001,,,\n"
				+ "900,10-06-2026,Dup,Rao,F,05-01-2016,Class 5,A,,Rao,9876500002,,,\n"
				+ "902,2026-06-10,Omkar,,X,05-01-2016,Class 9,A,,,,,,\n"
				+ "903,10-06-2026,Pooja,,F,05-01-2016,Class 5,Z,,Rao,12345,,," + invalidAadhaar() + "\n";

		upload(school.principal(), csv, false)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalRows").value(4))
			.andExpect(jsonPath("$.validRows").value(1))
			.andExpect(jsonPath("$.created").value(0))
			.andExpect(jsonPath("$.errors[?(@.row == 3 && @.field == 'admission_no')]").exists())
			.andExpect(jsonPath("$.errors[?(@.row == 4 && @.field == 'admission_date')]").exists())
			.andExpect(jsonPath("$.errors[?(@.row == 4 && @.field == 'gender')]").exists())
			.andExpect(jsonPath("$.errors[?(@.row == 4 && @.field == 'class')]").exists())
			.andExpect(jsonPath("$.errors[?(@.row == 4 && @.field == 'father_phone')]").exists())
			.andExpect(jsonPath("$.errors[?(@.row == 5 && @.field == 'section')]").exists())
			.andExpect(jsonPath("$.errors[?(@.row == 5 && @.field == 'aadhaar')]").exists());
		getAs(school.principal(), "/api/v1/students").andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void validFileIsCheckedThenImported() throws Exception {
		TestSchool school = fixtures.school("Good Import School");
		Setup setup = academic.basicSetup(school);
		String csv = "﻿" + HEADER
				+ "1201,10-06-2026,Rahul,Varma,MALE,05-01-2016,class 5,a,5,Srinivas Varma,98765 00011,,,\n"
				+ "1202,10-06-2026,\"Sravani, K\",,FEMALE,07-02-2016,Class 5,A,,,,Kavitha,+919876500012,"
				+ fakeAadhaar("45678901234") + "\n";

		upload(school.principal(), csv, true)
			.andExpect(jsonPath("$.dryRun").value(true))
			.andExpect(jsonPath("$.validRows").value(2))
			.andExpect(jsonPath("$.created").value(0));
		getAs(school.principal(), "/api/v1/students").andExpect(jsonPath("$.totalElements").value(0));

		upload(school.principal(), csv, false)
			.andExpect(jsonPath("$.created").value(2))
			.andExpect(jsonPath("$.errors.length()").value(0));
		getAs(school.principal(), "/api/v1/sections/{s}/students", setup.sectionId())
			.andExpect(jsonPath("$[0].rollNo").value(5))
			.andExpect(jsonPath("$[1].fullName").value("Sravani, K"))
			.andExpect(jsonPath("$[1].rollNo").value(6));
		getAs(school.principal(), "/api/v1/students?q=9876500012").andExpect(jsonPath("$.totalElements").value(1));

		// Same file again: every row is now a duplicate.
		upload(school.principal(), csv, false).andExpect(jsonPath("$.created").value(0))
			.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	void telanganaBcSubGroupsAndAliasesAreAccepted() throws Exception {
		TestSchool school = fixtures.school("Category School");
		academic.basicSetup(school);
		String csv = "admission_no,admission_date,first_name,gender,date_of_birth,class,section,father_name,father_phone,"
				+ "social_category\n"
				+ "C1,10-06-2026,Anvesh,M,05-01-2016,Class 5,A,Raju,9876500101,BC-A\n"
				+ "C2,10-06-2026,Bhargavi,F,05-01-2016,Class 5,A,Raju,9876500102,bc e\n"
				+ "C3,10-06-2026,Charan,M,05-01-2016,Class 5,A,Raju,9876500103,OC\n"
				+ "C4,10-06-2026,Deepika,F,05-01-2016,Class 5,A,Raju,9876500104,BC-F\n";
		upload(school.principal(), csv, true)
			.andExpect(jsonPath("$.validRows").value(3))
			.andExpect(jsonPath("$.errors[0].row").value(5))
			.andExpect(jsonPath("$.errors[0].field").value("social_category"));

		upload(school.principal(), csv.substring(0, csv.lastIndexOf("C4,")), false)
			.andExpect(jsonPath("$.created").value(3));
		long id = idOf(getAs(school.principal(), "/api/v1/students?q=Anvesh"), "$.content[0].id");
		getAs(school.principal(), "/api/v1/students/{id}", id).andExpect(jsonPath("$.socialCategory").value("BC_A"));
		long oc = idOf(getAs(school.principal(), "/api/v1/students?q=Charan"), "$.content[0].id");
		getAs(school.principal(), "/api/v1/students/{id}", oc).andExpect(jsonPath("$.socialCategory").value("GEN"));
	}

	@Test
	void whatsappConsentColumnIsYesOrNoAndDefaultsToNo() throws Exception {
		TestSchool school = fixtures.school("Consent Import School");
		academic.basicSetup(school);
		String csv = "admission_no,admission_date,first_name,gender,date_of_birth,class,section,father_name,father_phone,"
				+ "whatsapp_consent\n"
				+ "K1,10-06-2026,Kiran,M,05-01-2016,Class 5,A,Raju,9876500201,YES\n"
				+ "K2,10-06-2026,Lata,F,05-01-2016,Class 5,A,Raju,9876500202,\n"
				+ "K3,10-06-2026,Mohan,M,05-01-2016,Class 5,A,Raju,9876500203,maybe\n";
		upload(school.principal(), csv, true)
			.andExpect(jsonPath("$.validRows").value(2))
			.andExpect(jsonPath("$.errors[0].row").value(4))
			.andExpect(jsonPath("$.errors[0].field").value("whatsapp_consent"));

		upload(school.principal(), csv.substring(0, csv.lastIndexOf("K3,")), false)
			.andExpect(jsonPath("$.created").value(2));
		long kiran = idOf(getAs(school.principal(), "/api/v1/students?q=Kiran"), "$.content[0].id");
		getAs(school.principal(), "/api/v1/students/{id}", kiran).andExpect(jsonPath("$.whatsappConsent").value(true));
		long lata = idOf(getAs(school.principal(), "/api/v1/students?q=Lata"), "$.content[0].id");
		getAs(school.principal(), "/api/v1/students/{id}", lata).andExpect(jsonPath("$.whatsappConsent").value(false));
	}

	@Test
	void missingColumnsAndRoles() throws Exception {
		TestSchool school = fixtures.school("Columns School");
		academic.basicSetup(school);
		upload(school.principal(), "name,phone\nA,1\n", true).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("Missing columns")));
		upload(fixtures.user(school, "acc", Role.ACCOUNTANT), HEADER, true).andExpect(status().isForbidden());
	}

}
