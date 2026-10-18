package com.schoolerp.attendance;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.student.StudentTestSupport;
import com.schoolerp.support.AcademicFixtures.Setup;
import com.schoolerp.support.TenantFixtures.TestSchool;
import com.schoolerp.support.TenantFixtures.TestUser;
import com.schoolerp.support.TenantFixtures.TwoSchools;

class AttendanceTest extends StudentTestSupport {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

	private TestSchool school;

	private Setup setup;

	private TestUser principal;

	private long asha;

	private long chitra;

	@BeforeEach
	void createSchool() throws Exception {
		school = fixtures.school("Attendance School");
		fixtures.setWeekOffDays(school); // every day is a school day, whatever today is
		setup = academic.todaySetup(school);
		principal = school.principal();
		asha = admitOk(principal, "A1", "Asha", setup.sectionId());
		admitOk(principal, "A2", "Bala", setup.sectionId());
		chitra = admitOk(principal, "A3", "Chitra", setup.sectionId());
	}

	private String sheetUrl() {
		return "/api/v1/attendance/sections/" + setup.sectionId();
	}

	/** Builds a save body from the sheet: everyone P unless listed. */
	private String body(TestUser user, LocalDate date, Map<String, String> byFirstName) throws Exception {
		String sheet = getAs(user, sheetUrl() + "?date={d}", date).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> students = JsonPath.read(sheet, "$.students");
		String entries = students.stream()
			.map(s -> "{\"enrollmentId\":%s,\"status\":\"%s\"}".formatted(s.get("enrollmentId"),
					byFirstName.getOrDefault(((String) s.get("fullName")).split(" ")[0], "P")))
			.collect(Collectors.joining(","));
		return "{\"entries\":[" + entries + "]}";
	}

	private ResultActions save(TestUser user, LocalDate date, Map<String, String> statuses) throws Exception {
		return putAs(user, sheetUrl() + "?date={d}", body(principal, date, statuses), date);
	}

	@Test
	void markingASectionAndCorrectingIt() throws Exception {
		getAs(principal, sheetUrl())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value(TODAY.toString()))
			.andExpect(jsonPath("$.sectionLabel").value("Class 5 – A"))
			.andExpect(jsonPath("$.editable").value(true))
			.andExpect(jsonPath("$.marked").value(false))
			.andExpect(jsonPath("$.students.length()").value(3))
			.andExpect(jsonPath("$.students[0].status").doesNotExist())
			.andExpect(jsonPath("$.counts.unmarked").value(3));

		save(principal, TODAY, Map.of("Bala", "A"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.marked").value(true))
			.andExpect(jsonPath("$.markedByName").value("principal of Attendance School"))
			.andExpect(jsonPath("$.counts.present").value(2))
			.andExpect(jsonPath("$.counts.absent").value(1))
			.andExpect(jsonPath("$.counts.percentage").value(66.7));

		save(principal, TODAY, Map.of("Bala", "L", "Chitra", "H"))
			.andExpect(jsonPath("$.counts.present").value(1))
			.andExpect(jsonPath("$.counts.leave").value(1))
			.andExpect(jsonPath("$.counts.halfDay").value(1));

		getAs(principal, "/api/v1/attendance/sections?date={d}", TODAY)
			.andExpect(jsonPath("$[0].sectionId").value(setup.sectionId()))
			.andExpect(jsonPath("$[0].marked").value(true));
	}

	@Test
	void theWholeClassMustBeSaved() throws Exception {
		String full = body(principal, TODAY, Map.of());
		String missingOne = full.replaceFirst("\\{\"enrollmentId\":\\d+,\"status\":\"P\"},", "");
		putAs(principal, sheetUrl(), missingOne).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("class list has changed")));
		putAs(principal, sheetUrl(), "{\"entries\":[{\"enrollmentId\":999999,\"status\":\"P\"}]}")
			.andExpect(status().isBadRequest());
		putAs(principal, sheetUrl(), full.replaceFirst("\"P\"", "\"X\"")).andExpect(status().isBadRequest());
	}

	@Test
	void holidaysOffDaysFutureAndOtherYearsAreBlocked() throws Exception {
		academic.holiday(school, setup.yearId(), TODAY, "Bathukamma");
		getAs(principal, sheetUrl())
			.andExpect(jsonPath("$.editable").value(false))
			.andExpect(jsonPath("$.blockedReason", containsString("Holiday: Bathukamma")));
		putAs(principal, sheetUrl(), "{\"entries\":[{\"enrollmentId\":1,\"status\":\"P\"}]}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("not a school day")));

		LocalDate yesterday = TODAY.minusDays(1);
		fixtures.setWeekOffDays(school, yesterday.getDayOfWeek());
		putAs(principal, sheetUrl() + "?date={d}", "{\"entries\":[{\"enrollmentId\":1,\"status\":\"P\"}]}", yesterday)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("weekly off")));

		putAs(principal, sheetUrl() + "?date={d}", "{\"entries\":[{\"enrollmentId\":1,\"status\":\"P\"}]}",
				TODAY.plusDays(1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Attendance cannot be marked for a future date"));
		getAs(principal, sheetUrl() + "?date={d}", TODAY.minusDays(400))
			.andExpect(jsonPath("$.blockedReason", containsString("outside the academic year")));
	}

	@Test
	void teachersMarkOnlyTheirSectionsAndOnlyToday() throws Exception {
		TestUser classTeacher = fixtures.user(school, "ct", Role.TEACHER);
		TestUser subjectTeacher = fixtures.user(school, "st", Role.TEACHER);
		TestUser otherTeacher = fixtures.user(school, "ot", Role.TEACHER);
		TestUser accountant = fixtures.user(school, "acc", Role.ACCOUNTANT);
		Long sectionB = academic.section(school, setup.yearId(), setup.classId(), "B", classTeacher.id());
		academic.assign(school, setup.sectionId(), setup.subjectId(), subjectTeacher.id());

		save(subjectTeacher, TODAY, Map.of("Asha", "A")).andExpect(status().isOk())
			.andExpect(jsonPath("$.markedByName").value("st of Attendance School"));
		getAs(subjectTeacher, "/api/v1/attendance/sections")
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].sectionId").value(setup.sectionId()));

		// Yesterday: teacher may look but not change; the principal may change.
		LocalDate yesterday = TODAY.minusDays(1);
		getAs(subjectTeacher, sheetUrl() + "?date={d}", yesterday)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.editable").value(false))
			.andExpect(jsonPath("$.blockedReason", containsString("only today")));
		save(subjectTeacher, yesterday, Map.of()).andExpect(status().isForbidden());
		save(principal, yesterday, Map.of()).andExpect(status().isOk());
		TestUser admin = fixtures.user(school, "admin", Role.ADMIN);
		save(admin, yesterday, Map.of("Bala", "A")).andExpect(status().isOk());

		// Not their section: 404, as if it did not exist.
		getAs(otherTeacher, sheetUrl()).andExpect(status().isNotFound());
		save(otherTeacher, TODAY, Map.of()).andExpect(status().isNotFound());
		getAs(otherTeacher, "/api/v1/attendance/sections").andExpect(jsonPath("$.length()").value(0));
		getAs(classTeacher, sheetUrl()).andExpect(status().isNotFound());
		getAs(classTeacher, "/api/v1/attendance/sections/{s}", sectionB).andExpect(status().isOk());

		// Accountants do not take attendance; teachers do not see the school-wide report.
		save(accountant, TODAY, Map.of()).andExpect(status().isForbidden());
		getAs(subjectTeacher, "/api/v1/attendance/today").andExpect(status().isForbidden());
	}

	@Test
	void reports() throws Exception {
		LocalDate yesterday = TODAY.minusDays(1);
		save(principal, yesterday, Map.of("Bala", "A"));
		save(principal, TODAY, Map.of("Bala", "A", "Chitra", "L"));
		YearMonth month = YearMonth.from(TODAY);

		ResultActions register = getAs(principal, sheetUrl() + "/register?month={m}", month.toString())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.days.length()").value(month.lengthOfMonth()))
			.andExpect(jsonPath("$.rows.length()").value(3))
			.andExpect(jsonPath("$.rows[0].fullName").value("Asha Reddy"))
			.andExpect(jsonPath("$.rows[0].statuses[%d]".formatted(TODAY.getDayOfMonth() - 1)).value("P"));
		if (yesterday.getMonth() == TODAY.getMonth()) {
			register.andExpect(jsonPath("$.rows[1].counts.absent").value(2))
				.andExpect(jsonPath("$.rows[1].counts.percentage").value(0.0))
				.andExpect(jsonPath("$.rows[0].counts.percentage").value(100.0));
		}

		getAs(principal, sheetUrl() + "/register.csv?month={m}", month.toString())
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Roll,Name,01 ")))
			.andExpect(content().string(containsString("Asha Reddy")));

		getAs(principal, "/api/v1/attendance/students/{id}?month={m}", chitra, month.toString())
			.andExpect(jsonPath("$.statuses[%d]".formatted(TODAY.getDayOfMonth() - 1)).value("L"))
			.andExpect(jsonPath("$.counts.leave").value(1));

		Long sectionB = academic.section(school, setup.yearId(), setup.classId(), "B", null);
		admitOk(principal, "B1", "Dinesh", sectionB);
		academic.section(school, setup.yearId(), setup.classId(), "C", null); // empty: needs no marking
		getAs(principal, "/api/v1/attendance/today")
			.andExpect(jsonPath("$.enrolled").value(4))
			.andExpect(jsonPath("$.counts.present").value(1))
			.andExpect(jsonPath("$.counts.absent").value(1))
			.andExpect(jsonPath("$.counts.leave").value(1))
			.andExpect(jsonPath("$.counts.unmarked").value(1))
			.andExpect(jsonPath("$.classes[0].className").value("Class 5"))
			.andExpect(jsonPath("$.classes[0].sections").value(2))
			.andExpect(jsonPath("$.classes[0].markedSections").value(1))
			.andExpect(jsonPath("$.unmarkedSections[0].label").value("Class 5 – B"));
	}

	@Test
	void studentsWhoLeftDropOffLaterRegisters() throws Exception {
		postAs(principal, "/api/v1/students/{id}/leave", """
				{"leftOn":"%s","reason":"Moved"}""".formatted(TODAY.minusDays(1)), asha).andExpect(status().isOk());
		getAs(principal, sheetUrl()).andExpect(jsonPath("$.students.length()").value(2));
		getAs(principal, sheetUrl() + "?date={d}", TODAY.minusDays(1)).andExpect(jsonPath("$.students.length()").value(3));
	}

	@Test
	void switchedOffModuleIs404() throws Exception {
		fixtures.setModules(school, new SchoolSettings.Modules(false, true, true, true));
		getAs(principal, sheetUrl()).andExpect(status().isNotFound());
		getAs(principal, "/api/v1/attendance/today").andExpect(status().isNotFound());
	}

	@Test
	void schoolACannotReachSchoolBsAttendance() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		fixtures.setWeekOffDays(s.b());
		Setup b = academic.todaySetup(s.b());
		long bStudent = admitOk(s.b().principal(), "B-1", "Lalitha", b.sectionId());
		academic.todaySetup(s.a());
		TestUser a = s.a().principal();
		String bUrl = "/api/v1/attendance/sections/" + b.sectionId();

		getAs(a, bUrl).andExpect(status().isNotFound());
		putAs(a, bUrl, "{\"entries\":[{\"enrollmentId\":1,\"status\":\"A\"}]}").andExpect(status().isNotFound());
		getAs(a, bUrl + "/register?month={m}", YearMonth.from(TODAY).toString()).andExpect(status().isNotFound());
		getAs(a, bUrl + "/register.csv?month={m}", YearMonth.from(TODAY).toString()).andExpect(status().isNotFound());
		getAs(a, "/api/v1/attendance/students/{id}?month={m}", bStudent, YearMonth.from(TODAY).toString())
			.andExpect(status().isNotFound());
		getAs(a, "/api/v1/attendance/sections").andExpect(jsonPath("$[?(@.sectionId == %d)]".formatted(b.sectionId())).isEmpty());
		getAs(a, "/api/v1/attendance/today").andExpect(jsonPath("$.enrolled").value(0));
		getAs(s.b().principal(), bUrl).andExpect(jsonPath("$.students.length()").value(1));
	}

}
