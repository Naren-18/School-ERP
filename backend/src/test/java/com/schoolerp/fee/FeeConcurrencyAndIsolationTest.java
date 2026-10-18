package com.schoolerp.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class FeeConcurrencyAndIsolationTest extends FeeTestSupport {

	@Test
	void concurrentPaymentsGetUniqueGapFreeNumbersAndNoLostUpdates() throws Exception {
		TestSchool school = fixtures.school("Busy Fee Counter");
		Setup setup = academic.todaySetup(school);
		TestUser acc = fixtures.user(school, "acc", Role.ACCOUNTANT);
		installment(acc, setup.yearId(), setup.classId(), head(acc, "Tuition"), "Term 1", "10000", TODAY);
		long student = admitOk(school.principal(), "C1", "Ravi", setup.sectionId());
		long due = dueId(acc, student, "Term 1");

		int payments = 8;
		ExecutorService pool = Executors.newFixedThreadPool(payments);
		List<Future<String>> results = new ArrayList<>();
		for (int i = 0; i < payments; i++) {
			Callable<String> pay = () -> collect(acc, student, "CASH", due, "100").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
			results.add(pool.submit(pay));
		}
		List<String> numbers = new ArrayList<>();
		for (Future<String> f : results) {
			numbers.add(JsonPath.read(f.get(), "$.receiptNo"));
		}
		pool.shutdown();

		assertThat(numbers).doesNotHaveDuplicates();
		assertThat(numbers.stream().map(n -> Integer.parseInt(n.substring(n.lastIndexOf('/') + 1))).sorted().toList())
			.containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
		// Every payment landed on the same due: none overwrote another.
		getAs(acc, "/api/v1/fees/students/{id}", student).andExpect(jsonPath("$.totals.paid").value(800.00));
	}

	@Test
	void schoolACannotReachSchoolBsFees() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		Setup b = academic.todaySetup(s.b());
		TestUser bAcc = fixtures.user(s.b(), "acc", Role.ACCOUNTANT);
		long bHead = head(bAcc, "Tuition");
		long bInstallment = installment(bAcc, b.yearId(), b.classId(), bHead, "Term 1", "5000", TODAY);
		long bStudent = admitOk(s.b().principal(), "B1", "Lalitha", b.sectionId());
		long bDue = dueId(bAcc, bStudent, "Term 1");
		long bReceipt = idOf(collect(bAcc, bStudent, "CASH", bDue, "1000").andExpect(status().isCreated()));

		Setup a = academic.todaySetup(s.a());
		TestUser aAcc = fixtures.user(s.a(), "acc", Role.ACCOUNTANT);
		long aStudent = admitOk(s.a().principal(), "A1", "Mallika", a.sectionId());

		getAs(aAcc, "/api/v1/fees/heads").andExpect(jsonPath("$.length()").value(0));
		putAs(aAcc, "/api/v1/fees/heads/{id}", "{\"name\":\"Hacked\"}", bHead).andExpect(status().isNotFound());
		getAs(aAcc, "/api/v1/fees/plan?academicYearId={y}", b.yearId()).andExpect(status().isNotFound());
		deleteAs(aAcc, "/api/v1/fees/plan/{id}", bInstallment).andExpect(status().isNotFound());
		postAs(aAcc, "/api/v1/fees/plan", """
				{"academicYearId":%d,"classId":%d,"feeHeadId":%d,"name":"x","amount":1,"dueDate":"%s"}"""
			.formatted(a.yearId(), a.classId(), bHead, TODAY)).andExpect(status().isNotFound());

		getAs(aAcc, "/api/v1/fees/students/{id}", bStudent).andExpect(status().isNotFound());
		putAs(aAcc, "/api/v1/fees/dues/{id}/concession", "{\"concession\":100,\"reason\":\"x\"}", bDue)
			.andExpect(status().isNotFound());
		collect(aAcc, bStudent, "CASH", bDue, "100").andExpect(status().isNotFound());
		collect(aAcc, aStudent, "CASH", bDue, "100").andExpect(status().isNotFound());
		getAs(aAcc, "/api/v1/fees/receipts/{id}", bReceipt).andExpect(status().isNotFound());
		getAs(aAcc, "/api/v1/fees/receipts/{id}/pdf", bReceipt).andExpect(status().isNotFound());
		postAs(aAcc, "/api/v1/fees/receipts/{id}/cancel", "{\"reason\":\"x\"}", bReceipt).andExpect(status().isNotFound());
		getAs(aAcc, "/api/v1/fees/receipts").andExpect(jsonPath("$.totalElements").value(0));
		getAs(aAcc, "/api/v1/fees/reports/collection").andExpect(jsonPath("$.receipts").value(0));
		getAs(aAcc, "/api/v1/fees/reports/dues").andExpect(jsonPath("$.length()").value(0));

		getAs(bAcc, "/api/v1/fees/students/{id}", bStudent).andExpect(jsonPath("$.totals.paid").value(1000.00));
	}

}
