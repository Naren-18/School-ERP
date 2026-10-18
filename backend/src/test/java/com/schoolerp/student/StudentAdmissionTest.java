package com.schoolerp.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.schoolerp.auth.entity.Role;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;

class StudentAdmissionTest extends StudentTestSupport {

	@Autowired
	JdbcTemplate jdbc;

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10 };

	@Test
	void admissionCreatesStudentGuardiansAndEnrollment() throws Exception {
		TestSchool school = fixtures.school("Admission School");
		Setup setup = academic.basicSetup(school);
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);
		String phone = uniquePhone();

		long id = idOf(admit(admin, "1001", "Aarav", phone, setup.sectionId(), null)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.fullName").value("Aarav Reddy"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.guardians.length()").value(2))
			.andExpect(jsonPath("$.guardians[0].primary").value(true))
			.andExpect(jsonPath("$.guardians[0].phone").value(phone))
			.andExpect(jsonPath("$.currentEnrollment.className").value("Class 5"))
			.andExpect(jsonPath("$.currentEnrollment.sectionName").value("A"))
			.andExpect(jsonPath("$.currentEnrollment.rollNo").value(1))
			.andExpect(jsonPath("$.currentEnrollment.joinedOn").value("2026-06-10")));
		admit(admin, "1002", "Bhavya", uniquePhone(), setup.sectionId(), null)
			.andExpect(jsonPath("$.currentEnrollment.rollNo").value(2));

		admit(admin, "1001", "Dup", uniquePhone(), setup.sectionId(), null).andExpect(status().isConflict());
		getAs(admin, "/api/v1/students/next-admission-no").andExpect(jsonPath("$.admissionNo").value("1003"));

		Integer audits = jdbc.queryForObject(
				"SELECT count(*) FROM audit_log WHERE school_id = ? AND entity = 'student' AND entity_id = ? AND action = 'CREATE'",
				Integer.class, school.id(), id);
		assertThat(audits).isEqualTo(1);
	}

	@Test
	void nextAdmissionNumberKeepsPrefixAndPadding() throws Exception {
		TestSchool school = fixtures.school("Prefix School");
		Setup setup = academic.basicSetup(school);
		getAs(school.principal(), "/api/v1/students/next-admission-no").andExpect(jsonPath("$.admissionNo").value("1"));
		admitOk(school.principal(), "SSV/2026/009", "Chaitanya", setup.sectionId());
		getAs(school.principal(), "/api/v1/students/next-admission-no")
			.andExpect(jsonPath("$.admissionNo").value("SSV/2026/010"));
	}

	@Test
	void validation() throws Exception {
		TestSchool school = fixtures.school("Student Validation School");
		Setup setup = academic.basicSetup(school);
		TestUser p = school.principal();
		postAs(p, "/api/v1/students", """
				{"student":{"admissionNo":"","firstName":"","guardians":[]},"enrollment":{}}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors['student.admissionNo']").exists())
			.andExpect(jsonPath("$.errors['student.dateOfBirth']").exists())
			.andExpect(jsonPath("$.errors['student.guardians']").exists())
			.andExpect(jsonPath("$.errors['enrollment.sectionId']").exists());
		admit(p, "2001", "Bad", "12345", setup.sectionId(), null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors['student.guardians[0].phone']").value("Enter a 10-digit mobile number"));
		admit(p, "2002", "Bad", uniquePhone(), setup.sectionId(), "\"aadhaar\":\"" + invalidAadhaar() + "\"")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Enter a valid 12-digit Aadhaar number"));
		postAs(p, "/api/v1/students", admission("2003", "NoPhone", uniquePhone(), setup.sectionId(), null)
			.replaceFirst("\"phone\":\"\\d+\",", "")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("At least one parent or guardian needs a mobile number"));
		// Teacher cannot admit.
		TestUser teacher = fixtures.user(school, "teacher", Role.TEACHER);
		admit(teacher, "2004", "X", uniquePhone(), setup.sectionId(), null).andExpect(status().isForbidden());
	}

	@Test
	void aadhaarIsEncryptedAndNeverReturnedInFull() throws Exception {
		TestSchool school = fixtures.school("Aadhaar School");
		Setup setup = academic.basicSetup(school);
		String aadhaar = fakeAadhaar("23456789012");
		String sensitive = "\"aadhaar\":\"%s\",\"religion\":\"Hindu\",\"caste\":\"Kapu\",\"socialCategory\":\"BC\""
			.formatted(aadhaar.substring(0, 4) + " " + aadhaar.substring(4, 8) + " " + aadhaar.substring(8));
		long id = idOf(admit(school.principal(), "3001", "Divya", uniquePhone(), setup.sectionId(), sensitive)
			.andExpect(content().string(not(containsString(aadhaar))))
			.andExpect(jsonPath("$.aadhaarMasked").value("XXXX XXXX " + aadhaar.substring(8)))
			.andExpect(jsonPath("$.caste").value("Kapu")));

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT aadhaar_encrypted, aadhaar_last4 FROM student WHERE id = ?", id);
		assertThat(new String((byte[]) row.get("aadhaar_encrypted"), java.nio.charset.StandardCharsets.ISO_8859_1))
			.doesNotContain(aadhaar);
		assertThat(row.get("aadhaar_last4")).isEqualTo(aadhaar.substring(8));

		// Editing without sending Aadhaar keeps it; sending "" removes it.
		putAs(school.principal(), "/api/v1/students/{id}", details("3001", "Divya", uniquePhone(), null), id)
			.andExpect(jsonPath("$.aadhaarMasked").value("XXXX XXXX " + aadhaar.substring(8)));
		putAs(school.principal(), "/api/v1/students/{id}", details("3001", "Divya", uniquePhone(), "\"aadhaar\":\"\""), id)
			.andExpect(jsonPath("$.aadhaarMasked").doesNotExist());
	}

	@Test
	void sensitiveFieldsAreHiddenFromTeachersAndAccountants() throws Exception {
		TestSchool school = fixtures.school("Masking School");
		Setup setup = academic.basicSetup(school);
		TestUser teacher = fixtures.user(school, "teacher", Role.TEACHER);
		academic.assign(school, setup.sectionId(), setup.subjectId(), teacher.id());
		TestUser accountant = fixtures.user(school, "accountant", Role.ACCOUNTANT);
		long id = idOf(admit(school.principal(), "4001", "Esha", uniquePhone(), setup.sectionId(),
				"\"aadhaar\":\"%s\",\"caste\":\"Kapu\",\"religion\":\"Hindu\"".formatted(fakeAadhaar("34567890123")))
			.andExpect(status().isCreated()));

		for (TestUser user : new TestUser[] { teacher, accountant }) {
			getAs(user, "/api/v1/students/{id}", id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sensitiveVisible").value(false))
				.andExpect(jsonPath("$.caste").doesNotExist())
				.andExpect(jsonPath("$.religion").doesNotExist())
				.andExpect(jsonPath("$.aadhaarMasked").doesNotExist());
			putAs(user, "/api/v1/students/{id}", details("4001", "Esha", uniquePhone(), null), id)
				.andExpect(status().isForbidden());
		}
		getAs(fixtures.user(school, "admin", Role.ADMIN), "/api/v1/students/{id}", id)
			.andExpect(jsonPath("$.sensitiveVisible").value(true))
			.andExpect(jsonPath("$.caste").value("Kapu"));
	}

	@Test
	void photoUploadAndPrivateDownload() throws Exception {
		TestSchool school = fixtures.school("Photo School");
		Setup setup = academic.basicSetup(school);
		long id = admitOk(school.principal(), "5001", "Farhan", setup.sectionId());
		mvc.perform(multipart("/api/v1/students/{id}/photo", id)
			.file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG))
			.header(HttpHeaders.AUTHORIZATION, school.principal().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasPhoto").value(true));
		getAs(school.principal(), "/api/v1/students/{id}/photo", id)
			.andExpect(status().isOk())
			.andExpect(content().bytes(JPEG));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.get("/api/v1/students/{id}/photo", id)).andExpect(status().isUnauthorized());
		mvc.perform(multipart("/api/v1/students/{id}/photo", id)
			.file(new MockMultipartFile("file", "p.jpg", "image/jpeg", "not an image".getBytes()))
			.header(HttpHeaders.AUTHORIZATION, school.principal().bearer()))
			.andExpect(status().isBadRequest());
	}

}
