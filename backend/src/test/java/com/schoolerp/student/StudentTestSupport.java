package com.schoolerp.student;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.web.servlet.ResultActions;

import com.schoolerp.IntegrationTest;
import com.schoolerp.common.crypto.Aadhaar;
import com.schoolerp.support.TenantFixtures.TestUser;

/** Request builders shared by the student tests. */
abstract class StudentTestSupport extends IntegrationTest {

	private static final AtomicInteger SEQ = new AtomicInteger(1000);

	/** A fake but checksum-valid Aadhaar number. */
	static String fakeAadhaar(String elevenDigits) {
		return elevenDigits + Aadhaar.checkDigit(elevenDigits);
	}

	/** Same digits as a valid number but with a wrong check digit. */
	static String invalidAadhaar() {
		String base = "23456789012";
		return base + ((Aadhaar.checkDigit(base) + 1) % 10);
	}

	static String uniquePhone() {
		return "9" + String.format("%09d", SEQ.incrementAndGet());
	}

	static String details(String admissionNo, String firstName, String phone, String extra) {
		return """
				{"admissionNo":"%s","admissionDate":"2026-06-10","firstName":"%s","lastName":"Reddy",
				 "dateOfBirth":"2016-03-15","gender":"MALE","address":"Hyderabad"%s,
				 "guardians":[{"relation":"FATHER","fullName":"Suresh Reddy","phone":"%s","primary":true},
				              {"relation":"MOTHER","fullName":"Lakshmi Reddy"}]}"""
			.formatted(admissionNo, firstName, extra == null ? "" : "," + extra, phone);
	}

	static String admission(String admissionNo, String firstName, String phone, Long sectionId, String extra) {
		return """
				{"student":%s,"enrollment":{"sectionId":%d}}""".formatted(details(admissionNo, firstName, phone, extra),
				sectionId);
	}

	ResultActions admit(TestUser user, String admissionNo, String firstName, String phone, Long sectionId,
			String extra) throws Exception {
		return postAs(user, "/api/v1/students", admission(admissionNo, firstName, phone, sectionId, extra));
	}

	long admitOk(TestUser user, String admissionNo, String firstName, Long sectionId) throws Exception {
		return idOf(admit(user, admissionNo, firstName, uniquePhone(), sectionId, null).andExpect(status().isCreated()));
	}

}
