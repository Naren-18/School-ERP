package com.schoolerp.certificate.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.schoolerp.common.money.AmountInWords;
import com.schoolerp.common.pdf.HtmlPdf;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Guardian;
import com.schoolerp.student.entity.Student;

import lombok.RequiredArgsConstructor;

/** Builds the {@code school.*} and {@code student.*} placeholder values (see {@link TemplateFields}). */
@Component
@RequiredArgsConstructor
public class TemplateValues {

	public static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private static final String[] ORDINALS = { "", "First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh",
			"Eighth", "Ninth", "Tenth", "Eleventh", "Twelfth", "Thirteenth", "Fourteenth", "Fifteenth", "Sixteenth",
			"Seventeenth", "Eighteenth", "Nineteenth", "Twentieth", "Twenty First", "Twenty Second", "Twenty Third",
			"Twenty Fourth", "Twenty Fifth", "Twenty Sixth", "Twenty Seventh", "Twenty Eighth", "Twenty Ninth",
			"Thirtieth", "Thirty First" };

	private final SchoolService schoolService;

	public Map<String, String> school(School school) {
		Map<String, String> v = new LinkedHashMap<>();
		v.put("school.name", school.getName());
		v.put("school.address", nz(school.getAddress()));
		v.put("school.phone", nz(school.getPhone()));
		v.put("school.email", nz(school.getEmail()));
		v.put("school.board", school.getBoard() == null ? "" : switch (school.getBoard()) {
			case STATE -> "State Board";
			case CBSE -> "CBSE";
			case ICSE -> "ICSE";
			case OTHER -> "";
		});
		return v;
	}

	/** {@code school.logo} block: the uploaded logo, or nothing. */
	public String logoBlock() {
		return schoolService.currentLogo().map(l -> HtmlPdf.img(l.content(), l.contentType(), "logo")).orElse("");
	}

	/**
	 * @param enrollment the class to print (usually the current or latest one); may be null
	 */
	public Map<String, String> student(StudentDto.Detail s, StudentDto.EnrollmentView enrollment) {
		Map<String, String> v = new LinkedHashMap<>();
		v.put("student.fullName", s.fullName());
		v.put("student.admissionNo", s.admissionNo());
		v.put("student.admissionDate", s.admissionDate().format(DMY));
		v.put("student.admittedClass", s.enrollments().stream()
			.min(Comparator.comparing(StudentDto.EnrollmentView::joinedOn))
			.map(StudentDto.EnrollmentView::className)
			.orElse(""));
		v.put("student.dateOfBirth", s.dateOfBirth().format(DMY));
		v.put("student.dateOfBirthInWords", dateInWords(s.dateOfBirth()));
		v.put("student.gender", s.gender() == null ? "" : capitalize(s.gender().name()));
		v.put("student.sonDaughter", pick(s.gender(), "son", "daughter", "child"));
		v.put("student.heShe", pick(s.gender(), "he", "she", "they"));
		v.put("student.hisHer", pick(s.gender(), "his", "her", "their"));
		v.put("student.fatherName", guardian(s.guardians(), Guardian.Relation.FATHER));
		v.put("student.motherName", guardian(s.guardians(), Guardian.Relation.MOTHER));
		v.put("student.guardianName", s.guardians().stream()
			.filter(StudentDto.GuardianView::primary)
			.findFirst()
			.or(() -> s.guardians().stream().findFirst())
			.map(StudentDto.GuardianView::fullName)
			.orElse(""));
		v.put("student.class", enrollment == null ? "" : enrollment.className() + " – " + enrollment.sectionName());
		v.put("student.className", enrollment == null ? "" : enrollment.className());
		v.put("student.sectionName", enrollment == null ? "" : enrollment.sectionName());
		v.put("student.rollNo", enrollment == null || enrollment.rollNo() == null ? "" : enrollment.rollNo().toString());
		v.put("student.academicYear", enrollment == null ? "" : enrollment.academicYearName());
		v.put("student.motherTongue", nz(s.motherTongue()));
		v.put("student.religion", nz(s.religion()));
		v.put("student.caste", nz(s.caste()));
		v.put("student.socialCategory", s.socialCategory() == null ? "" : s.socialCategory().name().replace('_', '-'));
		v.put("student.identificationMarks", nz(s.identificationMarks()));
		v.put("student.address", nz(s.address()));
		return v;
	}

	/** The enrollment to print: the current one, else the most recent. */
	public static StudentDto.EnrollmentView classOf(StudentDto.Detail s) {
		return s.currentEnrollment() != null ? s.currentEnrollment()
				: s.enrollments().stream().max(Comparator.comparing(StudentDto.EnrollmentView::joinedOn)).orElse(null);
	}

	/** 03-02-2016 → "Third February Two Thousand Sixteen". */
	public static String dateInWords(LocalDate d) {
		return ORDINALS[d.getDayOfMonth()] + " "
				+ capitalize(d.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)) + " "
				+ AmountInWords.number(d.getYear());
	}

	private static String guardian(List<StudentDto.GuardianView> guardians, Guardian.Relation relation) {
		return guardians.stream().filter(g -> g.relation() == relation).findFirst().map(StudentDto.GuardianView::fullName).orElse("");
	}

	private static String pick(Student.Gender g, String male, String female, String other) {
		return g == Student.Gender.MALE ? male : g == Student.Gender.FEMALE ? female : other;
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ENGLISH) + s.substring(1).toLowerCase(Locale.ENGLISH);
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

}
