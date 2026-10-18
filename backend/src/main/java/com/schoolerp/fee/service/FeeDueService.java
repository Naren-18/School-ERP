package com.schoolerp.fee.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.entity.FeeInstallment;
import com.schoolerp.fee.entity.StudentFeeDue;
import com.schoolerp.fee.repository.FeeInstallmentRepository;
import com.schoolerp.fee.repository.StudentFeeDueRepository;
import com.schoolerp.student.service.StudentEnrolledEvent;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Student dues: created from the class plan, adjusted by concessions. */
@Service
@RequiredArgsConstructor
public class FeeDueService {

	private final StudentFeeDueRepository dues;

	private final FeeInstallmentRepository installments;

	private final SectionService sectionService;

	private final FeeViews views;

	private final AuditService audit;

	/** Every new enrollment (admission, promotion, late enrolment) gets that class's dues, in the same transaction. */
	@EventListener
	public void onEnrolled(StudentEnrolledEvent event) {
		Section section = sectionService.require(event.sectionId());
		createMissing(event.enrollmentId(), installments.findBySchoolIdAndAcademicYearIdAndClassIdOrderByDueDateAscIdAsc(
				event.schoolId(), event.academicYearId(), section.getClassId()));
	}

	/** Creates the dues an enrollment is missing for the given installments. Idempotent. */
	int createMissing(Long enrollmentId, List<FeeInstallment> plan) {
		Long schoolId = TenantContext.require();
		int created = 0;
		for (FeeInstallment i : plan) {
			if (!dues.existsBySchoolIdAndEnrollmentIdAndFeeInstallmentId(schoolId, enrollmentId, i.getId())) {
				StudentFeeDue d = new StudentFeeDue();
				d.setEnrollmentId(enrollmentId);
				d.setFeeInstallmentId(i.getId());
				d.setAmount(i.getAmount());
				dues.save(d);
				created++;
			}
		}
		return created;
	}

	/** Sets (or clears, with 0) a concession on one due. It can't exceed what is still unpaid. */
	@Transactional
	public FeeDto.Due setConcession(Long dueId, FeeDto.ConcessionRequest request) {
		StudentFeeDue due = dues.findByIdAndSchoolId(dueId, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Due"));
		BigDecimal concession = request.concession();
		if (concession.compareTo(due.getAmount().subtract(due.getPaidAmount())) > 0) {
			throw new ConflictException("The concession can be at most ₹" + due.getAmount().subtract(due.getPaidAmount())
					+ " (the amount not yet paid)");
		}
		String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
		if (concession.signum() > 0 && reason == null) {
			throw new BadRequestException("Give a reason for the concession (e.g. sibling, staff ward, scholarship)");
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("before", due.getConcession());
		details.put("after", concession);
		details.put("reason", reason);
		due.setConcession(concession);
		due.setConcessionReason(concession.signum() > 0 ? reason : null);
		audit.log(AuditAction.UPDATE, "student_fee_due", due.getId(), details);
		return views.dues(List.of(due)).getFirst();
	}

}
