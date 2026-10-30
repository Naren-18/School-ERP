package com.schoolerp.attendance.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.schoolerp.attendance.dto.AttendanceDto;
import com.schoolerp.attendance.entity.Attendance;
import com.schoolerp.common.error.ApiException;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.notification.dto.NotificationDto;
import com.schoolerp.notification.entity.MessageType;
import com.schoolerp.notification.service.NotificationService;
import com.schoolerp.school.service.SchoolService;

import lombok.RequiredArgsConstructor;

/**
 * WhatsApp absence alerts for a section's day: the students marked absent, and
 * sending to the ones chosen on screen. Same section rules as attendance; a
 * teacher sends only for today.
 */
@Service
@RequiredArgsConstructor
public class AbsenceAlertService {

	private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private final AttendanceService attendanceService;

	private final AttendanceAccess access;

	private final NotificationService notifications;

	private final SchoolService schoolService;

	private final Clock clock;

	/** The students marked absent on the day, with the last alert tried for each. */
	public AttendanceDto.AbsenceAlerts alerts(Long sectionId, LocalDate date) {
		List<AttendanceDto.SheetStudent> absent = absentees(attendanceService.sheet(sectionId, date));
		Map<Long, NotificationDto.History> history = notifications.history(MessageType.ABSENCE, date, null,
				absent.stream().map(AttendanceDto.SheetStudent::studentId).toList());
		return new AttendanceDto.AbsenceAlerts(notifications.isEnabled(MessageType.ABSENCE),
				notifications.status().connected(), absent.stream().map(s -> {
					NotificationDto.History h = history.get(s.studentId());
					return new AttendanceDto.AbsentStudent(s.studentId(), s.rollNo(), s.fullName(),
							h == null ? null : h.status(), h == null ? null : h.at());
				}).toList());
	}

	/** Sends the alert for each chosen student; every one must be marked absent on the day. */
	public NotificationDto.SendReport send(Long sectionId, LocalDate date, NotificationDto.SendRequest request) {
		AttendanceDto.Sheet sheet = attendanceService.sheet(sectionId, date);
		if (!access.isAdminLike() && !date.equals(LocalDate.now(clock))) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Teachers can send absence alerts only for today");
		}
		Map<Long, AttendanceDto.SheetStudent> absent = absentees(sheet).stream()
			.collect(Collectors.toMap(AttendanceDto.SheetStudent::studentId, s -> s));
		Set<Long> chosen = Set.copyOf(request.studentIds());
		if (!absent.keySet().containsAll(chosen)) {
			throw new BadRequestException("Only students marked absent on " + date.format(DMY) + " can be messaged. "
					+ "Reload the page and try again.");
		}
		String school = schoolService.requireCurrent().getName();
		List<NotificationService.Message> messages = request.studentIds()
			.stream()
			.distinct()
			.map(id -> new NotificationService.Message(id, date, null,
					List.of(absent.get(id).fullName(), sheet.sectionLabel(), school, date.format(DMY))))
			.toList();
		return notifications.send(MessageType.ABSENCE, messages);
	}

	private static List<AttendanceDto.SheetStudent> absentees(AttendanceDto.Sheet sheet) {
		return sheet.students().stream().filter(s -> s.status() == Attendance.Status.A).toList();
	}

}
