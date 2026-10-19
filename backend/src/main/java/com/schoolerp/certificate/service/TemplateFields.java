package com.schoolerp.certificate.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.schoolerp.certificate.entity.TemplateType;

/**
 * The placeholders each template type may use, with a description for the
 * editor. Values are {@code {{key}}}; blocks (HTML built by the application)
 * are {@code {{{key}}}}.
 */
public final class TemplateFields {

	public record Fields(Map<String, String> values, Map<String, String> blocks) {
	}

	private TemplateFields() {
	}

	private static final Map<String, String> SCHOOL = ordered(
			"school.name", "School name",
			"school.address", "School address",
			"school.phone", "School phone",
			"school.email", "School email",
			"school.board", "Board (CBSE, State, ...)");

	private static final Map<String, String> STUDENT = ordered(
			"student.fullName", "Student's full name",
			"student.admissionNo", "Admission number",
			"student.admissionDate", "Date of admission (DD-MM-YYYY)",
			"student.admittedClass", "Class first admitted to",
			"student.dateOfBirth", "Date of birth (DD-MM-YYYY)",
			"student.dateOfBirthInWords", "Date of birth in words",
			"student.gender", "Male / Female / Other",
			"student.sonDaughter", "son / daughter / child",
			"student.heShe", "he / she / they",
			"student.hisHer", "his / her / their",
			"student.fatherName", "Father's name",
			"student.motherName", "Mother's name",
			"student.guardianName", "Primary parent or guardian",
			"student.class", "Class and section, e.g. Class 5 – A",
			"student.className", "Class, e.g. Class 5",
			"student.sectionName", "Section, e.g. A",
			"student.rollNo", "Roll number",
			"student.academicYear", "Academic year, e.g. 2026-27",
			"student.motherTongue", "Mother tongue",
			"student.religion", "Religion",
			"student.caste", "Caste",
			"student.socialCategory", "Social category (BC-A, SC, ...)",
			"student.identificationMarks", "Identification marks",
			"student.address", "Address");

	private static final Map<String, String> CERTIFICATE = ordered(
			"certificate.serialNo", "Serial number, e.g. SSV/TC/0001",
			"certificate.issuedOn", "Date of issue (DD-MM-YYYY)",
			"certificate.purpose", "Purpose given when issuing");

	private static final Map<String, String> TC = ordered(
			"tc.leavingDate", "Date of leaving (DD-MM-YYYY)",
			"tc.reason", "Reason for leaving",
			"tc.conduct", "Conduct, e.g. Good",
			"tc.promotion", "Whether qualified for promotion",
			"tc.remarks", "Any other remarks");

	private static final Map<String, String> REPORT = ordered(
			"report.title", "e.g. Progress Report – FA1 (2026-27)",
			"report.examName", "Exam name, or Annual",
			"result.percent", "Overall percentage",
			"result.grade", "Overall grade",
			"result.status", "PASS / NOT PASSED / Incomplete",
			"result.rank", "Rank (empty when ranks are off)",
			"attendance.summary", "e.g. 182 of 190 days (95.8%); empty when attendance is off",
			"teacher.remark", "Class teacher's remark",
			"classTeacher.name", "Class teacher's name");

	private static final Map<String, String> LOGO = ordered("school.logo", "The school logo (when uploaded)");

	private static final Map<String, String> REPORT_BLOCKS = ordered(
			"school.logo", "The school logo (when uploaded)",
			"marks.table", "Subjects with marks and grades (or every exam, on the annual card)",
			"coScholastic.table", "Co-scholastic areas and grades (empty when none are set up)",
			"result.summary", "Percentage, grade, result and rank in a row",
			"attendance.line", "\"Attendance: …\" line; empty when attendance is not shown",
			"remark.line", "\"Class teacher's remark: …\" line; empty when there is no remark");

	public static Fields of(TemplateType type) {
		return switch (type) {
			case BONAFIDE -> new Fields(merge(SCHOOL, STUDENT, CERTIFICATE), LOGO);
			case TC -> new Fields(merge(SCHOOL, STUDENT, CERTIFICATE, TC), LOGO);
			case REPORT_CARD -> new Fields(merge(SCHOOL, STUDENT, REPORT), REPORT_BLOCKS);
		};
	}

	private static Map<String, String> ordered(String... keyThenDescription) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i < keyThenDescription.length; i += 2) {
			m.put(keyThenDescription[i], keyThenDescription[i + 1]);
		}
		return m;
	}

	@SafeVarargs
	private static Map<String, String> merge(Map<String, String>... maps) {
		Map<String, String> m = new LinkedHashMap<>();
		for (Map<String, String> part : maps) {
			m.putAll(part);
		}
		return m;
	}

}
