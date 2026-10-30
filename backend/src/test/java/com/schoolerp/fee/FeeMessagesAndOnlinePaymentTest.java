package com.schoolerp.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.FakeIntegrations.FakePaymentGateway;
import com.schoolerp.support.FakeIntegrations.FakeWhatsApp;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;

/** WhatsApp payment confirmations and fee reminders, and online payment (Razorpay) at the counter. */
class FeeMessagesAndOnlinePaymentTest extends FeeTestSupport {

	private static final String CONSENT = "\"whatsappConsent\":true";

	@Autowired
	private FakeWhatsApp whatsApp;

	@Autowired
	private FakePaymentGateway gateway;

	private TestSchool school;

	private Setup setup;

	private TestUser accountant;

	private String ashaPhone;

	private String balaPhone;

	private long asha;

	private long bala;

	@BeforeEach
	void plan() throws Exception {
		school = fixtures.school("Fee Message School");
		setup = academic.todaySetup(school);
		accountant = fixtures.user(school, "acc", Role.ACCOUNTANT);
		long tuition = head(accountant, "Tuition");
		installment(accountant, setup.yearId(), setup.classId(), tuition, "Term 1", "12000.00", TODAY.minusDays(30));
		installment(accountant, setup.yearId(), setup.classId(), tuition, "Term 2", "8000.50", TODAY.plusDays(30));
		ashaPhone = uniquePhone();
		balaPhone = uniquePhone();
		asha = idOf(admit(school.principal(), "M1", "Asha", ashaPhone, setup.sectionId(), CONSENT)
			.andExpect(status().isCreated()));
		bala = idOf(admit(school.principal(), "M2", "Bala", balaPhone, setup.sectionId(), null)
			.andExpect(status().isCreated()));
	}

	@Test
	void everyReceiptIsConfirmedToParentsWhoAgreed() throws Exception {
		String receipt = collect(accountant, asha, "CASH", dueId(accountant, asha, "Term 1"), "5000")
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		List<FakeWhatsApp.Sent> sent = whatsApp.to(ashaPhone);
		assertThat(sent).hasSize(1);
		assertThat(sent.getFirst().template()).isEqualTo("payment_received");
		assertThat(sent.getFirst().params()).containsExactly("5,000.00", "Asha Reddy", "Class 5 – A",
				JsonPath.<String>read(receipt, "$.receiptNo"), TODAY.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
				"Fee Message School");

		collect(accountant, bala, "CASH", dueId(accountant, bala, "Term 1"), "1000").andExpect(status().isCreated());
		assertThat(whatsApp.to(balaPhone)).isEmpty();

		fixtures.setMessages(school, new SchoolSettings.Messages(true, true, false, true));
		collect(accountant, asha, "CASH", dueId(accountant, asha, "Term 1"), "1000").andExpect(status().isCreated());
		assertThat(whatsApp.to(ashaPhone)).hasSize(1);
	}

	@Test
	void remindersGoToChosenStudentsWithTheirBalance() throws Exception {
		collect(accountant, asha, "CASH", dueId(accountant, asha, "Term 1"), "6000").andExpect(status().isCreated());
		int before = whatsApp.to(ashaPhone).size();

		postAs(accountant, "/api/v1/fees/reminders", "{\"studentIds\":[%d,%d]}".formatted(asha, bala))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("FEE_REMINDER"))
			.andExpect(jsonPath("$.sent").value(1))
			.andExpect(jsonPath("$.skipped").value(1));
		List<FakeWhatsApp.Sent> sent = whatsApp.to(ashaPhone);
		assertThat(sent).hasSize(before + 1);
		assertThat(sent.getLast().template()).isEqualTo("fee_reminder");
		assertThat(sent.getLast().params()).containsExactly("14,000.50", "Asha Reddy", "Class 5 – A", "Fee Message School");

		// Only students on the dues list; teachers may not send; the school can switch reminders off.
		TestSchool other = fixtures.school("Other Fee Message School");
		long stranger = admitOk(other.principal(), "X1", "Xavier", academic.todaySetup(other).sectionId());
		postAs(accountant, "/api/v1/fees/reminders", "{\"studentIds\":[%d]}".formatted(stranger))
			.andExpect(status().isBadRequest());
		postAs(fixtures.user(school, "t", Role.TEACHER), "/api/v1/fees/reminders", "{\"studentIds\":[%d]}".formatted(asha))
			.andExpect(status().isForbidden());
		fixtures.setMessages(school, new SchoolSettings.Messages(true, false, true, true));
		postAs(accountant, "/api/v1/fees/reminders", "{\"studentIds\":[%d]}".formatted(asha))
			.andExpect(status().isConflict());
	}

	private ResultActions order(TestUser user, long studentId, long dueId, String amount) throws Exception {
		return postAs(user, "/api/v1/fees/online/orders", """
				{"studentId":%d,"lines":[{"dueId":%d,"amount":%s}]}""".formatted(studentId, dueId, amount));
	}

	private ResultActions complete(TestUser user, long id, String orderId, String paymentId, String signature)
			throws Exception {
		return postAs(user, "/api/v1/fees/online/orders/{id}/complete", """
				{"razorpayOrderId":"%s","razorpayPaymentId":"%s","razorpaySignature":"%s"}"""
			.formatted(orderId, paymentId, signature), id);
	}

	@Test
	void onlinePaymentBecomesAReceiptOnlyOnceAndOnlyWhenVerified() throws Exception {
		getAs(accountant, "/api/v1/fees/online/config")
			.andExpect(jsonPath("$.enabled").value(true))
			.andExpect(jsonPath("$.keyId").value("rzp_test_fake"));

		long term2 = dueId(accountant, asha, "Term 2");
		String created = order(accountant, asha, term2, "8000.50")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.amountPaise").value(800050))
			.andExpect(jsonPath("$.currency").value("INR"))
			.andExpect(jsonPath("$.studentName").value("Asha Reddy"))
			.andExpect(jsonPath("$.contact").value(ashaPhone))
			.andExpect(jsonPath("$.schoolName").value("Fee Message School"))
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.id")).longValue();
		String orderId = JsonPath.read(created, "$.orderId");
		assertThat(gateway.amountOf(orderId)).isEqualTo(800050L);

		complete(accountant, id, orderId, "pay_1", "forged").andExpect(status().isBadRequest());
		complete(accountant, id, "order_other", "pay_1", FakePaymentGateway.signature("order_other", "pay_1"))
			.andExpect(status().isBadRequest());
		getAs(accountant, "/api/v1/fees/students/{id}", asha).andExpect(jsonPath("$.receipts.length()").value(0));

		String receipt = complete(accountant, id, orderId, "pay_1", FakePaymentGateway.signature(orderId, "pay_1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentMode").value("ONLINE"))
			.andExpect(jsonPath("$.referenceNo").value("pay_1"))
			.andExpect(jsonPath("$.totalAmount").value(8000.50))
			.andReturn().getResponse().getContentAsString();
		long receiptId = ((Number) JsonPath.read(receipt, "$.id")).longValue();
		assertThat(whatsApp.to(ashaPhone)).hasSize(1);

		// The payment window may report success twice: still one receipt, one message.
		complete(accountant, id, orderId, "pay_1", FakePaymentGateway.signature(orderId, "pay_1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(receiptId));
		getAs(accountant, "/api/v1/fees/students/{id}", asha)
			.andExpect(jsonPath("$.receipts.length()").value(1))
			.andExpect(jsonPath("$.totals.paid").value(8000.50));
		assertThat(whatsApp.to(ashaPhone)).hasSize(1);
	}

	@Test
	void ordersFollowTheSameRulesAsCashAndFailuresMakeNoReceipt() throws Exception {
		long term1 = dueId(accountant, asha, "Term 1");
		order(accountant, asha, term1, "12000.01").andExpect(status().isConflict());
		order(accountant, asha, dueId(accountant, bala, "Term 1"), "100").andExpect(status().isNotFound());
		order(fixtures.user(school, "t", Role.TEACHER), asha, term1, "100").andExpect(status().isForbidden());

		// The provider reports the payment as failed.
		String created = order(accountant, asha, term1, "500").andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.id")).longValue();
		String orderId = JsonPath.read(created, "$.orderId");
		gateway.refuse("pay_refused");
		complete(accountant, id, orderId, "pay_refused", FakePaymentGateway.signature(orderId, "pay_refused"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("No receipt was created")));
		complete(accountant, id, orderId, "pay_refused", FakePaymentGateway.signature(orderId, "pay_refused"))
			.andExpect(status().isConflict());

		// The due was paid in cash while the parent was paying online: no receipt, the reason is kept.
		created = order(accountant, asha, term1, "12000.00").andReturn().getResponse().getContentAsString();
		id = ((Number) JsonPath.read(created, "$.id")).longValue();
		orderId = JsonPath.read(created, "$.orderId");
		collect(accountant, asha, "CASH", term1, "100").andExpect(status().isCreated());
		complete(accountant, id, orderId, "pay_late", FakePaymentGateway.signature(orderId, "pay_late"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("pay_late")))
			.andExpect(jsonPath("$.detail", containsString("Refund it")));
		getAs(accountant, "/api/v1/fees/students/{id}", asha).andExpect(jsonPath("$.receipts.length()").value(1));
	}

	@Test
	void schoolBCannotCompleteSchoolAsPayment() throws Exception {
		long term1 = dueId(accountant, asha, "Term 1");
		String created = order(accountant, asha, term1, "500").andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.id")).longValue();
		String orderId = JsonPath.read(created, "$.orderId");
		TestUser otherPrincipal = fixtures.school("Other Online School").principal();
		complete(otherPrincipal, id, orderId, "pay_x", FakePaymentGateway.signature(orderId, "pay_x"))
			.andExpect(status().isNotFound());
		order(otherPrincipal, asha, term1, "500").andExpect(status().isNotFound());
		getAs(accountant, "/api/v1/fees/students/{id}", asha).andExpect(jsonPath("$.receipts.length()").value(0));
	}

}
