package com.schoolerp.fee.service;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.ClassDto;
import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.entity.AcademicYear;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.ClassService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.entity.FeeHead;
import com.schoolerp.fee.entity.FeeInstallment;
import com.schoolerp.fee.entity.StudentFeeDue;
import com.schoolerp.fee.repository.FeeHeadRepository;
import com.schoolerp.fee.repository.FeeInstallmentRepository;
import com.schoolerp.fee.repository.FeeReceiptLineRepository;
import com.schoolerp.fee.repository.StudentFeeDueRepository;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.service.EnrollmentService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Fee heads and the per-class installment plan for a year. */
@Service
@RequiredArgsConstructor
public class FeeSetupService {

	private final FeeHeadRepository heads;

	private final FeeInstallmentRepository installments;

	private final StudentFeeDueRepository dues;

	private final FeeReceiptLineRepository receiptLines;

	private final FeeDueService dueService;

	private final AcademicYearService yearService;

	private final ClassService classService;

	private final SectionService sectionService;

	private final EnrollmentService enrollmentService;

	// --- Heads -------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<FeeDto.Head> heads() {
		return heads.findBySchoolIdOrderByNameAsc(TenantContext.require()).stream().map(FeeSetupService::view).toList();
	}

	@Transactional
	public FeeDto.Head createHead(FeeDto.HeadRequest request) {
		String name = request.name().trim();
		if (heads.existsBySchoolIdAndNameIgnoreCase(TenantContext.require(), name)) {
			throw new ConflictException("A fee head named " + name + " already exists");
		}
		FeeHead head = new FeeHead();
		head.setName(name);
		return view(heads.save(head));
	}

	@Transactional
	public FeeDto.Head updateHead(Long id, FeeDto.HeadRequest request) {
		FeeHead head = requireHead(id);
		String name = request.name().trim();
		if (heads.existsBySchoolIdAndNameIgnoreCaseAndIdNot(TenantContext.require(), name, id)) {
			throw new ConflictException("A fee head named " + name + " already exists");
		}
		head.setName(name);
		if (request.active() != null) {
			head.setActive(request.active());
		}
		return view(head);
	}

	/** Heads used in any plan can only be deactivated, so old receipts keep their meaning. */
	@Transactional
	public void deleteHead(Long id) {
		FeeHead head = requireHead(id);
		if (installments.existsBySchoolIdAndFeeHeadId(TenantContext.require(), id)) {
			throw new ConflictException(head.getName() + " is used in a fee plan. Deactivate it instead.");
		}
		heads.delete(head);
	}

	// --- Plan --------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<FeeDto.Installment> plan(Long academicYearId, Long classId) {
		Long schoolId = TenantContext.require();
		yearService.require(academicYearId);
		List<FeeInstallment> list = classId == null
				? installments.findBySchoolIdAndAcademicYearIdOrderByDueDateAscIdAsc(schoolId, academicYearId)
				: installments.findBySchoolIdAndAcademicYearIdAndClassIdOrderByDueDateAscIdAsc(schoolId, academicYearId,
						classService.require(classId).getId());
		return views(list);
	}

	@Transactional
	public FeeDto.Installment createInstallment(FeeDto.InstallmentRequest r) {
		Long schoolId = TenantContext.require();
		validate(r);
		String name = r.name().trim();
		if (installments.existsBySchoolIdAndAcademicYearIdAndClassIdAndFeeHeadIdAndNameIgnoreCase(schoolId,
				r.academicYearId(), r.classId(), r.feeHeadId(), name)) {
			throw new ConflictException("This class already has \"" + name + "\" for that fee head");
		}
		FeeInstallment i = new FeeInstallment();
		i.setAcademicYearId(r.academicYearId());
		i.setClassId(r.classId());
		i.setFeeHeadId(r.feeHeadId());
		apply(i, name, r);
		return views(List.of(installments.save(i))).getFirst();
	}

	/**
	 * Name, amount and due date can change after dues exist; a new amount is
	 * applied to every student's due unless someone has paid (or been given a
	 * concession) beyond it.
	 */
	@Transactional
	public FeeDto.Installment updateInstallment(Long id, FeeDto.InstallmentRequest r) {
		Long schoolId = TenantContext.require();
		FeeInstallment i = requireInstallment(id);
		if (!i.getAcademicYearId().equals(r.academicYearId()) || !i.getClassId().equals(r.classId())
				|| !i.getFeeHeadId().equals(r.feeHeadId())) {
			throw new BadRequestException("Year, class and fee head cannot be changed. Add a new installment instead.");
		}
		String name = r.name().trim();
		if (installments.existsBySchoolIdAndAcademicYearIdAndClassIdAndFeeHeadIdAndNameIgnoreCaseAndIdNot(schoolId,
				i.getAcademicYearId(), i.getClassId(), i.getFeeHeadId(), name, id)) {
			throw new ConflictException("This class already has \"" + name + "\" for that fee head");
		}
		if (i.getAmount().compareTo(r.amount()) != 0) {
			List<StudentFeeDue> existing = dues.findBySchoolIdAndFeeInstallmentId(schoolId, id);
			long blocked = existing.stream()
				.filter(d -> d.getConcession().add(d.getPaidAmount()).compareTo(r.amount()) > 0)
				.count();
			if (blocked > 0) {
				throw new ConflictException(blocked + " student(s) have already paid or been given a concession above ₹"
						+ r.amount() + ". Adjust those first.");
			}
			existing.forEach(d -> d.setAmount(r.amount()));
		}
		apply(i, name, r);
		return views(List.of(i)).getFirst();
	}

	/** Deletes the installment and its unpaid dues; refused once anything has been paid against it. */
	@Transactional
	public void deleteInstallment(Long id) {
		Long schoolId = TenantContext.require();
		FeeInstallment i = requireInstallment(id);
		List<StudentFeeDue> existing = dues.findBySchoolIdAndFeeInstallmentId(schoolId, id);
		if (!existing.isEmpty()
				&& receiptLines.existsByStudentFeeDueIdIn(existing.stream().map(StudentFeeDue::getId).toList())) {
			throw new ConflictException("Payments have been recorded against this installment, so it cannot be deleted");
		}
		dues.deleteAll(existing);
		installments.delete(i);
		installments.flush();
	}

	/** Copies a year's plan into another year (due dates moved by the same number of years). Skips duplicates. */
	@Transactional
	public FeeDto.CopyPlanResult copyPlan(FeeDto.CopyPlanRequest r) {
		Long schoolId = TenantContext.require();
		AcademicYear from = yearService.require(r.fromAcademicYearId());
		AcademicYear to = yearService.require(r.toAcademicYearId());
		if (from.getId().equals(to.getId())) {
			throw new BadRequestException("Choose two different years");
		}
		long years = ChronoUnit.YEARS.between(from.getStartDate().withDayOfMonth(1), to.getStartDate().withDayOfMonth(1));
		int copied = 0;
		int skipped = 0;
		for (FeeInstallment src : installments.findBySchoolIdAndAcademicYearIdOrderByDueDateAscIdAsc(schoolId, from.getId())) {
			if (installments.existsBySchoolIdAndAcademicYearIdAndClassIdAndFeeHeadIdAndNameIgnoreCase(schoolId, to.getId(),
					src.getClassId(), src.getFeeHeadId(), src.getName())) {
				skipped++;
				continue;
			}
			FeeInstallment copy = new FeeInstallment();
			copy.setAcademicYearId(to.getId());
			copy.setClassId(src.getClassId());
			copy.setFeeHeadId(src.getFeeHeadId());
			copy.setName(src.getName());
			copy.setAmount(src.getAmount());
			copy.setDueDate(src.getDueDate().plusYears(years));
			installments.save(copy);
			copied++;
		}
		return new FeeDto.CopyPlanResult(copied, skipped);
	}

	/** Creates missing dues for every active student of the class (or all classes) in the year. Safe to repeat. */
	@Transactional
	public FeeDto.GenerateDuesResult generateDues(FeeDto.GenerateDuesRequest r) {
		Long schoolId = TenantContext.require();
		yearService.require(r.academicYearId());
		if (r.classId() != null) {
			classService.require(r.classId());
		}
		Map<Long, List<FeeInstallment>> planByClass = installments
			.findBySchoolIdAndAcademicYearIdOrderByDueDateAscIdAsc(schoolId, r.academicYearId())
			.stream()
			.collect(Collectors.groupingBy(FeeInstallment::getClassId));
		int created = 0;
		for (SectionDto.Response section : sectionService.list(r.academicYearId(), r.classId())) {
			List<FeeInstallment> plan = planByClass.getOrDefault(section.classId(), List.of());
			if (plan.isEmpty()) {
				continue;
			}
			for (StudentDto.SectionStudent s : enrollmentService.sectionStudents(section.id(), false)) {
				created += dueService.createMissing(s.enrollmentId(), plan);
			}
		}
		return new FeeDto.GenerateDuesResult(created);
	}

	FeeHead requireHead(Long id) {
		return heads.findByIdAndSchoolId(id, TenantContext.require()).orElseThrow(() -> new NotFoundException("Fee head"));
	}

	FeeInstallment requireInstallment(Long id) {
		return installments.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Installment"));
	}

	private void validate(FeeDto.InstallmentRequest r) {
		AcademicYear year = yearService.require(r.academicYearId());
		classService.require(r.classId());
		FeeHead head = requireHead(r.feeHeadId());
		if (!head.isActive()) {
			throw new BadRequestException(head.getName() + " is inactive");
		}
		if (r.dueDate().isBefore(year.getStartDate()) || r.dueDate().isAfter(year.getEndDate())) {
			throw new BadRequestException("The due date must be within " + year.getName());
		}
	}

	private static void apply(FeeInstallment i, String name, FeeDto.InstallmentRequest r) {
		i.setName(name);
		i.setAmount(r.amount());
		i.setDueDate(r.dueDate());
	}

	private List<FeeDto.Installment> views(List<FeeInstallment> list) {
		if (list.isEmpty()) {
			return List.of();
		}
		Long schoolId = TenantContext.require();
		Map<Long, String> headNames = heads
			.findBySchoolIdAndIdIn(schoolId, list.stream().map(FeeInstallment::getFeeHeadId).collect(Collectors.toSet()))
			.stream()
			.collect(Collectors.toMap(FeeHead::getId, FeeHead::getName));
		Map<Long, String> classNames = classService.list()
			.stream()
			.collect(Collectors.toMap(ClassDto.Response::id, ClassDto.Response::name));
		Map<Long, Integer> students = new HashMap<>();
		for (Object[] row : dues.countByInstallment(schoolId, list.stream().map(FeeInstallment::getId).toList())) {
			students.put((Long) row[0], ((Long) row[1]).intValue());
		}
		return list.stream()
			.map(i -> new FeeDto.Installment(i.getId(), i.getAcademicYearId(), i.getClassId(), classNames.get(i.getClassId()),
					i.getFeeHeadId(), headNames.get(i.getFeeHeadId()), i.getName(), i.getAmount(), i.getDueDate(),
					students.getOrDefault(i.getId(), 0)))
			.toList();
	}

	private static FeeDto.Head view(FeeHead h) {
		return new FeeDto.Head(h.getId(), h.getName(), h.isActive());
	}

}
