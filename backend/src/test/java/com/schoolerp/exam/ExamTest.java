package com.schoolerp.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

class ExamTest extends StudentTestSupport {

	private TestSchool school;

	private Setup setup;

	private TestUser p;

	private TestUser mathsTeacher;

	private long scale;

	private long exam;

	private long maths;

	private long english;

	private long asha;

	@BeforeEach
	void examWithPapers() throws Exception {
		school = fixtures.school("Exam School");
		setup = academic.todaySetup(school);
		Long englishSubject = academic.subject(school, "English");
		academic.mapSubject(school, setup.yearId(), setup.classId(), englishSubject);
		p = school.principal();
		mathsTeacher = fixtures.user(school, "mt", Role.TEACHER);
		academic.assign(school, setup.sectionId(), setup.subjectId(), mathsTeacher.id());
		asha = admitOk(p, "E1", "Asha", setup.sectionId());
		admitOk(p, "E2", "Bala", setup.sectionId());
		admitOk(p, "E3", "Chitra", setup.sectionId());

		scale = idOf(postAs(p, "/api/v1/exams/grading-scales/presets/CBSE_9_POINT", null).andExpect(status().isCreated()));
		exam = createExam("FA1", "20");
		postAs(p, "/api/v1/exams/{id}/papers/apply", "{\"maxMarks\":50,\"passMarks\":17.5}", exam)
			.andExpect(jsonPath("$.created").value(2));
		postAs(p, "/api/v1/exams/{id}/papers/apply", "{\"maxMarks\":50}", exam).andExpect(jsonPath("$.created").value(0));
		maths = paperId(exam, "Mathematics");
		english = paperId(exam, "English");
	}

	private long createExam(String name, String weightage) throws Exception {
		return idOf(postAs(p, "/api/v1/exams", """
				{"academicYearId":%d,"name":"%s","gradingScaleId":%d,"displayOrder":1,"weightage":%s}"""
			.formatted(setup.yearId(), name, scale, weightage)).andExpect(status().isCreated()));
	}

	private long paperId(long examId, String subject) throws Exception {
		String json = getAs(p, "/api/v1/exams/{id}/papers", examId).andReturn().getResponse().getContentAsString();
		return JsonPath.<List<Number>>read(json, "$[?(@.subjectName == '" + subject + "')].id").getFirst().longValue();
	}

	private String marksUrl(long paper) {
		return "/api/v1/exams/papers/" + paper + "/marks?sectionId=" + setup.sectionId();
	}

	/** values by first name: a number, "AB" for absent, or "" to clear. */
	private ResultActions enter(TestUser user, long paper, Map<String, String> byName) throws Exception {
		String sheet = getAs(p, marksUrl(paper)).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> rows = JsonPath.read(sheet, "$.rows");
		String entries = rows.stream()
			.filter(r -> byName.containsKey(((String) r.get("fullName")).split(" ")[0]))
			.map(r -> {
				String v = byName.get(((String) r.get("fullName")).split(" ")[0]);
				return "{\"enrollmentId\":%s,\"marks\":%s,\"absent\":%s}".formatted(r.get("enrollmentId"),
						v.equals("AB") || v.isEmpty() ? "null" : v, v.equals("AB"));
			})
			.collect(Collectors.joining(","));
		return putAs(user, marksUrl(paper), "{\"entries\":[" + entries + "]}");
	}

	@Test
	void teacherEntersOnlyTheirSubjectAndResultsAreComputed() throws Exception {
		getAs(mathsTeacher, "/api/v1/exams/{id}/entry-options", exam)
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].subjectName").value("Mathematics"))
			.andExpect(jsonPath("$[0].students").value(3));

		enter(mathsTeacher, maths, Map.of("Asha", "45.5", "Bala", "17", "Chitra", "AB"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows[0].marks").value(45.5))
			.andExpect(jsonPath("$.rows[2].absent").value(true));
		enter(mathsTeacher, english, Map.of("Asha", "40")).andExpect(status().isNotFound());
		TestUser other = fixtures.user(school, "other", Role.TEACHER);
		enter(other, maths, Map.of("Asha", "1")).andExpect(status().isNotFound());
		getAs(other, "/api/v1/exams/{id}/entry-options", exam).andExpect(jsonPath("$.length()").value(0));
		getAs(mathsTeacher, "/api/v1/exams/result-sections").andExpect(jsonPath("$.length()").value(1));
		getAs(other, "/api/v1/exams/result-sections").andExpect(jsonPath("$.length()").value(0));
		getAs(other, "/api/v1/exams/{id}/results?sectionId={s}", exam, setup.sectionId()).andExpect(status().isNotFound());
		enter(p, maths, Map.of("Asha", "51")).andExpect(status().isBadRequest());

		// English not yet entered: results are incomplete, not zero.
		getAs(p, "/api/v1/exams/{id}/results?sectionId={s}", exam, setup.sectionId())
			.andExpect(jsonPath("$.rows[0].result.complete").value(false))
			.andExpect(jsonPath("$.rows[0].result.passed").doesNotExist())
			.andExpect(jsonPath("$.rows[0].result.percent").value(91.0));

		enter(p, english, Map.of("Asha", "40", "Bala", "30", "Chitra", "25")).andExpect(status().isOk());
		getAs(p, "/api/v1/exams/{id}/results?sectionId={s}", exam, setup.sectionId())
			.andExpect(jsonPath("$.subjects.length()").value(2))
			.andExpect(jsonPath("$.rows[0].fullName").value("Asha Reddy"))
			.andExpect(jsonPath("$.rows[0].result.total").value(85.5))
			.andExpect(jsonPath("$.rows[0].result.percent").value(85.5))
			.andExpect(jsonPath("$.rows[0].result.grade").value("A2"))
			.andExpect(jsonPath("$.rows[0].result.passed").value(true))
			.andExpect(jsonPath("$.rows[0].rank").value(1))
			.andExpect(jsonPath("$.rows[1].result.passed").value(false)) // Bala: 17 < 17.5 in Maths
			.andExpect(jsonPath("$.rows[2].result.passed").value(false)) // Chitra: absent
			.andExpect(jsonPath("$.rows[2].result.subjects[?(@.subjectName == 'Mathematics')].absent").value(true));

		// Clearing a mark puts it back to "not entered".
		enter(p, english, Map.of("Chitra", "")).andExpect(jsonPath("$.rows[2].marks").doesNotExist());
	}

	@Test
	void publishingLocksMarksAndEnablesReportCards() throws Exception {
		enter(p, maths, Map.of("Asha", "45", "Bala", "30", "Chitra", "20"));
		enter(p, english, Map.of("Asha", "40", "Bala", "30", "Chitra", "20"));
		getAs(p, "/api/v1/exams/report-cards.pdf?sectionId={s}&examId={e}", setup.sectionId(), exam)
			.andExpect(status().isConflict());

		postAs(p, "/api/v1/exams/{id}/publish", null, exam).andExpect(jsonPath("$.published").value(true));
		enter(mathsTeacher, maths, Map.of("Asha", "1")).andExpect(status().isConflict());
		getAs(mathsTeacher, marksUrl(maths)).andExpect(jsonPath("$.editable").value(false))
			.andExpect(jsonPath("$.lockedReason", containsString("published")));
		putAs(p, "/api/v1/exams/{id}/papers", """
				{"classId":%d,"subjectId":%d,"maxMarks":100}""".formatted(setup.classId(), setup.subjectId()), exam)
			.andExpect(status().isConflict());

		byte[] all = getAs(p, "/api/v1/exams/report-cards.pdf?sectionId={s}&examId={e}", setup.sectionId(), exam)
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(all, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		byte[] one = getAs(p, "/api/v1/exams/report-cards.pdf?sectionId={s}&examId={e}&studentId={st}", setup.sectionId(),
				exam, asha).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		assertThat(one.length).isLessThan(all.length);
		// Year card (all published exams + weighted final); a class teacher may print it too.
		getAs(p, "/api/v1/exams/report-cards.pdf?sectionId={s}", setup.sectionId()).andExpect(status().isOk());

		postAs(p, "/api/v1/exams/{id}/unpublish", null, exam).andExpect(jsonPath("$.published").value(false));
		enter(mathsTeacher, maths, Map.of("Asha", "46")).andExpect(status().isOk());
	}

	@Test
	void studentYearAndWeightedFinal() throws Exception {
		long sa = createExam("SA1", "80");
		postAs(p, "/api/v1/exams/{id}/papers/apply", "{\"maxMarks\":100,\"passMarks\":35}", sa);
		enter(p, maths, Map.of("Asha", "40"));
		enter(p, english, Map.of("Asha", "40")); // FA1 = 80%
		long saMaths = paperId(sa, "Mathematics");
		long saEnglish = paperId(sa, "English");
		enter(p, saMaths, Map.of("Asha", "60"));
		enter(p, saEnglish, Map.of("Asha", "60")); // SA1 = 60%
		postAs(p, "/api/v1/exams/{id}/publish", null, exam);

		// Only published exams count.
		getAs(p, "/api/v1/exams/students/{id}/results", asha)
			.andExpect(jsonPath("$.exams.length()").value(1))
			.andExpect(jsonPath("$.finalResult.percent").value(80.0));
		postAs(p, "/api/v1/exams/{id}/publish", null, sa);
		getAs(p, "/api/v1/exams/students/{id}/results", asha)
			.andExpect(jsonPath("$.exams.length()").value(2))
			.andExpect(jsonPath("$.finalResult.percent").value(64.0)) // 0.2 × 80 + 0.8 × 60
			.andExpect(jsonPath("$.finalResult.grade").value("B2"))
			.andExpect(jsonPath("$.finalResult.passed").value(true));
	}

	private static String pdfText(byte[] pdf) throws Exception {
		try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
			return new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
		}
	}

	@Test
	void remarksAndCoScholasticGradesPrintThroughTheTemplate() throws Exception {
		long art = idOf(postAs(p, "/api/v1/exams/co-scholastic-areas", "{\"name\":\"Art Education\",\"displayOrder\":1}")
			.andExpect(status().isCreated()));
		postAs(p, "/api/v1/exams/co-scholastic-areas", "{\"name\":\"art education\"}").andExpect(status().isConflict());
		postAs(mathsTeacher, "/api/v1/exams/co-scholastic-areas", "{\"name\":\"Sports\"}").andExpect(status().isForbidden());

		// Section B has a class teacher; only they (and principal/admin) write its remarks.
		TestUser classTeacher = fixtures.user(school, "ct", Role.TEACHER);
		long sectionB = academic.section(school, setup.yearId(), setup.classId(), "B", classTeacher.id());
		admitOk(p, "E9", "Devi", sectionB);
		String notesUrl = "/api/v1/exams/" + exam + "/notes?sectionId=" + sectionB;
		String sheet = getAs(classTeacher, notesUrl).andExpect(status().isOk())
			.andExpect(jsonPath("$.areas.length()").value(1))
			.andExpect(jsonPath("$.editable").value(true))
			.andReturn().getResponse().getContentAsString();
		long devi = JsonPath.<Number>read(sheet, "$.rows[0].enrollmentId").longValue();
		String body = """
				{"entries":[{"enrollmentId":%d,"remark":"Reads well; neat handwriting.","grades":{"%d":"a"}}]}"""
			.formatted(devi, art);
		putAs(mathsTeacher, notesUrl, body).andExpect(status().isNotFound());
		getAs(classTeacher, "/api/v1/exams/" + exam + "/notes?sectionId=" + setup.sectionId()).andExpect(status().isNotFound());
		putAs(classTeacher, notesUrl, body).andExpect(status().isOk())
			.andExpect(jsonPath("$.rows[0].remark").value("Reads well; neat handwriting."))
			.andExpect(jsonPath("$.rows[0].grades['" + art + "']").value("A"));

		String marks = "/api/v1/exams/papers/%d/marks?sectionId=" + sectionB;
		putAs(p, marks.formatted(maths), "{\"entries\":[{\"enrollmentId\":%d,\"marks\":40,\"absent\":false}]}".formatted(devi));
		putAs(p, marks.formatted(english), "{\"entries\":[{\"enrollmentId\":%d,\"marks\":35,\"absent\":false}]}".formatted(devi));
		postAs(p, "/api/v1/exams/{id}/publish", null, exam);
		putAs(classTeacher, notesUrl, body).andExpect(status().isConflict());

		String card = pdfText(getAs(classTeacher, "/api/v1/exams/report-cards.pdf?sectionId={s}&examId={e}", sectionB, exam)
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
		assertThat(card).contains("Devi Reddy", "Mathematics", "Art Education", "Reads well; neat handwriting.");
		String annual = pdfText(getAs(p, "/api/v1/exams/report-cards.pdf?sectionId={s}", sectionB)
			.andReturn().getResponse().getContentAsByteArray());
		assertThat(annual).contains("Annual Progress Report", "Reads well; neat handwriting.");

		// The school's own report card template.
		getAs(p, "/api/v1/exams/report-card-template").andExpect(jsonPath("$.custom").value(false))
			.andExpect(jsonPath("$.blocks['coScholastic.table']").exists());
		putAs(p, "/api/v1/exams/report-card-template", "{\"html\":\"<p>{{marks.table}}</p>\"}")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("three braces")));
		putAs(p, "/api/v1/exams/report-card-template",
				"{\"html\":\"<h2>Our card for {{student.fullName}}</h2>{{{marks.table}}}<p>{{teacher.remark}}</p>\"}")
			.andExpect(status().isOk()).andExpect(jsonPath("$.custom").value(true));
		assertThat(pdfText(getAs(p, "/api/v1/exams/report-cards.pdf?sectionId={s}&examId={e}", sectionB, exam)
			.andReturn().getResponse().getContentAsByteArray())).contains("Our card for Devi Reddy", "Reads well");
		postAs(p, "/api/v1/exams/report-card-template/preview", "{\"html\":\"<p>{{student.fullName}}</p>\"}")
			.andExpect(status().isOk());
		getAs(mathsTeacher, "/api/v1/exams/report-card-template").andExpect(status().isForbidden());
		deleteAs(p, "/api/v1/exams/report-card-template").andExpect(jsonPath("$.custom").value(false));

		// Another school sees none of it.
		TestUser other = fixtures.twoSchools().a().principal();
		getAs(other, "/api/v1/exams/co-scholastic-areas").andExpect(jsonPath("$.length()").value(0));
		getAs(other, notesUrl).andExpect(status().isNotFound());
		deleteAs(other, "/api/v1/exams/co-scholastic-areas/{id}", art).andExpect(status().isNotFound());
	}

	@Test
	void gradingScaleRulesAndRoles() throws Exception {
		postAs(p, "/api/v1/exams/grading-scales/presets/CBSE_9_POINT", null).andExpect(status().isConflict());
		postAs(p, "/api/v1/exams/grading-scales", """
				{"name":"Bad","bands":[{"minPercent":40,"maxPercent":100,"grade":"P"}]}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("start at 0%")));
		deleteAs(p, "/api/v1/exams/grading-scales/{id}", scale).andExpect(status().isConflict());

		TestUser accountant = fixtures.user(school, "acc", Role.ACCOUNTANT);
		getAs(accountant, "/api/v1/exams").andExpect(status().isForbidden());
		postAs(mathsTeacher, "/api/v1/exams/{id}/publish", null, exam).andExpect(status().isForbidden());

		fixtures.setModules(school, new SchoolSettings.Modules(true, true, false, true));
		getAs(p, "/api/v1/exams").andExpect(status().isNotFound());
	}

	@Test
	void schoolACannotReachSchoolBsExams() throws Exception {
		TwoSchools s = fixtures.twoSchools();
		Setup a = academic.todaySetup(s.a());
		TestUser aP = s.a().principal();
		String bSheet = marksUrl(maths);

		getAs(aP, "/api/v1/exams").andExpect(jsonPath("$.length()").value(0));
		getAs(aP, "/api/v1/exams/grading-scales").andExpect(jsonPath("$.length()").value(0));
		getAs(aP, "/api/v1/exams/{id}", exam).andExpect(status().isNotFound());
		postAs(aP, "/api/v1/exams/{id}/publish", null, exam).andExpect(status().isNotFound());
		deleteAs(aP, "/api/v1/exams/{id}", exam).andExpect(status().isNotFound());
		getAs(aP, "/api/v1/exams/{id}/papers", exam).andExpect(status().isNotFound());
		getAs(aP, bSheet).andExpect(status().isNotFound());
		putAs(aP, bSheet, "{\"entries\":[]}").andExpect(status().isNotFound());
		getAs(aP, "/api/v1/exams/{id}/results?sectionId={s}", exam, a.sectionId()).andExpect(status().isNotFound());
		getAs(aP, "/api/v1/exams/students/{id}/results", asha).andExpect(status().isNotFound());
		getAs(aP, "/api/v1/exams/report-cards.pdf?sectionId={s}", setup.sectionId()).andExpect(status().isNotFound());
		postAs(aP, "/api/v1/exams", """
				{"academicYearId":%d,"name":"FA1","gradingScaleId":%d}""".formatted(a.yearId(), scale))
			.andExpect(status().isNotFound());
		deleteAs(aP, "/api/v1/exams/grading-scales/{id}", scale).andExpect(status().isNotFound());

		getAs(p, "/api/v1/exams").andExpect(jsonPath("$.length()").value(1));
	}

}
