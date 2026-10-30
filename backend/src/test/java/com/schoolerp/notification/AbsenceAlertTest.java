package com.schoolerp.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.student.StudentTestSupport;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.FakeIntegrations.FakeWhatsApp;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;

/** WhatsApp absence alerts, parent consent, the school's switches and the message log. */
class AbsenceAlertTest extends StudentTestSupport {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

	private static final String CONSENT = "\"whatsappConsent\":true";

	@Autowired
	private FakeWhatsApp whatsApp;

	private TestSchool school;

	private Setup setup;

	private TestUser principal;

	private String ashaPhone;

	private String balaPhone;

	private String chitraPhone;

	private long asha;

	private long bala;

	private long chitra;

	private long devi;

	@BeforeEach
	void absentStudents() throws Exception {
		school = fixtures.school("Alert School");
		fixtures.setWeekOffDays(school); // every day is a school day
		setup = academic.todaySetup(school);
		principal = school.principal();
		ashaPhone = uniquePhone();
		balaPhone = uniquePhone();
		chitraPhone = uniquePhone();
		asha = admitted(admit(principal, "W1", "Asha", ashaPhone, setup.sectionId(), CONSENT));
		bala = admitted(admit(principal, "W2", "Bala", balaPhone, setup.sectionId(), null)); // no consent
		chitra = admitted(admit(principal, "W3", "Chitra", chitraPhone, setup.sectionId(), CONSENT));
		devi = admitted(admit(principal, "W4", "Devi", uniquePhone(), setup.sectionId(), CONSENT));
		whatsApp.failFor(chitraPhone);
		mark(Map.of("Asha", "A", "Bala", "A", "Chitra", "A"));
	}

	private long admitted(ResultActions admission) throws Exception {
		return idOf(admission.andExpect(status().isCreated()));
	}

	private String url() {
		return "/api/v1/attendance/sections/" + setup.sectionId() + "/absence-alerts";
	}

	/** Saves today's attendance: everyone P unless listed. */
	private void mark(Map<String, String> byFirstName) throws Exception {
		String sheetUrl = "/api/v1/attendance/sections/" + setup.sectionId();
		String sheet = getAs(principal, sheetUrl).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> students = JsonPath.read(sheet, "$.students");
		String entries = students.stream()
			.map(s -> "{\"enrollmentId\":%s,\"status\":\"%s\"}".formatted(s.get("enrollmentId"),
					byFirstName.getOrDefault(((String) s.get("fullName")).split(" ")[0], "P")))
			.collect(Collectors.joining(","));
		putAs(principal, sheetUrl, "{\"entries\":[" + entries + "]}").andExpect(status().isOk());
	}

	private ResultActions send(TestUser user, long... studentIds) throws Exception {
		String ids = Arrays.stream(studentIds).mapToObj(String::valueOf).collect(Collectors.joining(","));
		return postAs(user, url(), "{\"studentIds\":[" + ids + "]}");
	}

	@Test
	void sendsOnlyToParentsWhoAgreedAndReportsEachStudent() throws Exception {
		getAs(principal, url())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled").value(true))
			.andExpect(jsonPath("$.connected").value(true))
			.andExpect(jsonPath("$.students.length()").value(3))
			.andExpect(jsonPath("$.students[0].lastStatus").doesNotExist());

		send(principal, asha, bala, chitra)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("ABSENCE"))
			.andExpect(jsonPath("$.sent").value(1))
			.andExpect(jsonPath("$.failed").value(1))
			.andExpect(jsonPath("$.skipped").value(1))
			.andExpect(jsonPath("$.outcomes[?(@.studentId == %d)].detail".formatted(bala))
				.value(contains("Parent has not agreed to WhatsApp messages")))
			.andExpect(jsonPath("$.outcomes[?(@.studentId == %d)].result".formatted(chitra))
				.value(contains("FAILED")));

		List<FakeWhatsApp.Sent> toAsha = whatsApp.to(ashaPhone);
		assertThat(toAsha).hasSize(1);
		assertThat(toAsha.getFirst().template()).isEqualTo("absence_alert");
		assertThat(toAsha.getFirst().params()).containsExactly("Asha Reddy", "Class 5 – A", "Alert School",
				TODAY.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
		assertThat(whatsApp.to(balaPhone)).isEmpty();

		getAs(principal, url())
			.andExpect(jsonPath("$.students[?(@.studentId == %d)].lastStatus".formatted(asha))
				.value(contains("SENT")))
			.andExpect(jsonPath("$.students[?(@.studentId == %d)].lastStatus".formatted(chitra))
				.value(contains("FAILED")));

		// Sending again is allowed (the screen warns); a present student cannot be messaged.
		send(principal, asha).andExpect(jsonPath("$.sent").value(1));
		assertThat(whatsApp.to(ashaPhone)).hasSize(2);
		send(principal, devi).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("marked absent")));
	}

	@Test
	void consentIsSetOnAdmissionAndKeptWhenAnEditLeavesItOut() throws Exception {
		getAs(principal, "/api/v1/students/{id}", asha).andExpect(jsonPath("$.whatsappConsent").value(true));
		getAs(principal, "/api/v1/students/{id}", bala).andExpect(jsonPath("$.whatsappConsent").value(false));
		putAs(principal, "/api/v1/students/{id}", details("W1", "Asha", ashaPhone, null), asha)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.whatsappConsent").value(true));
		putAs(principal, "/api/v1/students/{id}", details("W1", "Asha", ashaPhone, "\"whatsappConsent\":false"), asha)
			.andExpect(jsonPath("$.whatsappConsent").value(false));
		send(principal, asha).andExpect(jsonPath("$.skipped").value(1));
	}

	@Test
	void teachersSendForTheirOwnSectionsAndOthersCannot() throws Exception {
		TestUser stranger = fixtures.user(school, "t2", Role.TEACHER);
		getAs(stranger, url()).andExpect(status().isNotFound());
		send(stranger, asha).andExpect(status().isNotFound());

		TestUser teacher = fixtures.user(school, "t1", Role.TEACHER);
		academic.assign(school, setup.sectionId(), setup.subjectId(), teacher.id());
		send(teacher, asha).andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(1));

		TestUser accountant = fixtures.user(school, "acc", Role.ACCOUNTANT);
		send(accountant, asha).andExpect(status().isForbidden());
	}

	@Test
	void switchedOffMessagesAreRefused() throws Exception {
		fixtures.setMessages(school, new SchoolSettings.Messages(false, true, true, true));
		getAs(principal, url()).andExpect(jsonPath("$.enabled").value(false));
		send(principal, asha).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("switched off")));
		assertThat(whatsApp.to(ashaPhone)).isEmpty();
		getAs(principal, "/api/v1/auth/me").andExpect(jsonPath("$.school.messages.absence").value(false));
	}

	@Test
	void settingsKeepMessageChoicesWhenARequestLeavesThemOut() throws Exception {
		fixtures.setMessages(school, new SchoolSettings.Messages(true, false, true, false));
		putAs(principal, "/api/v1/school/settings", """
				{"receiptPrefix":"ALT","certificatePrefix":"ALT","weekOffDays":["SUNDAY"],
				 "modules":{"attendance":true,"fees":true,"exams":true,"certificates":true},
				 "reportCard":{"showRank":false,"showAttendance":true}}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.settings.messages.feeReminder").value(false))
			.andExpect(jsonPath("$.settings.messages.resultsPublished").value(false));
		putAs(principal, "/api/v1/school/settings", """
				{"receiptPrefix":"ALT","certificatePrefix":"ALT","weekOffDays":["SUNDAY"],
				 "modules":{"attendance":true,"fees":true,"exams":true,"certificates":true},
				 "reportCard":{"showRank":false,"showAttendance":true},
				 "messages":{"absence":true,"feeReminder":true,"paymentReceived":false,"resultsPublished":true}}""")
			.andExpect(jsonPath("$.settings.messages.feeReminder").value(true))
			.andExpect(jsonPath("$.settings.messages.paymentReceived").value(false));
	}

	@Test
	void theLogShowsWhatWasTriedToPrincipalAndAdminOnly() throws Exception {
		send(principal, asha, bala, chitra);
		getAs(principal, "/api/v1/notifications")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2)) // skipped students are not stored
			.andExpect(jsonPath("$.content[?(@.studentName == 'Asha Reddy')].status")
				.value(contains("SENT")))
			.andExpect(jsonPath("$.content[?(@.studentName == 'Chitra Reddy')].error")
				.value(contains("WhatsApp: recipient is not a WhatsApp user")))
			.andExpect(jsonPath("$.content[0].sentByName").value("principal of Alert School"));
		getAs(principal, "/api/v1/notifications?status=FAILED").andExpect(jsonPath("$.totalElements").value(1));
		getAs(principal, "/api/v1/notifications?q=asha").andExpect(jsonPath("$.totalElements").value(1));
		getAs(principal, "/api/v1/notifications?type=FEE_REMINDER").andExpect(jsonPath("$.totalElements").value(0));
		getAs(fixtures.user(school, "adm", Role.ADMIN), "/api/v1/notifications")
			.andExpect(jsonPath("$.totalElements").value(2));
		getAs(fixtures.user(school, "t3", Role.TEACHER), "/api/v1/notifications").andExpect(status().isForbidden());
		getAs(fixtures.user(school, "t4", Role.TEACHER), "/api/v1/notifications/status")
			.andExpect(jsonPath("$.connected").value(true));
	}

	@Test
	void schoolBCannotSeeOrSendForSchoolA() throws Exception {
		send(principal, asha).andExpect(status().isOk());
		TestSchool other = fixtures.school("Other Alert School");
		TestUser otherPrincipal = other.principal();
		getAs(otherPrincipal, url()).andExpect(status().isNotFound());
		send(otherPrincipal, asha).andExpect(status().isNotFound());
		getAs(otherPrincipal, "/api/v1/notifications").andExpect(jsonPath("$.totalElements").value(0));
		assertThat(whatsApp.to(ashaPhone)).hasSize(1);
	}

	@Test
	void switchedOffAttendanceModuleHidesAlerts() throws Exception {
		fixtures.setModules(school, new SchoolSettings.Modules(false, true, true, true));
		getAs(principal, url()).andExpect(status().isNotFound());
		send(principal, asha).andExpect(status().isNotFound());
	}

}
