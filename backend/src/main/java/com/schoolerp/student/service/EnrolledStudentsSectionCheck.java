package com.schoolerp.student.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.SectionUsageCheck;
import com.schoolerp.student.entity.Enrollment;
import com.schoolerp.student.repository.EnrollmentRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** A section with any enrollment (current or past) cannot be deleted; say how many and what to do. */
@Component
@RequiredArgsConstructor
class EnrolledStudentsSectionCheck implements SectionUsageCheck {

	private final EnrollmentRepository enrollments;

	@Override
	public Optional<String> blockingReason(Section section, String label) {
		Long schoolId = TenantContext.require();
		long total = enrollments.countBySchoolIdAndSectionId(schoolId, section.getId());
		if (total == 0) {
			return Optional.empty();
		}
		long active = enrollments.countBySchoolIdAndSectionIdAndStatus(schoolId, section.getId(),
				Enrollment.Status.ACTIVE);
		if (active > 0) {
			return Optional.of(plural(active, "student is", "students are") + " enrolled in " + label
					+ ". Move them to another section before deleting it.");
		}
		return Optional.of(label + " has class records for " + plural(total, "student", "students")
				+ " (promoted, detained or left), so it cannot be deleted.");
	}

	private static String plural(long n, String one, String many) {
		return n + " " + (n == 1 ? one : many);
	}

}
