package com.schoolerp.academic.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.HolidayDto;
import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.entity.Holiday;
import com.schoolerp.academic.repository.HolidayRepository;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HolidayService {

	private final HolidayRepository holidays;

	private final AcademicYearService yearService;

	@Transactional(readOnly = true)
	public List<HolidayDto.Response> list(Long academicYearId) {
		Long yearId = yearService.resolve(academicYearId).orElse(null);
		if (yearId == null) {
			return List.of();
		}
		return holidays.findBySchoolIdAndAcademicYearIdOrderByDateAsc(TenantContext.require(), yearId)
			.stream()
			.map(HolidayDto.Response::of)
			.toList();
	}

	@Transactional
	public HolidayDto.Response create(HolidayDto.Request request) {
		validate(request);
		if (holidays.existsBySchoolIdAndDate(TenantContext.require(), request.date())) {
			throw new ConflictException("There is already a holiday on this date");
		}
		Holiday holiday = new Holiday();
		apply(holiday, request);
		return HolidayDto.Response.of(holidays.save(holiday));
	}

	@Transactional
	public HolidayDto.Response update(Long id, HolidayDto.Request request) {
		Holiday holiday = require(id);
		validate(request);
		if (holidays.existsBySchoolIdAndDateAndIdNot(TenantContext.require(), request.date(), id)) {
			throw new ConflictException("There is already a holiday on this date");
		}
		apply(holiday, request);
		return HolidayDto.Response.of(holiday);
	}

	@Transactional
	public void delete(Long id) {
		holidays.delete(require(id));
	}

	private Holiday require(Long id) {
		return holidays.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Holiday"));
	}

	private void validate(HolidayDto.Request request) {
		AcademicYear year = yearService.require(request.academicYearId());
		if (!year.contains(request.date())) {
			throw new BadRequestException("The date must be within the academic year " + year.getName());
		}
	}

	private static void apply(Holiday holiday, HolidayDto.Request request) {
		holiday.setAcademicYearId(request.academicYearId());
		holiday.setDate(request.date());
		holiday.setDescription(request.description().trim());
	}

}
