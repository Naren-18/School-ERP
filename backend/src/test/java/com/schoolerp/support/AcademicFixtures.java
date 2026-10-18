package com.schoolerp.support;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestComponent;

import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.entity.ClassSubject;
import com.schoolerp.academic.entity.Holiday;
import com.schoolerp.academic.entity.SchoolClass;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.entity.Subject;
import com.schoolerp.academic.entity.TeacherAssignment;
import com.schoolerp.academic.repository.AcademicYearRepository;
import com.schoolerp.academic.repository.ClassSubjectRepository;
import com.schoolerp.academic.repository.HolidayRepository;
import com.schoolerp.academic.repository.SchoolClassRepository;
import com.schoolerp.academic.repository.SectionRepository;
import com.schoolerp.academic.repository.SubjectRepository;
import com.schoolerp.academic.repository.TeacherAssignmentRepository;
import com.schoolerp.support.TenantFixtures.TestSchool;

import lombok.RequiredArgsConstructor;

/**
 * Builds a school's academic structure directly (no HTTP), for tests that need
 * existing data — typically School B's records that School A must not reach.
 */
@TestComponent
@RequiredArgsConstructor
public class AcademicFixtures {

	private final TenantFixtures tenants;

	private final AcademicYearRepository years;

	private final SchoolClassRepository classes;

	private final SectionRepository sections;

	private final SubjectRepository subjects;

	private final ClassSubjectRepository classSubjects;

	private final TeacherAssignmentRepository assignments;

	private final HolidayRepository holidays;

	/** One school's typical setup: current year, a class, a section, a subject mapped to it. */
	public record Setup(Long yearId, Long classId, Long sectionId, Long subjectId) {
	}

	public Setup basicSetup(TestSchool school) {
		Long yearId = year(school, "2026-27", true);
		Long classId = schoolClass(school, "Class 5", 5);
		Long sectionId = section(school, yearId, classId, "A", null);
		Long subjectId = subject(school, "Mathematics");
		mapSubject(school, yearId, classId, subjectId);
		return new Setup(yearId, classId, sectionId, subjectId);
	}

	/** Like {@link #basicSetup} but the current year spans today (for date-sensitive tests such as attendance). */
	public Setup todaySetup(TestSchool school) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		Long yearId = tenants.inSchool(school, () -> {
			AcademicYear y = new AcademicYear();
			y.setName("Y" + today);
			y.setStartDate(today.minusDays(120));
			y.setEndDate(today.plusDays(200));
			y.setCurrent(true);
			return years.save(y).getId();
		});
		Long classId = schoolClass(school, "Class 5", 5);
		Long sectionId = section(school, yearId, classId, "A", null);
		Long subjectId = subject(school, "Mathematics");
		mapSubject(school, yearId, classId, subjectId);
		return new Setup(yearId, classId, sectionId, subjectId);
	}

	public Long year(TestSchool school, String name, boolean current) {
		int startYear = Integer.parseInt(name.substring(0, 4));
		return tenants.inSchool(school, () -> {
			AcademicYear y = new AcademicYear();
			y.setName(name);
			y.setStartDate(LocalDate.of(startYear, 6, 1));
			y.setEndDate(LocalDate.of(startYear + 1, 4, 30));
			y.setCurrent(current);
			return years.save(y).getId();
		});
	}

	public Long schoolClass(TestSchool school, String name, int order) {
		return tenants.inSchool(school, () -> {
			SchoolClass c = new SchoolClass();
			c.setName(name);
			c.setDisplayOrder(order);
			return classes.save(c).getId();
		});
	}

	public Long section(TestSchool school, Long yearId, Long classId, String name, Long classTeacherId) {
		return tenants.inSchool(school, () -> {
			Section s = new Section();
			s.setAcademicYearId(yearId);
			s.setClassId(classId);
			s.setName(name);
			s.setClassTeacherId(classTeacherId);
			return sections.save(s).getId();
		});
	}

	public Long subject(TestSchool school, String name) {
		return tenants.inSchool(school, () -> {
			Subject s = new Subject();
			s.setName(name);
			return subjects.save(s).getId();
		});
	}

	public Long mapSubject(TestSchool school, Long yearId, Long classId, Long subjectId) {
		return tenants.inSchool(school, () -> {
			ClassSubject cs = new ClassSubject();
			cs.setAcademicYearId(yearId);
			cs.setClassId(classId);
			cs.setSubjectId(subjectId);
			cs.setDisplayOrder(1);
			return classSubjects.save(cs).getId();
		});
	}

	public Long assign(TestSchool school, Long sectionId, Long subjectId, Long teacherId) {
		return tenants.inSchool(school, () -> {
			TeacherAssignment a = new TeacherAssignment();
			a.setSectionId(sectionId);
			a.setSubjectId(subjectId);
			a.setTeacherId(teacherId);
			return assignments.save(a).getId();
		});
	}

	public Long holiday(TestSchool school, Long yearId, LocalDate date, String description) {
		return tenants.inSchool(school, () -> {
			Holiday h = new Holiday();
			h.setAcademicYearId(yearId);
			h.setDate(date);
			h.setDescription(description);
			return holidays.save(h).getId();
		});
	}

}
