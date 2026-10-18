package com.schoolerp.fee;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.student.StudentTestSupport;
import com.schoolerp.support.TenantFixtures.TestUser;

/** Fee request helpers. Dates are relative to today so "overdue" stays meaningful whenever tests run. */
abstract class FeeTestSupport extends StudentTestSupport {

	static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

	long head(TestUser user, String name) throws Exception {
		return idOf(postAs(user, "/api/v1/fees/heads", "{\"name\":\"%s\"}".formatted(name)).andExpect(status().isCreated()));
	}

	long installment(TestUser user, Long yearId, Long classId, long headId, String name, String amount, LocalDate due)
			throws Exception {
		return idOf(postAs(user, "/api/v1/fees/plan", """
				{"academicYearId":%d,"classId":%d,"feeHeadId":%d,"name":"%s","amount":%s,"dueDate":"%s"}"""
			.formatted(yearId, classId, headId, name, amount, due)).andExpect(status().isCreated()));
	}

	String fees(TestUser user, long studentId) throws Exception {
		return getAs(user, "/api/v1/fees/students/{id}", studentId).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	/** Id of the student's due for the named installment. */
	long dueId(TestUser user, long studentId, String installmentName) throws Exception {
		Number id = JsonPath.<List<Number>>read(fees(user, studentId),
				"$.dues[?(@.installmentName == '" + installmentName + "')].id").getFirst();
		return id.longValue();
	}

	/** lines: dueId, amount, dueId, amount, ... */
	ResultActions collect(TestUser user, long studentId, String mode, Object... lines) throws Exception {
		String body = IntStream.range(0, lines.length / 2)
			.mapToObj(i -> "{\"dueId\":%s,\"amount\":%s}".formatted(lines[2 * i], lines[2 * i + 1]))
			.collect(Collectors.joining(","));
		return postAs(user, "/api/v1/fees/receipts", """
				{"studentId":%d,"paymentMode":"%s","referenceNo":"REF1","lines":[%s]}""".formatted(studentId, mode, body));
	}

}
