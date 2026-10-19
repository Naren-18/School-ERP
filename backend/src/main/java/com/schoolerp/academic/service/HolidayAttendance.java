package com.schoolerp.academic.service;

import java.time.LocalDate;

/**
 * Attendance already marked on a date that is becoming a holiday. Implemented
 * by the attendance module, so the academic module does not depend on it.
 */
public interface HolidayAttendance {

	record Marked(int students, int sections) {
	}

	/** Attendance rows on the date in the current school. */
	Marked markedOn(LocalDate date);

	/** Removes the current school's attendance on the date; returns how many rows. */
	int clear(LocalDate date);

}
