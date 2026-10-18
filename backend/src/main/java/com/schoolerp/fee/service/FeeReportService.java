package com.schoolerp.fee.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.entity.PaymentMode;
import com.schoolerp.fee.repository.FeeReportRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeeReportService {

	private final FeeReportRepository reports;

	private final AcademicYearService yearService;

	private final Clock clock;

	@Transactional(readOnly = true)
	public FeeDto.CollectionReport collection(LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.now(clock);
		LocalDate start = from != null ? from : today;
		LocalDate end = to != null ? to : start;
		if (end.isBefore(start)) {
			throw new BadRequestException("The end date is before the start date");
		}
		if (start.plusDays(366).isBefore(end)) {
			throw new BadRequestException("Choose a range of at most one year");
		}
		List<FeeDto.CollectionDay> days = reports.collection(TenantContext.require(), start, end);
		Map<PaymentMode, BigDecimal> byMode = new EnumMap<>(PaymentMode.class);
		BigDecimal total = BigDecimal.ZERO;
		int count = 0;
		for (FeeDto.CollectionDay d : days) {
			byMode.merge(d.paymentMode(), d.total(), BigDecimal::add);
			total = total.add(d.total());
			count += d.receipts();
		}
		return new FeeDto.CollectionReport(start, end, total, count, byMode, days);
	}

	@Transactional(readOnly = true)
	public List<FeeDto.DueRow> dues(Long academicYearId, Long classId, Long sectionId, boolean overdueOnly) {
		return reports.dues(TenantContext.require(), year(academicYearId), classId, sectionId, overdueOnly,
				LocalDate.now(clock));
	}

	@Transactional(readOnly = true)
	public List<FeeDto.ClassSummary> classSummary(Long academicYearId) {
		return reports.classSummary(TenantContext.require(), year(academicYearId));
	}

	private Long year(Long academicYearId) {
		return yearService.resolve(academicYearId).orElseThrow(() -> new NotFoundException("Current academic year"));
	}

}
