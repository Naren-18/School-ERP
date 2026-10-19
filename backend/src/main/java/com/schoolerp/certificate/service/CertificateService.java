package com.schoolerp.certificate.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.auth.service.CurrentUser;
import com.schoolerp.auth.service.StaffService;
import com.schoolerp.certificate.dto.CertificateDto;
import com.schoolerp.certificate.entity.IssuedCertificate;
import com.schoolerp.certificate.entity.TemplateType;
import com.schoolerp.certificate.repository.CertificateCounterRepository;
import com.schoolerp.certificate.repository.IssuedCertificateRepository;
import com.schoolerp.common.dto.PageResponse;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.common.pdf.HtmlPdf;
import com.schoolerp.common.template.Placeholders;
import com.schoolerp.files.service.FileStorage;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Student;
import com.schoolerp.student.service.StudentService;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Issuing bonafide certificates and TCs: a serial number per school and type,
 * a snapshot of the printed values, and the PDF kept in file storage.
 */
@Service
@RequiredArgsConstructor
public class CertificateService {

	private final IssuedCertificateRepository issued;

	private final CertificateCounterRepository counters;

	private final TemplateService templates;

	private final TemplateValues values;

	private final StudentService studentService;

	private final SchoolService schoolService;

	private final StaffService staff;

	private final FileStorage storage;

	private final AuditService audit;

	private final Clock clock;

	/** The PDF as it would be issued now, without numbering or saving anything. */
	@Transactional(readOnly = true)
	public byte[] preview(Long studentId, CertificateDto.IssueRequest request) {
		Student student = studentService.requireVisible(studentId);
		check(student, request);
		School school = schoolService.requireCurrent();
		Map<String, String> v = valuesFor(school, studentService.get(studentId), request, "(preview)");
		return render(school, templates.activeHtml(request.type()), v, values.logoBlock());
	}

	@Transactional
	public CertificateDto.Issued issue(Long studentId, CertificateDto.IssueRequest request) {
		Student student = studentService.requireVisible(studentId);
		check(student, request);
		School school = schoolService.requireCurrent();
		Long schoolId = TenantContext.require();
		String serial = "%s/%s/%04d".formatted(school.getSettings().certificatePrefix(), request.type().serialCode(),
				counters.next(schoolId, request.type()));
		if (request.type() == TemplateType.TC) {
			// Marks an active student as left; the details printed below are read afterwards.
			LocalDate leftOn = request.tc().leavingDate() != null ? request.tc().leavingDate() : student.getLeftOn();
			studentService.markTcIssued(studentId, leftOn, blankToNull(request.tc().reason()));
		}
		Map<String, String> v = valuesFor(school, studentService.get(studentId), request, serial);
		byte[] pdf = render(school, templates.activeHtml(request.type()), v, values.logoBlock());

		IssuedCertificate c = new IssuedCertificate();
		c.setStudentId(studentId);
		c.setType(request.type());
		c.setSerialNo(serial);
		c.setIssuedOn(LocalDate.now(clock));
		c.setIssuedBy(CurrentUser.get().userId());
		c.setPurpose(blankToNull(request.purpose()));
		c.setData(v);
		issued.saveAndFlush(c);
		String key = "schools/" + schoolId + "/certificates/" + c.getId() + "-" + serial.replaceAll("[^A-Za-z0-9]", "-") + ".pdf";
		storage.put(key, pdf, "application/pdf");
		c.setPdfKey(key);
		audit.log(AuditAction.ISSUE, "certificate", c.getId(),
				Map.of("type", request.type().name(), "serialNo", serial, "studentId", studentId));
		return view(c, Map.of(c.getIssuedBy(), CurrentUser.get().name()));
	}

	@Transactional(readOnly = true)
	public PageResponse<CertificateDto.Issued> list(TemplateType type, LocalDate from, LocalDate to, String q, int page,
			int size) {
		Page<IssuedCertificate> result = issued.search(TenantContext.require(), type == null ? null : type.name(), from, to,
				q == null || q.isBlank() ? null : q.trim(), PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));
		List<CertificateDto.Issued> views = views(result.getContent());
		return new PageResponse<>(views, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public List<CertificateDto.Issued> forStudent(Long studentId) {
		studentService.requireVisible(studentId);
		return views(issued.findBySchoolIdAndStudentIdOrderByIssuedOnDescIdDesc(TenantContext.require(), studentId));
	}

	public record Pdf(String fileName, byte[] content) {
	}

	/** The stored PDF; if the file is missing it is rebuilt from the snapshot with the current template. */
	@Transactional(readOnly = true)
	public Pdf pdf(Long id) {
		IssuedCertificate c = issued.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Certificate"));
		byte[] content = c.getPdfKey() == null ? null : storage.get(c.getPdfKey()).orElse(null);
		if (content == null) {
			content = render(schoolService.requireCurrent(), templates.activeHtml(c.getType()), c.getData(), values.logoBlock());
		}
		return new Pdf(c.getSerialNo().replaceAll("[^A-Za-z0-9]", "-") + ".pdf", content);
	}

	/** Sample values for the template editor's preview. */
	public byte[] sample(TemplateType type, String html) {
		School school = schoolService.requireCurrent();
		Map<String, String> v = new LinkedHashMap<>(values.school(school));
		v.putAll(Map.ofEntries(Map.entry("student.fullName", "Sample Student"), Map.entry("student.admissionNo", "1234"),
				Map.entry("student.admissionDate", "10-06-2021"), Map.entry("student.admittedClass", "Class 1"),
				Map.entry("student.dateOfBirth", "03-02-2016"),
				Map.entry("student.dateOfBirthInWords", TemplateValues.dateInWords(LocalDate.of(2016, 2, 3))),
				Map.entry("student.gender", "Female"), Map.entry("student.sonDaughter", "daughter"),
				Map.entry("student.heShe", "she"), Map.entry("student.hisHer", "her"),
				Map.entry("student.fatherName", "Sample Father"), Map.entry("student.motherName", "Sample Mother"),
				Map.entry("student.guardianName", "Sample Father"), Map.entry("student.class", "Class 5 – A"),
				Map.entry("student.className", "Class 5"), Map.entry("student.sectionName", "A"),
				Map.entry("student.rollNo", "7"), Map.entry("student.academicYear", "2026-27"),
				Map.entry("student.motherTongue", "Telugu"), Map.entry("student.religion", "—"),
				Map.entry("student.caste", "—"), Map.entry("student.socialCategory", "—"),
				Map.entry("student.identificationMarks", "A mole on the right hand"),
				Map.entry("student.address", "1-2-3, Main Road"), Map.entry("certificate.serialNo", "(sample)"),
				Map.entry("certificate.issuedOn", LocalDate.now(clock).format(TemplateValues.DMY)),
				Map.entry("certificate.purpose", "applying for a scholarship"),
				Map.entry("tc.leavingDate", LocalDate.now(clock).format(TemplateValues.DMY)),
				Map.entry("tc.reason", "Parent's transfer"), Map.entry("tc.conduct", "Good"),
				Map.entry("tc.promotion", "Yes, to Class 6"), Map.entry("tc.remarks", "")));
		return render(school, html, v, values.logoBlock());
	}

	/** Which certificates can be issued to a student now. */
	private void check(Student student, CertificateDto.IssueRequest request) {
		switch (request.type()) {
			case BONAFIDE -> {
				if (student.getStatus() != Student.Status.ACTIVE) {
					throw new ConflictException("A bonafide certificate is only for a student who is studying here now");
				}
			}
			case TC -> {
				if (student.getStatus() == Student.Status.TC_ISSUED) {
					String serial = issued.findFirstBySchoolIdAndStudentIdAndType(TenantContext.require(), student.getId(), TemplateType.TC)
						.map(IssuedCertificate::getSerialNo)
						.orElse("earlier");
					throw new ConflictException("A TC (" + serial + ") was already issued. Download it again from the register.");
				}
				if (request.tc() == null) {
					throw new BadRequestException("Fill in the TC details");
				}
				if (student.getStatus() == Student.Status.ACTIVE && request.tc().leavingDate() == null) {
					throw new BadRequestException("Date of leaving is required");
				}
				if (student.getStatus() == Student.Status.ACTIVE
						&& (request.tc().reason() == null || request.tc().reason().isBlank())) {
					throw new BadRequestException("Reason for leaving is required");
				}
			}
			case REPORT_CARD -> throw new BadRequestException("Report cards are printed from Exams");
		}
	}

	private Map<String, String> valuesFor(School school, StudentDto.Detail s, CertificateDto.IssueRequest request,
			String serial) {
		Map<String, String> v = new LinkedHashMap<>(values.school(school));
		v.putAll(values.student(s, TemplateValues.classOf(s)));
		v.put("certificate.serialNo", serial);
		v.put("certificate.issuedOn", LocalDate.now(clock).format(TemplateValues.DMY));
		v.put("certificate.purpose", nz(request.purpose()).trim());
		if (request.type() == TemplateType.TC) {
			CertificateDto.TcDetails tc = request.tc();
			LocalDate left = tc.leavingDate() != null ? tc.leavingDate() : s.leftOn();
			v.put("tc.leavingDate", left == null ? "" : left.format(TemplateValues.DMY));
			v.put("tc.reason", !nz(tc.reason()).isBlank() ? tc.reason().trim() : nz(s.leavingReason()));
			v.put("tc.conduct", !nz(tc.conduct()).isBlank() ? tc.conduct().trim() : "Good");
			v.put("tc.promotion", nz(tc.promotion()).trim());
			v.put("tc.remarks", nz(tc.remarks()).trim());
		}
		return v;
	}

	private static byte[] render(School school, String html, Map<String, String> v, String logo) {
		return HtmlPdf.render(TemplateDocument.wrap(List.of(Placeholders.render(html, v, Map.of("school.logo", logo))),
				school.getPrimaryColor()));
	}

	private List<CertificateDto.Issued> views(List<IssuedCertificate> list) {
		Set<Long> userIds = new HashSet<>();
		list.forEach(c -> userIds.add(c.getIssuedBy()));
		Map<Long, String> names = staff.fullNames(userIds);
		return list.stream().map(c -> view(c, names)).toList();
	}

	/** Student name and admission no. come from the snapshot, as printed. */
	private static CertificateDto.Issued view(IssuedCertificate c, Map<Long, String> names) {
		Map<String, String> d = c.getData();
		return new CertificateDto.Issued(c.getId(), c.getStudentId(), d.get("student.fullName"), d.get("student.admissionNo"),
				c.getType(), c.getType().title(), c.getSerialNo(), c.getIssuedOn(), names.get(c.getIssuedBy()), c.getPurpose());
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

}
