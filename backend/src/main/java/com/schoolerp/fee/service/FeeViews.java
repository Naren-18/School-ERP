package com.schoolerp.fee.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.schoolerp.academic.dto.AcademicYearDto;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.entity.FeeHead;
import com.schoolerp.fee.entity.FeeInstallment;
import com.schoolerp.fee.entity.StudentFeeDue;
import com.schoolerp.fee.repository.FeeHeadRepository;
import com.schoolerp.fee.repository.FeeInstallmentRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Turns due rows into readable views (installment, head, year names) with batched lookups. */
@Component
@RequiredArgsConstructor
class FeeViews {

	private final FeeInstallmentRepository installments;

	private final FeeHeadRepository heads;

	private final AcademicYearService yearService;

	private final Clock clock;

	List<FeeDto.Due> dues(Collection<StudentFeeDue> dues) {
		if (dues.isEmpty()) {
			return List.of();
		}
		Long schoolId = TenantContext.require();
		Map<Long, FeeInstallment> inst = installments
			.findBySchoolIdAndIdIn(schoolId, dues.stream().map(StudentFeeDue::getFeeInstallmentId).collect(Collectors.toSet()))
			.stream()
			.collect(Collectors.toMap(FeeInstallment::getId, Function.identity()));
		Map<Long, String> headNames = headNames(inst.values().stream().map(FeeInstallment::getFeeHeadId).toList());
		Map<Long, String> years = yearNames();
		LocalDate today = LocalDate.now(clock);
		return dues.stream().map(d -> {
			FeeInstallment i = inst.get(d.getFeeInstallmentId());
			BigDecimal balance = d.getBalance();
			return new FeeDto.Due(d.getId(), d.getEnrollmentId(), i.getAcademicYearId(), years.get(i.getAcademicYearId()),
					i.getId(), i.getName(), headNames.get(i.getFeeHeadId()), i.getDueDate(), d.getAmount(),
					d.getConcession(), d.getConcessionReason(), d.getPaidAmount(), balance,
					balance.signum() > 0 && i.getDueDate().isBefore(today));
		})
			.sorted(Comparator.comparing(FeeDto.Due::dueDate).thenComparing(FeeDto.Due::feeHeadName))
			.toList();
	}

	static FeeDto.Totals totals(List<FeeDto.Due> dues) {
		BigDecimal net = BigDecimal.ZERO;
		BigDecimal paid = BigDecimal.ZERO;
		BigDecimal overdue = BigDecimal.ZERO;
		for (FeeDto.Due d : dues) {
			net = net.add(d.amount().subtract(d.concession()));
			paid = paid.add(d.paid());
			if (d.overdue()) {
				overdue = overdue.add(d.balance());
			}
		}
		return new FeeDto.Totals(net, paid, net.subtract(paid), overdue);
	}

	Map<Long, String> headNames(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return new HashMap<>();
		}
		return heads.findBySchoolIdAndIdIn(TenantContext.require(), ids)
			.stream()
			.collect(Collectors.toMap(FeeHead::getId, FeeHead::getName));
	}

	Map<Long, String> yearNames() {
		return yearService.list()
			.stream()
			.collect(Collectors.toMap(AcademicYearDto.Response::id, AcademicYearDto.Response::name));
	}

}
