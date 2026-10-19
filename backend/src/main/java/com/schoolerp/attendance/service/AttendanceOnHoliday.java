package com.schoolerp.attendance.service;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.schoolerp.academic.service.HolidayAttendance;
import com.schoolerp.attendance.repository.AttendanceRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Lets the holiday screen warn about, and clear, attendance already marked that day. */
@Component
@RequiredArgsConstructor
class AttendanceOnHoliday implements HolidayAttendance {

	private final AttendanceRepository attendance;

	@Override
	public Marked markedOn(LocalDate date) {
		Long schoolId = TenantContext.require();
		return new Marked((int) attendance.countBySchoolIdAndDate(schoolId, date),
				attendance.countMarkedSections(schoolId, date));
	}

	@Override
	public int clear(LocalDate date) {
		return attendance.deleteOnDate(TenantContext.require(), date);
	}

}
