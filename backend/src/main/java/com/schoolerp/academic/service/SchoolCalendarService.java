package com.schoolerp.academic.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.entity.Holiday;
import com.schoolerp.academic.repository.HolidayRepository;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Which days are not school days: declared holidays and the school's weekly off days. */
@Service
@RequiredArgsConstructor
public class SchoolCalendarService {

	private final HolidayRepository holidays;

	private final SchoolService schoolService;

	/** Non-working days in the range, with a reason ("Holiday: Diwali", "Sunday (weekly off)"). */
	@Transactional(readOnly = true)
	public Map<LocalDate, String> nonWorkingDays(LocalDate from, LocalDate to) {
		Set<DayOfWeek> weekOff = Set.copyOf(schoolService.requireCurrent().getSettings().weekOffDays());
		Map<LocalDate, String> result = new HashMap<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			if (weekOff.contains(d.getDayOfWeek())) {
				result.put(d, d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " (weekly off)");
			}
		}
		// A declared holiday wins over the weekly-off reason.
		result.putAll(holidays.findBySchoolIdAndDateBetween(TenantContext.require(), from, to)
			.stream()
			.collect(Collectors.toMap(Holiday::getDate, h -> "Holiday: " + h.getDescription())));
		return result;
	}

	@Transactional(readOnly = true)
	public Optional<String> nonWorkingReason(LocalDate date) {
		return Optional.ofNullable(nonWorkingDays(date, date).get(date));
	}

}
