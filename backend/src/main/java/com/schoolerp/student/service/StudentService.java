package com.schoolerp.student.service;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.common.crypto.Aadhaar;
import com.schoolerp.common.dto.PageResponse;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.files.service.FileStorage;
import com.schoolerp.files.service.ImageTypes;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Guardian;
import com.schoolerp.student.entity.Student;
import com.schoolerp.student.repository.EnrollmentRepository;
import com.schoolerp.student.repository.GuardianRepository;
import com.schoolerp.student.repository.StudentRepository;
import com.schoolerp.student.repository.StudentSearchRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentService {

	private static final String ENTITY = "student";

	static final long MAX_PHOTO_BYTES = 2 * 1024 * 1024;

	private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)$");

	private final StudentRepository students;

	private final GuardianRepository guardians;

	private final EnrollmentRepository enrollmentRepository;

	private final StudentSearchRepository search;

	private final EnrollmentService enrollments;

	private final AcademicYearService yearService;

	private final StudentAccess access;

	private final FileStorage storage;

	private final AuditService audit;

	private final Clock clock;

	public record Photo(byte[] content, String contentType) {
	}

	@Transactional(readOnly = true)
	public PageResponse<StudentDto.ListItem> search(String q, Long academicYearId, Long classId, Long sectionId,
			Student.Status status, int page, int size) {
		Long schoolId = TenantContext.require();
		Long yearId = yearService.resolve(academicYearId).orElse(null);
		StudentSearchRepository.Result result = search.search(new StudentSearchRepository.Criteria(schoolId, yearId, q,
				classId, sectionId, status, access.restrictedSectionIds(), page, size));
		int totalPages = (int) Math.ceil(result.total() / (double) size);
		return new PageResponse<>(result.rows(), page, size, result.total(), totalPages);
	}

	@Transactional(readOnly = true)
	public StudentDto.Detail get(Long id) {
		return detail(requireVisible(id));
	}

	@Transactional
	public StudentDto.Detail create(StudentDto.CreateRequest request) {
		StudentDto.Details d = request.student();
		String admissionNo = d.admissionNo().trim();
		if (students.existsBySchoolIdAndAdmissionNoIgnoreCase(TenantContext.require(), admissionNo)) {
			throw new ConflictException("Admission number " + admissionNo + " is already used");
		}
		Student student = new Student();
		student.setAdmissionNo(admissionNo);
		apply(student, d);
		students.save(student);
		replaceGuardians(student, d.guardians());
		enrollments.enroll(student, request.enrollment(), d.admissionDate());
		audit.log(AuditAction.CREATE, ENTITY, student.getId(), Map.of("admissionNo", admissionNo));
		return detail(student);
	}

	@Transactional
	public StudentDto.Detail update(Long id, StudentDto.Details d) {
		Student student = require(id);
		String admissionNo = d.admissionNo().trim();
		if (students.existsBySchoolIdAndAdmissionNoIgnoreCaseAndIdNot(TenantContext.require(), admissionNo, id)) {
			throw new ConflictException("Admission number " + admissionNo + " is already used");
		}
		student.setAdmissionNo(admissionNo);
		apply(student, d);
		replaceGuardians(student, d.guardians());
		audit.log(AuditAction.UPDATE, ENTITY, student.getId(), null);
		return detail(student);
	}

	@Transactional
	public StudentDto.Detail markLeft(Long id, StudentDto.LeaveRequest request) {
		Student student = require(id);
		if (student.getStatus() != Student.Status.ACTIVE) {
			throw new ConflictException("Only an active student can be marked as left");
		}
		if (request.leftOn().isBefore(student.getAdmissionDate())) {
			throw new BadRequestException("The leaving date cannot be before the admission date");
		}
		student.setStatus(Student.Status.LEFT);
		student.setLeftOn(request.leftOn());
		student.setLeavingReason(request.reason().trim());
		enrollments.closeActive(student.getId(), request.leftOn());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("status", "LEFT");
		details.put("leftOn", request.leftOn().toString());
		audit.log(AuditAction.STATUS_CHANGE, ENTITY, student.getId(), details);
		return detail(student);
	}

	/**
	 * Suggests the next admission number: the highest existing number plus one,
	 * keeping any prefix and zero padding (SSV/2026/009 becomes SSV/2026/010).
	 */
	@Transactional(readOnly = true)
	public String nextAdmissionNo() {
		String best = null;
		long bestValue = -1;
		for (String no : students.admissionNumbers(TenantContext.require())) {
			Matcher m = TRAILING_NUMBER.matcher(no);
			if (m.matches() && m.group(2).length() <= 15) {
				long value = Long.parseLong(m.group(2));
				if (value > bestValue) {
					bestValue = value;
					best = no;
				}
			}
		}
		if (best == null) {
			return "1";
		}
		Matcher m = TRAILING_NUMBER.matcher(best);
		m.matches();
		String digits = m.group(2);
		String next = String.valueOf(bestValue + 1);
		if (next.length() < digits.length()) {
			next = "0".repeat(digits.length() - next.length()) + next;
		}
		return m.group(1) + next;
	}

	@Transactional
	public StudentDto.Detail uploadPhoto(Long id, MultipartFile file) {
		Student student = require(id);
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("Choose a photo to upload");
		}
		if (file.getSize() > MAX_PHOTO_BYTES) {
			throw new BadRequestException("Photo must be 2 MB or smaller");
		}
		byte[] content;
		try {
			content = file.getBytes();
		}
		catch (IOException ex) {
			throw new BadRequestException("Could not read the uploaded file");
		}
		String extension = ImageTypes.detect(content)
			.orElseThrow(() -> new BadRequestException("Photo must be a PNG, JPEG or WebP image"));
		String key = "schools/" + student.getSchoolId() + "/students/" + student.getId() + "/photo-" + clock.millis()
				+ "." + extension;
		storage.put(key, content, ImageTypes.contentType(extension));
		String previous = student.getPhotoKey();
		student.setPhotoKey(key);
		if (previous != null) {
			storage.delete(previous);
		}
		audit.log(AuditAction.UPDATE, ENTITY, student.getId(), Map.of("change", "photo"));
		return detail(student);
	}

	/** Photos are personal data: served only to staff who may see the student, never publicly. */
	@Transactional(readOnly = true)
	public Photo photo(Long id) {
		Student student = requireVisible(id);
		String key = Optional.ofNullable(student.getPhotoKey()).orElseThrow(() -> new NotFoundException("Photo"));
		byte[] content = storage.get(key).orElseThrow(() -> new NotFoundException("Photo"));
		return new Photo(content, ImageTypes.contentType(key.substring(key.lastIndexOf('.') + 1)));
	}

	Student require(Long id) {
		return students.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Student"));
	}

	/** Like {@link #require} but also applies the teacher's section scope (404 outside it). */
	private Student requireVisible(Long id) {
		Student student = require(id);
		Set<Long> allowed = access.restrictedSectionIds();
		if (allowed != null && (allowed.isEmpty() || !enrollmentRepository
			.existsBySchoolIdAndStudentIdAndSectionIdIn(TenantContext.require(), id, allowed))) {
			throw new NotFoundException("Student");
		}
		return student;
	}

	private void apply(Student s, StudentDto.Details d) {
		s.setAdmissionDate(d.admissionDate());
		s.setFirstName(d.firstName().trim());
		s.setLastName(blankToNull(d.lastName()));
		if (!d.dateOfBirth().isBefore(d.admissionDate())) {
			throw new BadRequestException("Date of birth must be before the admission date");
		}
		s.setDateOfBirth(d.dateOfBirth());
		s.setGender(d.gender());
		s.setMotherTongue(blankToNull(d.motherTongue()));
		s.setSocialCategory(d.socialCategory());
		s.setReligion(blankToNull(d.religion()));
		s.setCaste(blankToNull(d.caste()));
		if (d.aadhaar() != null) {
			String digits = Aadhaar.normalize(d.aadhaar());
			if (digits.isEmpty()) {
				s.setAadhaarNumber(null);
			}
			else if (!Aadhaar.isValid(digits)) {
				throw new BadRequestException("Enter a valid 12-digit Aadhaar number");
			}
			else {
				s.setAadhaarNumber(digits);
			}
		}
		s.setIdentificationMarks(blankToNull(d.identificationMarks()));
		s.setAddress(blankToNull(d.address()));
		s.setPreviousSchool(blankToNull(d.previousSchool()));
		s.setPreviousTcNo(blankToNull(d.previousTcNo()));
	}

	private void replaceGuardians(Student student, List<StudentDto.GuardianInput> inputs) {
		if (inputs.stream().noneMatch(g -> g.phone() != null && !g.phone().isBlank())) {
			throw new BadRequestException("At least one parent or guardian needs a mobile number");
		}
		long primaries = inputs.stream().filter(StudentDto.GuardianInput::isPrimary).count();
		if (primaries > 1) {
			throw new BadRequestException("Only one parent or guardian can be the primary contact");
		}
		Long schoolId = TenantContext.require();
		guardians.deleteAll(guardians.findBySchoolIdAndStudentIdOrderByPrimaryDescIdAsc(schoolId, student.getId()));
		guardians.flush();
		List<Guardian> saved = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			StudentDto.GuardianInput in = inputs.get(i);
			Guardian g = new Guardian();
			g.setStudentId(student.getId());
			g.setRelation(in.relation());
			g.setFullName(in.fullName().trim());
			g.setOccupation(blankToNull(in.occupation()));
			g.setPhone(blankToNull(in.phone()));
			g.setEmail(blankToNull(in.email()));
			g.setPrimary(primaries == 0 ? i == 0 : in.isPrimary());
			saved.add(g);
		}
		guardians.saveAll(saved);
	}

	private StudentDto.Detail detail(Student s) {
		Long schoolId = TenantContext.require();
		boolean sensitive = access.canSeeSensitive();
		List<StudentDto.GuardianView> guardianViews = guardians
			.findBySchoolIdAndStudentIdOrderByPrimaryDescIdAsc(schoolId, s.getId())
			.stream()
			.map(StudentDto.GuardianView::of)
			.toList();
		List<StudentDto.EnrollmentView> history = enrollments.history(s.getId());
		Long currentYearId = yearService.resolve(null).orElse(null);
		StudentDto.EnrollmentView current = history.stream()
			.filter(e -> e.academicYearId().equals(currentYearId))
			.findFirst()
			.orElse(null);
		// Changes whenever a new photo is stored, so clients can cache by it.
		long photoVersion = s.getPhotoKey() == null ? 0 : s.getPhotoKey().hashCode();
		return new StudentDto.Detail(s.getId(), s.getAdmissionNo(), s.getAdmissionDate(), s.getFirstName(),
				s.getLastName(), s.getFullName(), s.getDateOfBirth(), s.getGender(), s.getMotherTongue(), sensitive,
				sensitive ? s.getSocialCategory() : null, sensitive ? s.getReligion() : null,
				sensitive ? s.getCaste() : null,
				sensitive && s.getAadhaarLast4() != null ? "XXXX XXXX " + s.getAadhaarLast4() : null,
				s.getIdentificationMarks(), s.getAddress(), s.getPreviousSchool(), s.getPreviousTcNo(), s.getStatus(),
				s.getLeftOn(), s.getLeavingReason(), s.getPhotoKey() != null, photoVersion, guardianViews, current,
				history);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
