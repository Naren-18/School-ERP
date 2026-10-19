package com.schoolerp.dashboard;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.student.StudentTestSupport;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class DashboardTest extends StudentTestSupport {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

	private TestSchool school;

	private Setup setup;

	private TestUser p;

	private TestUser teacher;

	private long asha;

	private long exam;

	@BeforeEach
	void schoolWithFeesStudentsAndAnExam() throws Exception {
		school = fixtures.school("Dashboard School");
		fixtures.setWeekOffDays(school); // today is always a school day
		setup = academic.todaySetup(school);
		p = school.principal();
		teacher = fixtures.user(school, "dt", Role.TEACHER);
		academic.assign(school, setup.sectionId(), setup.subjectId(), teacher.id());

		// Plan first, so admissions get their dues: one installment overdue, one not yet due.
		long head = idOf(postAs(p, "/api/v1/fees/heads", "{\"name\":\"Tuition\"}").andExpect(status().isCreated()));
		String plan = """
				{"academicYearId":%d,"classId":%d,"feeHeadId":%d,"name":"%s","amount":%s,"dueDate":"%s"}""";
		postAs(p, "/api/v1/fees/plan", plan.formatted(setup.yearId(), setup.classId(), head, "Term 1", "1000", TODAY.minusDays(1)))
			.andExpect(status().isCreated());
		postAs(p, "/api/v1/fees/plan", plan.formatted(setup.yearId(), setup.classId(), head, "Term 2", "500", TODAY))
			.andExpect(status().isCreated());
		asha = admitOk(p, "D1", "Asha", setup.sectionId());
		admitOk(p, "D2", "Bala", setup.sectionId());

		String fees = getAs(p, "/api/v1/fees/students/{id}", asha).andReturn().getResponse().getContentAsString();
		Number due = JsonPath.<List<Number>>read(fees, "$.dues[?(@.installmentName == 'Term 1')].id").getFirst();
		postAs(p, "/api/v1/fees/receipts", """
				{"studentId":%d,"paymentMode":"CASH","lines":[{"dueId":%s,"amount":300}]}""".formatted(asha, due))
			.andExpect(status().isCreated());

		long scale = idOf(postAs(p, "/api/v1/exams/grading-scales/presets/CBSE_9_POINT", null));
		exam = idOf(postAs(p, "/api/v1/exams", """
				{"academicYearId":%d,"name":"FA1","gradingScaleId":%d,"startDate":"%s","endDate":"%s"}"""
			.formatted(setup.yearId(), scale, TODAY, TODAY)).andExpect(status().isCreated()));
		postAs(p, "/api/v1/exams/{id}/papers/apply", "{\"maxMarks\":50}", exam);
	}

	private void markToday(Map<String, String> statuses) throws Exception {
		String url = "/api/v1/attendance/sections/" + setup.sectionId() + "?date=" + TODAY;
		List<Map<String, Object>> students = JsonPath.read(getAs(p, url).andReturn().getResponse().getContentAsString(),
				"$.students");
		String entries = students.stream()
			.map(s -> "{\"enrollmentId\":%s,\"status\":\"%s\"}".formatted(s.get("enrollmentId"),
					statuses.getOrDefault(((String) s.get("fullName")).split(" ")[0], "P")))
			.collect(Collectors.joining(","));
		putAs(p, url, "{\"entries\":[" + entries + "]}").andExpect(status().isOk());
	}

	@Test
	void principalSeesTheWholeSchool() throws Exception {
		getAs(p, "/api/v1/dashboard")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.students.total").value(2))
			.andExpect(jsonPath("$.students.byClass[0].className").value("Class 5"))
			.andExpect(jsonPath("$.students.byClass[0].students").value(2))
			.andExpect(jsonPath("$.students.byClass[0].boys").value(2))
			.andExpect(jsonPath("$.students.byClass[0].girls").value(0))
			.andExpect(jsonPath("$.attendance.sectionsToMark").value(1))
			.andExpect(jsonPath("$.attendance.notMarked[0].label").value("Class 5 – A"))
			.andExpect(jsonPath("$.attendance.percentage").doesNotExist())
			.andExpect(jsonPath("$.fees.collectedToday").value(300.0))
			.andExpect(jsonPath("$.fees.collectedThisMonth").value(300.0))
			.andExpect(jsonPath("$.fees.collectedThisYear").value(300.0))
			.andExpect(jsonPath("$.fees.byMonth[-1].amount").value(300.0))
			.andExpect(jsonPath("$.fees.byMode[0].label").value("Cash"))
			.andExpect(jsonPath("$.fees.byHead[0].label").value("Tuition"))
			.andExpect(jsonPath("$.fees.byHead[0].amount").value(300.0))
			.andExpect(jsonPath("$.latestResult").doesNotExist())
			.andExpect(jsonPath("$.fees.outstanding").value(2700.0)) // 2 × 1500 − 300
			.andExpect(jsonPath("$.fees.overdue").value(1700.0)) // 2 × 1000 − 300
			.andExpect(jsonPath("$.fees.topOverdueClasses[0].students").value(2))
			.andExpect(jsonPath("$.fees.trend.length()").value(14))
			.andExpect(jsonPath("$.fees.trend[13].amount").value(300.0))
			.andExpect(jsonPath("$.upcomingExams[0].name").value("FA1"))
			.andExpect(jsonPath("$.recentAdmissions[0].fullName").value("Bala Reddy"))
			.andExpect(jsonPath("$.recentAdmissions[0].className").value("Class 5 – A"))
			.andExpect(jsonPath("$.teacher").doesNotExist());

		markToday(Map.of("Bala", "A"));
		getAs(p, "/api/v1/dashboard")
			.andExpect(jsonPath("$.attendance.percentage").value(50.0))
			.andExpect(jsonPath("$.attendance.absent").value(1))
			.andExpect(jsonPath("$.attendance.notMarked.length()").value(0))
			.andExpect(jsonPath("$.attendance.trend[-1].percentage").value(50.0))
			.andExpect(jsonPath("$.attendance.byClass[0].className").value("Class 5"))
			.andExpect(jsonPath("$.attendance.byClass[0].percentage").value(50.0));
	}

	@Test
	void latestPublishedExamShowsSubjectAverages() throws Exception {
		long paper = JsonPath.<Number>read(
				getAs(p, "/api/v1/exams/{id}/papers", exam).andReturn().getResponse().getContentAsString(), "$[0].id")
			.longValue();
		String url = "/api/v1/exams/papers/" + paper + "/marks?sectionId=" + setup.sectionId();
		List<Number> enrollments = JsonPath.read(getAs(p, url).andReturn().getResponse().getContentAsString(),
				"$.rows[*].enrollmentId");
		// One student scores 40 / 50, the other is absent.
		putAs(p, url, """
				{"entries":[{"enrollmentId":%s,"marks":40,"absent":false},{"enrollmentId":%s,"marks":null,"absent":true}]}"""
			.formatted(enrollments.get(0), enrollments.get(1))).andExpect(status().isOk());
		getAs(p, "/api/v1/dashboard").andExpect(jsonPath("$.latestResult").doesNotExist());

		postAs(p, "/api/v1/exams/{id}/publish", null, exam).andExpect(status().isOk());
		getAs(p, "/api/v1/dashboard")
			.andExpect(jsonPath("$.latestResult.examName").value("FA1"))
			.andExpect(jsonPath("$.latestResult.subjects.length()").value(1))
			.andExpect(jsonPath("$.latestResult.subjects[0].subjectName").value("Mathematics"))
			.andExpect(jsonPath("$.latestResult.subjects[0].students").value(2))
			.andExpect(jsonPath("$.latestResult.subjects[0].averagePercent").value(80.0))
			.andExpect(jsonPath("$.latestResult.subjects[0].passPercent").value(50.0));

		// Another school's dashboard never counts these marks or receipts.
		TestSchool other = fixtures.school("Other Dashboard School");
		academic.todaySetup(other);
		getAs(other.principal(), "/api/v1/dashboard")
			.andExpect(jsonPath("$.latestResult").doesNotExist())
			.andExpect(jsonPath("$.fees.collectedThisYear").value(0))
			.andExpect(jsonPath("$.fees.byMode.length()").value(0))
			.andExpect(jsonPath("$.fees.byHead.length()").value(0));
	}

	@Test
	void eachRoleSeesItsOwnDashboard() throws Exception {
		TestUser accountant = fixtures.user(school, "da", Role.ACCOUNTANT);
		getAs(accountant, "/api/v1/dashboard")
			.andExpect(jsonPath("$.students.total").value(2))
			.andExpect(jsonPath("$.fees.outstanding").value(2700.0))
			.andExpect(jsonPath("$.attendance").doesNotExist())
			.andExpect(jsonPath("$.recentAdmissions").doesNotExist())
			.andExpect(jsonPath("$.teacher").doesNotExist());

		getAs(teacher, "/api/v1/dashboard")
			.andExpect(jsonPath("$.students").doesNotExist())
			.andExpect(jsonPath("$.fees").doesNotExist())
			.andExpect(jsonPath("$.teacher.sections.length()").value(1))
			.andExpect(jsonPath("$.teacher.sections[0].students").value(2))
			.andExpect(jsonPath("$.teacher.sections[0].classTeacher").value(false))
			.andExpect(jsonPath("$.teacher.sections[0].marked").value(false))
			.andExpect(jsonPath("$.teacher.marksPending.length()").value(1))
			.andExpect(jsonPath("$.teacher.marksPending[0].subjectName").value("Mathematics"))
			.andExpect(jsonPath("$.teacher.marksPending[0].entered").value(0));
		markToday(Map.of());
		getAs(teacher, "/api/v1/dashboard").andExpect(jsonPath("$.teacher.sections[0].marked").value(true));

		TestUser stranger = fixtures.user(school, "ds", Role.TEACHER);
		getAs(stranger, "/api/v1/dashboard")
			.andExpect(jsonPath("$.teacher.sections.length()").value(0))
			.andExpect(jsonPath("$.teacher.marksPending.length()").value(0));
	}

	@Test
	void switchedOffModulesDropTheirWidgets() throws Exception {
		fixtures.setModules(school, new SchoolSettings.Modules(false, false, false, true));
		getAs(p, "/api/v1/dashboard")
			.andExpect(jsonPath("$.students.total").value(2))
			.andExpect(jsonPath("$.attendance").doesNotExist())
			.andExpect(jsonPath("$.fees").doesNotExist())
			.andExpect(jsonPath("$.upcomingExams").doesNotExist());
		getAs(teacher, "/api/v1/dashboard")
			.andExpect(jsonPath("$.teacher.sections[0].marked").doesNotExist())
			.andExpect(jsonPath("$.teacher.marksPending.length()").value(0));
	}

	@Test
	void schoolACountsOnlyItsOwnData() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		academic.todaySetup(s.a());
		getAs(s.a().principal(), "/api/v1/dashboard")
			.andExpect(jsonPath("$.students.total").value(0))
			.andExpect(jsonPath("$.fees.collectedToday").value(0))
			.andExpect(jsonPath("$.fees.outstanding").value(0))
			.andExpect(jsonPath("$.fees.topOverdueClasses.length()").value(0))
			.andExpect(jsonPath("$.upcomingExams.length()").value(0))
			.andExpect(jsonPath("$.recentAdmissions.length()").value(0));
		// A school with no academic year yet: nothing to show (the screen shows the setup checklist).
		getAs(s.b().principal(), "/api/v1/dashboard")
			.andExpect(jsonPath("$.academicYearName").doesNotExist())
			.andExpect(jsonPath("$.students").doesNotExist());
	}

}
