package com.schoolerp.academic.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.AcademicYearDto;
import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.repository.AcademicYearRepository;
import com.schoolerp.academic.repository.ClassSubjectRepository;
import com.schoolerp.academic.repository.HolidayRepository;
import com.schoolerp.academic.repository.SectionRepository;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Academic years. A school always has exactly one current year once it has any. */
@Service
@RequiredArgsConstructor
public class AcademicYearService {

	private final AcademicYearRepository years;

	private final SectionRepository sections;

	private final ClassSubjectRepository classSubjects;

	private final HolidayRepository holidays;

	@Transactional(readOnly = true)
	public List<AcademicYearDto.Response> list() {
		return years.findBySchoolIdOrderByStartDateDesc(TenantContext.require())
			.stream()
			.map(AcademicYearDto.Response::of)
			.toList();
	}

	@Transactional(readOnly = true)
	public AcademicYearDto.Response get(Long id) {
		return AcademicYearDto.Response.of(require(id));
	}

	@Transactional(readOnly = true)
	public AcademicYearDto.Response current() {
		return years.findBySchoolIdAndCurrentTrue(TenantContext.require())
			.map(AcademicYearDto.Response::of)
			.orElseThrow(() -> new NotFoundException("Current academic year"));
	}

	@Transactional
	public AcademicYearDto.Response create(AcademicYearDto.Request request) {
		Long schoolId = TenantContext.require();
		String name = request.name().trim();
		if (years.existsBySchoolIdAndNameIgnoreCase(schoolId, name)) {
			throw new ConflictException("An academic year named " + name + " already exists");
		}
		AcademicYear year = new AcademicYear();
		year.setName(name);
		year.setStartDate(request.startDate());
		year.setEndDate(request.endDate());
		year.setCurrent(years.countBySchoolId(schoolId) == 0);
		return AcademicYearDto.Response.of(years.save(year));
	}

	@Transactional
	public AcademicYearDto.Response update(Long id, AcademicYearDto.Request request) {
		Long schoolId = TenantContext.require();
		AcademicYear year = require(id);
		String name = request.name().trim();
		if (years.existsBySchoolIdAndNameIgnoreCaseAndIdNot(schoolId, name, id)) {
			throw new ConflictException("An academic year named " + name + " already exists");
		}
		if (holidays.existsOutsideRange(schoolId, id, request.startDate(), request.endDate())) {
			throw new ConflictException("Some holidays of this year fall outside the new dates");
		}
		year.setName(name);
		year.setStartDate(request.startDate());
		year.setEndDate(request.endDate());
		return AcademicYearDto.Response.of(year);
	}

	@Transactional
	public AcademicYearDto.Response makeCurrent(Long id) {
		AcademicYear year = require(id);
		if (!year.isCurrent()) {
			years.clearCurrent(year.getSchoolId());
			years.flush();
			year.setCurrent(true);
		}
		return AcademicYearDto.Response.of(year);
	}

	@Transactional
	public void delete(Long id) {
		Long schoolId = TenantContext.require();
		AcademicYear year = require(id);
		if (year.isCurrent()) {
			throw new ConflictException("The current academic year cannot be deleted");
		}
		if (sections.existsBySchoolIdAndAcademicYearId(schoolId, id)
				|| classSubjects.existsBySchoolIdAndAcademicYearId(schoolId, id)
				|| holidays.existsBySchoolIdAndAcademicYearId(schoolId, id)) {
			throw new ConflictException("This year has sections, subjects or holidays. Remove them first.");
		}
		years.delete(year);
		years.flush();
	}

	/**
	 * The given year (404 if not this school's), or the current year when none is
	 * given; empty only when the school has no current year yet.
	 */
	public Optional<Long> resolve(Long academicYearId) {
		if (academicYearId != null) {
			return Optional.of(require(academicYearId).getId());
		}
		return years.findBySchoolIdAndCurrentTrue(TenantContext.require()).map(AcademicYear::getId);
	}

	/** The year if it belongs to the current school; otherwise 404. */
	public AcademicYear require(Long id) {
		return years.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Academic year"));
	}

}
