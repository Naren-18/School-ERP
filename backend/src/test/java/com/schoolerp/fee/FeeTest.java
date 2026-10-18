package com.schoolerp.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;

class FeeTest extends FeeTestSupport {

	private TestSchool school;

	private Setup setup;

	private TestUser accountant;

	private long tuition;

	@BeforeEach
	void plan() throws Exception {
		school = fixtures.school("Fee School");
		setup = academic.todaySetup(school);
		accountant = fixtures.user(school, "acc", Role.ACCOUNTANT);
		tuition = head(accountant, "Tuition");
		installment(accountant, setup.yearId(), setup.classId(), tuition, "Term 1", "12000.00", TODAY.minusDays(30));
		installment(accountant, setup.yearId(), setup.classId(), tuition, "Term 2", "8000.50", TODAY.plusDays(30));
	}

	@Test
	void admissionCreatesDuesAndGenerateIsIdempotent() throws Exception {
		long s = admitOk(school.principal(), "F1", "Asha", setup.sectionId());
		getAs(accountant, "/api/v1/fees/students/{id}", s)
			.andExpect(jsonPath("$.dues.length()").value(2))
			.andExpect(jsonPath("$.dues[0].installmentName").value("Term 1"))
			.andExpect(jsonPath("$.dues[0].overdue").value(true))
			.andExpect(jsonPath("$.dues[1].overdue").value(false))
			.andExpect(jsonPath("$.totals.net").value(20000.50))
			.andExpect(jsonPath("$.totals.overdue").value(12000.00));

		// A new installment reaches existing students through "generate dues", once.
		installment(accountant, setup.yearId(), setup.classId(), head(accountant, "Books"), "Annual", "1500", TODAY);
		postAs(accountant, "/api/v1/fees/dues/generate", "{\"academicYearId\":%d}".formatted(setup.yearId()))
			.andExpect(jsonPath("$.created").value(1));
		postAs(accountant, "/api/v1/fees/dues/generate", "{\"academicYearId\":%d}".formatted(setup.yearId()))
			.andExpect(jsonPath("$.created").value(0));
		getAs(accountant, "/api/v1/fees/plan?academicYearId={y}", setup.yearId())
			.andExpect(jsonPath("$[0].students").value(1));
	}

	@Test
	void partialPaymentReceiptAndCancel() throws Exception {
		long s = admitOk(school.principal(), "F2", "Bala", setup.sectionId());
		long term1 = dueId(accountant, s, "Term 1");
		long term2 = dueId(accountant, s, "Term 2");

		String receipt = collect(accountant, s, "UPI", term1, "5000", term2, "8000.50")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.receiptNo", matchesPattern("[A-Z0-9]{1,10}/\\d\\d-\\d\\d/000001")))
			.andExpect(jsonPath("$.totalAmount").value(13000.50))
			.andExpect(jsonPath("$.amountInWords").value("Rupees Thirteen Thousand and Fifty Paise Only"))
			.andExpect(jsonPath("$.collectedByName").value("acc of Fee School"))
			.andExpect(jsonPath("$.lines.length()").value(2))
			.andReturn().getResponse().getContentAsString();
		long receiptId = ((Number) JsonPath.read(receipt, "$.id")).longValue();
		getAs(accountant, "/api/v1/fees/students/{id}", s)
			.andExpect(jsonPath("$.totals.paid").value(13000.50))
			.andExpect(jsonPath("$.totals.balance").value(7000.00))
			.andExpect(jsonPath("$.receipts[0].receiptNo").value(JsonPath.<String>read(receipt, "$.receiptNo")));

		// More than the balance is refused, and nothing changes.
		collect(accountant, s, "CASH", term1, "7000.01").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("more than the ₹7000.00 still due")));
		collect(accountant, s, "CHEQUE", term1, "10").andExpect(status().isCreated());
		postAs(accountant, "/api/v1/fees/receipts", """
				{"studentId":%d,"paymentMode":"CHEQUE","lines":[{"dueId":%d,"amount":10}]}""".formatted(s, term1))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Enter the cheque number"));

		// Cancelling restores the dues; the number stays used.
		postAs(accountant, "/api/v1/fees/receipts/{id}/cancel", "{\"reason\":\"Wrong student\"}", receiptId)
			.andExpect(jsonPath("$.cancelled").value(true))
			.andExpect(jsonPath("$.cancelledByName").value("acc of Fee School"));
		postAs(accountant, "/api/v1/fees/receipts/{id}/cancel", "{\"reason\":\"again\"}", receiptId)
			.andExpect(status().isConflict());
		getAs(accountant, "/api/v1/fees/students/{id}", s).andExpect(jsonPath("$.totals.paid").value(10.00));
		String next = collect(accountant, s, "CASH", term2, "100").andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<String>read(next, "$.receiptNo")).endsWith("/000003");

		mvcPdf(receiptId);
	}

	private void mvcPdf(long receiptId) throws Exception {
		byte[] pdf = getAs(accountant, "/api/v1/fees/receipts/{id}/pdf", receiptId)
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
	}

	@Test
	void concessionsAndPlanEditsRespectPayments() throws Exception {
		long s = admitOk(school.principal(), "F3", "Chitra", setup.sectionId());
		long term1 = dueId(accountant, s, "Term 1");
		putAs(accountant, "/api/v1/fees/dues/{id}/concession", "{\"concession\":2000}", term1)
			.andExpect(status().isBadRequest());
		putAs(accountant, "/api/v1/fees/dues/{id}/concession", "{\"concession\":2000,\"reason\":\"Sibling\"}", term1)
			.andExpect(jsonPath("$.balance").value(10000.00));
		collect(accountant, s, "CASH", term1, "9000").andExpect(status().isCreated());
		putAs(accountant, "/api/v1/fees/dues/{id}/concession", "{\"concession\":3500,\"reason\":\"More\"}", term1)
			.andExpect(status().isConflict());

		long planId = ((Number) JsonPath.read(getAs(accountant, "/api/v1/fees/plan?academicYearId={y}", setup.yearId())
			.andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();
		String update = """
				{"academicYearId":%d,"classId":%d,"feeHeadId":%d,"name":"Term 1","amount":%s,"dueDate":"%s"}""";
		putAs(accountant, "/api/v1/fees/plan/{id}", update.formatted(setup.yearId(), setup.classId(), tuition, "10000",
				TODAY.minusDays(30)), planId).andExpect(status().isConflict());
		putAs(accountant, "/api/v1/fees/plan/{id}", update.formatted(setup.yearId(), setup.classId(), tuition, "13000",
				TODAY.minusDays(30)), planId).andExpect(status().isOk());
		getAs(accountant, "/api/v1/fees/students/{id}", s).andExpect(jsonPath("$.dues[0].amount").value(13000.00));
		deleteAs(accountant, "/api/v1/fees/plan/{id}", planId).andExpect(status().isConflict());
		deleteAs(accountant, "/api/v1/fees/heads/{id}", tuition).andExpect(status().isConflict());
	}

	@Test
	void copyPlanToNextYear() throws Exception {
		Long next = academic.year(school, "2099-00", false);
		// Due dates move by the same number of years as the year start; running it twice copies nothing.
		postAs(accountant, "/api/v1/fees/plan/copy",
				"{\"fromAcademicYearId\":%d,\"toAcademicYearId\":%d}".formatted(setup.yearId(), next))
			.andExpect(jsonPath("$.copied").value(2));
		postAs(accountant, "/api/v1/fees/plan/copy",
				"{\"fromAcademicYearId\":%d,\"toAcademicYearId\":%d}".formatted(setup.yearId(), next))
			.andExpect(jsonPath("$.copied").value(0)).andExpect(jsonPath("$.skipped").value(2));
	}

	@Test
	void reports() throws Exception {
		long a = admitOk(school.principal(), "R1", "Anil", setup.sectionId());
		long b = admitOk(school.principal(), "R2", "Bindu", setup.sectionId());
		collect(accountant, a, "CASH", dueId(accountant, a, "Term 1"), "12000").andExpect(status().isCreated());
		collect(accountant, b, "UPI", dueId(accountant, b, "Term 1"), "2000").andExpect(status().isCreated());

		getAs(accountant, "/api/v1/fees/reports/collection")
			.andExpect(jsonPath("$.total").value(14000.00))
			.andExpect(jsonPath("$.receipts").value(2))
			.andExpect(jsonPath("$.byMode.CASH").value(12000.00))
			.andExpect(jsonPath("$.byMode.UPI").value(2000.00));
		getAs(accountant, "/api/v1/fees/reports/dues")
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[1].fullName").value("Bindu Reddy"))
			.andExpect(jsonPath("$[1].overdue").value(10000.00));
		getAs(accountant, "/api/v1/fees/reports/dues?overdueOnly=true")
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].fullName").value("Bindu Reddy"));
		getAs(accountant, "/api/v1/fees/reports/class-summary")
			.andExpect(jsonPath("$[0].className").value("Class 5"))
			.andExpect(jsonPath("$[0].students").value(2))
			.andExpect(jsonPath("$[0].expected").value(40001.00))
			.andExpect(jsonPath("$[0].collected").value(14000.00))
			.andExpect(jsonPath("$[0].collectedPercent").value(35.0));
	}

	@Test
	void rolesAndModuleSwitch() throws Exception {
		long s = admitOk(school.principal(), "X1", "Esha", setup.sectionId());
		long term1 = dueId(accountant, s, "Term 1");
		TestUser teacher = fixtures.user(school, "teacher", Role.TEACHER);
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);

		getAs(teacher, "/api/v1/fees/students/{id}", s).andExpect(status().isForbidden());
		collect(teacher, s, "CASH", term1, "100").andExpect(status().isForbidden());
		String receipt = collect(admin, s, "CASH", term1, "100").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(receipt, "$.id")).longValue();
		postAs(admin, "/api/v1/fees/receipts/{id}/cancel", "{\"reason\":\"x\"}", id).andExpect(status().isForbidden());
		postAs(school.principal(), "/api/v1/fees/receipts/{id}/cancel", "{\"reason\":\"x\"}", id).andExpect(status().isOk());

		fixtures.setModules(school, new SchoolSettings.Modules(true, false, true, true));
		getAs(accountant, "/api/v1/fees/students/{id}", s).andExpect(status().isNotFound());
	}

}
