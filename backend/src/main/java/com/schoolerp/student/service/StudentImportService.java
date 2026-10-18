package com.schoolerp.student.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.schoolerp.academic.dto.ClassDto;
import com.schoolerp.academic.entity.Section;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.ClassService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.common.crypto.Aadhaar;
import com.schoolerp.common.csv.Csv;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Guardian;
import com.schoolerp.student.entity.Student;
import com.schoolerp.student.repository.StudentRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Bulk admission from CSV into the current academic year. All-or-nothing: if any
 * row is invalid nothing is created and the report lists every problem.
 */
@Service
@RequiredArgsConstructor
public class StudentImportService {

	static final int MAX_ROWS = 2000;

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	/** Template columns; * marks required ones in the header comment of the template. */
	static final List<String> COLUMNS = List.of("admission_no", "admission_date", "first_name", "last_name", "gender",
			"date_of_birth", "class", "section", "roll_no", "father_name", "father_phone", "mother_name",
			"mother_phone", "guardian_name", "guardian_phone", "address", "mother_tongue", "social_category",
			"religion", "caste", "aadhaar", "previous_school");

	private static final Set<String> REQUIRED = Set.of("admission_no", "admission_date", "first_name", "gender",
			"date_of_birth", "class", "section");

	private final StudentService studentService;

	private final StudentRepository students;

	private final AcademicYearService yearService;

	private final ClassService classService;

	private final SectionService sectionService;

	private final Clock clock;

	public String template() {
		return Csv.write(List.of(COLUMNS,
				List.of("1001", "10-06-2026", "Aarav", "Reddy", "MALE", "15-03-2016", "Class 5", "A", "", "Suresh Reddy",
						"9876543210", "Lakshmi Reddy", "", "", "", "Kukatpally, Hyderabad", "Telugu", "", "", "", "", "")));
	}

	@Transactional
	public StudentDto.ImportReport importCsv(MultipartFile file, boolean dryRun) {
		List<List<String>> rows = read(file);
		if (rows.isEmpty()) {
			throw new BadRequestException("The file is empty");
		}
		Map<String, Integer> header = header(rows.getFirst());
		List<List<String>> data = rows.subList(1, rows.size());
		if (data.isEmpty()) {
			throw new BadRequestException("The file has a header but no students");
		}
		if (data.size() > MAX_ROWS) {
			throw new BadRequestException("Import at most " + MAX_ROWS + " students at a time");
		}
		Long yearId = yearService.resolve(null)
			.orElseThrow(() -> new BadRequestException("Create the current academic year before importing"));

		List<StudentDto.ImportError> errors = new ArrayList<>();
		List<StudentDto.CreateRequest> requests = new ArrayList<>();
		RowContext ctx = new RowContext(yearId);
		for (int i = 0; i < data.size(); i++) {
			int rowNo = i + 2; // spreadsheet row number, header is row 1
			Row row = new Row(rowNo, data.get(i), header, errors);
			parse(row, ctx).ifPresent(requests::add);
		}

		int valid = data.size() - (int) errors.stream().map(StudentDto.ImportError::row).distinct().count();
		if (dryRun || !errors.isEmpty()) {
			return new StudentDto.ImportReport(dryRun, data.size(), valid, 0, errors);
		}
		// One transaction: any failure here rolls back every student created so far.
		requests.forEach(studentService::create);
		return new StudentDto.ImportReport(false, data.size(), valid, requests.size(), List.of());
	}

	private Optional<StudentDto.CreateRequest> parse(Row r, RowContext ctx) {
		String admissionNo = r.required("admission_no");
		LocalDate admissionDate = r.date("admission_date", true);
		String firstName = r.required("first_name");
		Student.Gender gender = r.gender();
		LocalDate dob = r.date("date_of_birth", true);
		Long sectionId = ctx.section(r);
		Integer rollNo = r.integer("roll_no");

		if (admissionNo != null) {
			String key = admissionNo.toLowerCase(Locale.ROOT);
			if (!ctx.admissionNos.add(key)) {
				r.error("admission_no", "Appears more than once in the file");
			}
			else if (ctx.existingAdmissionNos.contains(key)) {
				r.error("admission_no", "Already used by an existing student");
			}
		}
		if (admissionDate != null && admissionDate.isAfter(LocalDate.now(clock))) {
			r.error("admission_date", "Cannot be in the future");
		}
		if (dob != null && admissionDate != null && !dob.isBefore(admissionDate)) {
			r.error("date_of_birth", "Must be before the admission date");
		}
		if (sectionId != null && rollNo != null && !ctx.rollNos.add(sectionId + ":" + rollNo)) {
			r.error("roll_no", "Roll number repeated in this section");
		}

		List<StudentDto.GuardianInput> guardians = new ArrayList<>();
		r.guardian(Guardian.Relation.FATHER, "father_name", "father_phone").ifPresent(guardians::add);
		r.guardian(Guardian.Relation.MOTHER, "mother_name", "mother_phone").ifPresent(guardians::add);
		r.guardian(Guardian.Relation.GUARDIAN, "guardian_name", "guardian_phone").ifPresent(guardians::add);
		if (guardians.stream().noneMatch(g -> g.phone() != null)) {
			r.error("father_phone", "At least one parent or guardian with a mobile number is required");
		}

		String aadhaar = r.optional("aadhaar");
		if (aadhaar != null && !Aadhaar.isValid(Aadhaar.normalize(aadhaar))) {
			r.error("aadhaar", "Not a valid Aadhaar number");
		}
		Student.SocialCategory category = r.enumValue("social_category", Student.SocialCategory.class);

		if (r.hasErrors()) {
			return Optional.empty();
		}
		StudentDto.Details details = new StudentDto.Details(admissionNo, admissionDate, firstName,
				r.optional("last_name"), dob, gender, r.optional("mother_tongue"), category, r.optional("religion"),
				r.optional("caste"), aadhaar, null, r.optional("address"), r.optional("previous_school"), null, guardians);
		return Optional.of(new StudentDto.CreateRequest(details, new StudentDto.Placement(sectionId, rollNo, null)));
	}

	private static List<List<String>> read(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("Choose a CSV file to import");
		}
		if (file.getSize() > 2 * 1024 * 1024) {
			throw new BadRequestException("The file must be 2 MB or smaller");
		}
		try {
			return Csv.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new BadRequestException("Could not read the uploaded file");
		}
	}

	private static Map<String, Integer> header(List<String> cells) {
		Map<String, Integer> index = new HashMap<>();
		for (int i = 0; i < cells.size(); i++) {
			index.put(cells.get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace("*", ""), i);
		}
		List<String> missing = REQUIRED.stream().filter(c -> !index.containsKey(c)).sorted().toList();
		if (!missing.isEmpty()) {
			throw new BadRequestException("Missing columns: " + String.join(", ", missing)
					+ ". Download the template to see the expected format.");
		}
		return index;
	}

	/** Lookups shared across rows: existing data and duplicates within the file. */
	private final class RowContext {

		final Map<String, Long> classIdByName;

		final Map<String, Optional<Long>> sectionCache = new HashMap<>();

		final Set<String> existingAdmissionNos;

		final Set<String> admissionNos = new HashSet<>();

		final Set<String> rollNos = new HashSet<>();

		final Long yearId;

		RowContext(Long yearId) {
			this.yearId = yearId;
			this.classIdByName = classService.list()
				.stream()
				.collect(Collectors.toMap(c -> c.name().toLowerCase(Locale.ROOT), ClassDto.Response::id, (a, b) -> a));
			this.existingAdmissionNos = students.admissionNumbers(TenantContext.require())
				.stream()
				.map(no -> no.toLowerCase(Locale.ROOT))
				.collect(Collectors.toSet());
		}

		Long section(Row r) {
			String className = r.required("class");
			String sectionName = r.required("section");
			if (className == null || sectionName == null) {
				return null;
			}
			Long classId = classIdByName.get(className.toLowerCase(Locale.ROOT));
			if (classId == null) {
				r.error("class", "No class named \"" + className + "\"");
				return null;
			}
			Optional<Long> id = sectionCache.computeIfAbsent(classId + ":" + sectionName.toLowerCase(Locale.ROOT),
					k -> sectionService.findByNames(yearId, classId, sectionName).map(Section::getId));
			if (id.isEmpty()) {
				r.error("section", "No section \"" + sectionName + "\" in " + className + " this year");
			}
			return id.orElse(null);
		}

	}

	/** One CSV row with typed accessors that record errors instead of throwing. */
	private record Row(int number, List<String> cells, Map<String, Integer> header,
			List<StudentDto.ImportError> errors) {

		String optional(String column) {
			Integer i = header.get(column);
			if (i == null || i >= cells.size()) {
				return null;
			}
			String v = cells.get(i).trim();
			return v.isEmpty() ? null : v;
		}

		String required(String column) {
			String v = optional(column);
			if (v == null) {
				error(column, "Required");
			}
			return v;
		}

		LocalDate date(String column, boolean required) {
			String v = required ? required(column) : optional(column);
			if (v == null) {
				return null;
			}
			try {
				return LocalDate.parse(v, DATE);
			}
			catch (DateTimeParseException ex) {
				error(column, "Use DD-MM-YYYY, e.g. 05-06-2026");
				return null;
			}
		}

		Integer integer(String column) {
			String v = optional(column);
			if (v == null) {
				return null;
			}
			try {
				int n = Integer.parseInt(v);
				if (n < 1 || n > 999) {
					throw new NumberFormatException();
				}
				return n;
			}
			catch (NumberFormatException ex) {
				error(column, "Must be a number from 1 to 999");
				return null;
			}
		}

		Student.Gender gender() {
			String v = required("gender");
			if (v == null) {
				return null;
			}
			return switch (v.toUpperCase(Locale.ROOT)) {
				case "M", "MALE", "BOY" -> Student.Gender.MALE;
				case "F", "FEMALE", "GIRL" -> Student.Gender.FEMALE;
				case "O", "OTHER" -> Student.Gender.OTHER;
				default -> {
					error("gender", "Use MALE, FEMALE or OTHER");
					yield null;
				}
			};
		}

		<E extends Enum<E>> E enumValue(String column, Class<E> type) {
			String v = optional(column);
			if (v == null) {
				return null;
			}
			try {
				return Enum.valueOf(type, v.toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException ex) {
				error(column, "Unknown value \"" + v + "\"");
				return null;
			}
		}

		Optional<StudentDto.GuardianInput> guardian(Guardian.Relation relation, String nameColumn, String phoneColumn) {
			String name = optional(nameColumn);
			String phone = optional(phoneColumn);
			if (name == null && phone == null) {
				return Optional.empty();
			}
			if (name == null) {
				error(nameColumn, "Name is required when a phone number is given");
				return Optional.empty();
			}
			String digits = phone == null ? null : phone.replaceAll("[\\s-]", "").replaceFirst("^\\+91", "");
			if (digits != null && !digits.matches(StudentDto.MOBILE)) {
				error(phoneColumn, "Enter a 10-digit mobile number");
				return Optional.empty();
			}
			return Optional.of(new StudentDto.GuardianInput(relation, name, null, digits, null, false));
		}

		void error(String field, String message) {
			errors.add(new StudentDto.ImportError(number, field, message));
		}

		boolean hasErrors() {
			return errors.stream().anyMatch(e -> e.row() == number);
		}

	}

}
