package com.schoolerp.student.service;

/**
 * Published (inside the enrolling transaction) whenever a student gets a new
 * enrollment. The fees module (phase 5) listens to create that student's dues.
 */
public record StudentEnrolledEvent(Long schoolId, Long enrollmentId, Long sectionId, Long academicYearId) {
}
