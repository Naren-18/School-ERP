package com.schoolerp.school.seed.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.schoolerp.academic.dto.AcademicYearDto;
import com.schoolerp.academic.dto.ClassDto;
import com.schoolerp.academic.dto.ClassSubjectDto;
import com.schoolerp.academic.dto.HolidayDto;
import com.schoolerp.academic.dto.SectionDto;
import com.schoolerp.academic.dto.SubjectDto;
import com.schoolerp.academic.dto.TeacherAssignmentDto;
import com.schoolerp.academic.service.AcademicYearService;
import com.schoolerp.academic.service.ClassService;
import com.schoolerp.academic.service.HolidayService;
import com.schoolerp.academic.service.SchoolCalendarService;
import com.schoolerp.academic.service.SectionService;
import com.schoolerp.academic.service.SubjectService;
import com.schoolerp.academic.service.TeacherAssignmentService;
import com.schoolerp.attendance.dto.AttendanceDto;
import com.schoolerp.attendance.entity.Attendance;
import com.schoolerp.attendance.service.AttendanceService;
import com.schoolerp.auth.dto.UserDto;
import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.repository.AppUserRepository;
import com.schoolerp.auth.service.AuthUser;
import com.schoolerp.auth.service.UserService;
import com.schoolerp.certificate.dto.CertificateDto;
import com.schoolerp.certificate.entity.TemplateType;
import com.schoolerp.certificate.service.CertificateService;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.service.ExamService;
import com.schoolerp.exam.service.GradingService;
import com.schoolerp.exam.service.MarksService;
import com.schoolerp.exam.service.ReportCardNoteService;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.entity.PaymentMode;
import com.schoolerp.fee.service.FeeCollectionService;
import com.schoolerp.fee.service.FeeDueService;
import com.schoolerp.fee.service.FeeSetupService;
import com.schoolerp.school.dto.UpdateSchoolProfileRequest;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.school.repository.SchoolRepository;
import com.schoolerp.school.service.SchoolService;
import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Guardian;
import com.schoolerp.student.entity.Student;
import com.schoolerp.student.service.EnrollmentService;
import com.schoolerp.student.service.StudentService;
import com.schoolerp.tenant.TenantContext;

/**
 * Builds the three demo schools through the application's own services
 * (signed in as each school's principal), so every rule applies: dues on
 * admission, gap-free receipt and certificate numbers, attendance and marks
 * checks, audit entries. Random choices use a fixed seed per school, so a
 * run on the same day gives the same data. Idempotent: a school whose code
 * exists is skipped.
 */
@Service
@Profile({ "demo", "test" })
public class DemoDataService {

	private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

	/**
	 * @param studentsPerSection about this many (± 3 when 10 or more)
	 * @param attendanceDays how far back attendance goes (school days only)
	 */
	public record Options(String password, int studentsPerSection, int attendanceDays) {
	}

	public record Result(List<String> created, List<String> skipped) {
	}

	private final SchoolRepository schools;

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TransactionTemplate tx;

	private final SchoolService schoolService;

	private final UserService userService;

	private final AcademicYearService yearService;

	private final ClassService classService;

	private final SectionService sectionService;

	private final SubjectService subjectService;

	private final TeacherAssignmentService assignmentService;

	private final HolidayService holidayService;

	private final SchoolCalendarService calendar;

	private final StudentService studentService;

	private final EnrollmentService enrollmentService;

	private final FeeSetupService feeSetup;

	private final FeeCollectionService feeCollection;

	private final FeeDueService feeDues;

	private final AttendanceService attendanceService;

	private final GradingService gradingService;

	private final ExamService examService;

	private final MarksService marksService;

	private final ReportCardNoteService noteService;

	private final CertificateService certificateService;

	private final Clock clock;

	public DemoDataService(SchoolRepository schools, AppUserRepository users, PasswordEncoder passwordEncoder,
			TransactionTemplate tx, SchoolService schoolService, UserService userService, AcademicYearService yearService,
			ClassService classService, SectionService sectionService, SubjectService subjectService,
			TeacherAssignmentService assignmentService, HolidayService holidayService, SchoolCalendarService calendar,
			StudentService studentService, EnrollmentService enrollmentService, FeeSetupService feeSetup,
			FeeCollectionService feeCollection, FeeDueService feeDues, AttendanceService attendanceService,
			GradingService gradingService, ExamService examService, MarksService marksService,
			ReportCardNoteService noteService, CertificateService certificateService, Clock clock) {
		this.schools = schools;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tx = tx;
		this.schoolService = schoolService;
		this.userService = userService;
		this.yearService = yearService;
		this.classService = classService;
		this.sectionService = sectionService;
		this.subjectService = subjectService;
		this.assignmentService = assignmentService;
		this.holidayService = holidayService;
		this.calendar = calendar;
		this.studentService = studentService;
		this.enrollmentService = enrollmentService;
		this.feeSetup = feeSetup;
		this.feeCollection = feeCollection;
		this.feeDues = feeDues;
		this.attendanceService = attendanceService;
		this.gradingService = gradingService;
		this.examService = examService;
		this.marksService = marksService;
		this.noteService = noteService;
		this.certificateService = certificateService;
		this.clock = clock;
	}

	public Result seed(Options options) {
		if (options.password() == null || options.password().length() < 8) {
			throw new IllegalArgumentException("DEMO_SEED_PASSWORD must be set (at least 8 characters)");
		}
		List<String> created = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		for (DemoSchools.Spec spec : DemoSchools.ALL) {
			if (schools.findByCodeIgnoreCase(spec.code()).isPresent()) {
				skipped.add(spec.code());
				continue;
			}
			long started = System.currentTimeMillis();
			new SchoolSeed(spec, options).run();
			created.add(spec.code());
			log.info("Demo seed: created '{}' (code {}) in {} s", spec.name(), spec.code(),
					(System.currentTimeMillis() - started) / 1000);
		}
		return new Result(created, skipped);
	}

	/** One school's data. */
	private final class SchoolSeed {

		private final DemoSchools.Spec spec;

		private final Options options;

		private final Random random;

		private final LocalDate today = LocalDate.now(clock);

		private LocalDate yearStart;

		private LocalDate yearEnd;

		private Long schoolId;

		private Long yearId;

		private final Map<Integer, Long> classIds = new LinkedHashMap<>();

		/** level → section ids (A, B) */
		private final Map<Integer, List<Long>> sectionIds = new LinkedHashMap<>();

		private final Map<String, Long> subjectIds = new LinkedHashMap<>();

		private final List<Long> teachers = new ArrayList<>();

		private Long teacher1;

		private Long teacher2;

		private final List<Pupil> pupils = new ArrayList<>();

		private int phoneCounter;

		/** A seeded student: what the later steps need to know about them. */
		private record Pupil(Long studentId, Long sectionId, int level, Long enrollmentId, double ability,
				double attendance, LocalDate joinedOn, boolean recent) {
		}

		SchoolSeed(DemoSchools.Spec spec, Options options) {
			this.spec = spec;
			this.options = options;
			this.random = new Random(spec.code().hashCode() * 31L + today.getYear());
		}

		void run() {
			AppUser principal = createSchool();
			try {
				signInAs(principal);
				step("profile and staff", () -> {
					profile();
					staff();
				});
				step("classes, sections, subjects, teachers, holidays, fee plan", () -> {
					academics();
					holidays();
					fees();
				});
				step("admissions", this::admissions);
				step("concessions and fee payments", () -> {
					concessions();
					payments();
				});
				step("attendance", this::attendance);
				step("exams, marks and remarks", this::exams);
				step("certificates", this::certificates);
			}
			finally {
				SecurityContextHolder.clearContext();
				TenantContext.clear();
			}
		}

		private void step(String name, Runnable work) {
			long started = System.currentTimeMillis();
			work.run();
			log.info("Demo seed {}: {} done in {} s", spec.code(), name, (System.currentTimeMillis() - started) / 1000);
		}

		// --- School, staff -------------------------------------------------

		private AppUser createSchool() {
			return tx.execute(status -> {
				School school = new School();
				school.setCode(spec.code());
				school.setName(spec.name());
				school.setBoard(spec.board());
				school.setMedium(spec.medium());
				school.setPrimaryColor(spec.color());
				schools.save(school);
				schoolId = school.getId();
				return TenantContext.runAs(schoolId, () -> {
					AppUser p = new AppUser();
					p.setUsername("principal");
					p.setFullName(spec.staff().principal());
					p.setDesignation("Principal");
					p.setPasswordHash(passwordEncoder.encode(options.password()));
					p.setRoles(EnumSet.of(Role.PRINCIPAL));
					return users.save(p);
				});
			});
		}

		private void signInAs(AppUser user) {
			AuthUser authUser = new AuthUser(user.getId(), schoolId, user.getFullName(), Set.copyOf(user.getRoles()));
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(new UsernamePasswordAuthenticationToken(authUser, null,
					user.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList()));
			SecurityContextHolder.setContext(context);
			TenantContext.set(schoolId);
		}

		private void profile() {
			schoolService.updateProfile(new UpdateSchoolProfileRequest(spec.name(), spec.board(), spec.medium(), spec.address(),
					spec.phone(), spec.email(), spec.color()));
			schoolService.updateSettings(new SchoolSettings(spec.prefix(), spec.prefix(), spec.weekOff(),
					new SchoolSettings.Modules(true, true, true, true), new SchoolSettings.ReportCard(spec.showRank(), true),
					new SchoolSettings.Messages(true, true, true, true)));
			schoolService.uploadLogo(resource(spec.logo()));
		}

		private void staff() {
			user("admin", spec.staff().admin(), "Administrator", Role.ADMIN);
			user("accountant", spec.staff().accountant(), "Accountant", Role.ACCOUNTANT);
			teacher1 = user("teacher1", spec.staff().teacher1(), "Teacher", Role.TEACHER);
			teacher2 = user("teacher2", spec.staff().teacher2(), "Teacher", Role.TEACHER);
			// One class teacher per section, plus subject teachers: about 26 in all.
			int needed = DemoSchools.CLASSES.size() * 2 - 2;
			Set<String> used = new HashSet<>(List.of(spec.staff().teacher1(), spec.staff().teacher2()));
			for (int i = 0; i < needed; i++) {
				String name;
				do {
					boolean woman = random.nextInt(10) < 6;
					name = (woman ? DemoNames.pick(random, DemoNames.WOMEN) : DemoNames.pick(random, DemoNames.MEN)) + " "
							+ DemoNames.pick(random, DemoNames.SURNAMES);
				}
				while (!used.add(name));
				teachers.add(user("teacher" + (i + 3), name, "Teacher", Role.TEACHER));
			}
		}

		private Long user(String username, String fullName, String designation, Role role) {
			return userService.create(new UserDto.CreateRequest(username, fullName, designation, null, null, Set.of(role),
					options.password())).id();
		}

		// --- Year, classes, sections, subjects, teachers --------------------

		private void academics() {
			int startYear = today.getMonthValue() >= spec.yearStartMonth() ? today.getYear() : today.getYear() - 1;
			yearStart = LocalDate.of(startYear, spec.yearStartMonth(), 1);
			yearEnd = yearStart.plusMonths(spec.yearMonths()).minusDays(1);
			String name = startYear + "-" + String.valueOf(startYear + 1).substring(2);
			yearId = yearService.create(new AcademicYearDto.Request(name, yearStart, yearEnd)).id();
			yearService.makeCurrent(yearId);

			for (int level = 0; level < DemoSchools.CLASSES.size(); level++) {
				classIds.put(level, classService.create(new ClassDto.Request(DemoSchools.CLASSES.get(level), level + 1)).id());
			}
			for (DemoSchools.SubjectSpec s : spec.subjects()) {
				subjectIds.put(s.name(), subjectService.create(new SubjectDto.Request(s.name(), s.code(), s.type())).id());
			}
			// Class teachers: teacher1 for Class 5 – A, teacher2 for Class 8 – B, the rest in order.
			int next = 0;
			for (int level = 0; level < DemoSchools.CLASSES.size(); level++) {
				List<Long> ids = new ArrayList<>();
				for (String sectionName : List.of("A", "B")) {
					Long classTeacher = level == 7 && sectionName.equals("A") ? teacher1
							: level == 10 && sectionName.equals("B") ? teacher2 : teachers.get(next++);
					ids.add(sectionService.create(new SectionDto.Request(yearId, classIds.get(level), sectionName, classTeacher, 40)).id());
				}
				sectionIds.put(level, ids);
				int lvl = level;
				subjectService.setClassSubjects(new ClassSubjectDto.Request(yearId, classIds.get(level),
						spec.subjects().stream().filter(s -> s.taughtIn(lvl)).map(s -> subjectIds.get(s.name())).toList()));
			}
			assignTeachers();
		}

		/**
		 * Pre-primary: the class teacher teaches everything. Classes 1–10: each
		 * subject has a few subject teachers; teacher1 takes Mathematics in Classes
		 * 5–7 and teacher2 English in Classes 8–10.
		 */
		private void assignTeachers() {
			Map<String, List<Long>> specialists = new HashMap<>();
			int t = 0;
			for (DemoSchools.SubjectSpec s : spec.subjects()) {
				List<Long> pool = new ArrayList<>();
				for (int k = 0; k < 3; k++) {
					pool.add(teachers.get((t++) % teachers.size()));
				}
				specialists.put(s.name(), pool);
			}
			for (int level = 0; level < DemoSchools.CLASSES.size(); level++) {
				for (int si = 0; si < 2; si++) {
					Long sectionId = sectionIds.get(level).get(si);
					Long classTeacher = sectionService.get(sectionId).classTeacherId();
					for (DemoSchools.SubjectSpec s : spec.subjects()) {
						if (!s.taughtIn(level)) {
							continue;
						}
						Long teacher;
						if (level < DemoSchools.CLASS_1) {
							teacher = classTeacher;
						}
						else if (s.name().equals("Mathematics") && level >= 7 && level <= 9) {
							teacher = teacher1;
						}
						else if (s.name().equals("English") && level >= 10) {
							teacher = teacher2;
						}
						else {
							List<Long> pool = specialists.get(s.name());
							teacher = pool.get((level + si) % pool.size());
						}
						assignmentService.assign(new TeacherAssignmentDto.Request(sectionId, subjectIds.get(s.name()), teacher));
					}
				}
			}
		}

		private void holidays() {
			for (DemoSchools.HolidaySpec h : spec.holidays()) {
				LocalDate date = inYear(h.date());
				if (!date.isAfter(yearEnd)) {
					holidayService.create(new HolidayDto.Request(yearId, date, h.description(), null));
				}
			}
		}

		// --- Fees ------------------------------------------------------------

		private void fees() {
			Map<String, Long> heads = new HashMap<>();
			for (DemoSchools.FeeSpec f : spec.fees()) {
				Long head = heads.computeIfAbsent(f.head(), name -> feeSetup.createHead(new FeeDto.HeadRequest(name, true)).id());
				for (int level = 0; level < DemoSchools.CLASSES.size(); level++) {
					feeSetup.createInstallment(new FeeDto.InstallmentRequest(yearId, classIds.get(level), head,
							f.head().equals("Tuition") ? f.installment() : f.head() + " – " + f.installment(), f.amount(level),
							inYear(f.due())));
				}
			}
		}

		// --- Students --------------------------------------------------------

		private void admissions() {
			int admissionNo = 1001;
			int recentLeft = 6;
			for (int level = 0; level < DemoSchools.CLASSES.size(); level++) {
				for (Long sectionId : sectionIds.get(level)) {
					int base = options.studentsPerSection();
					int count = base >= 10 ? base - 3 + random.nextInt(7) : base;
					for (int i = 0; i < count; i++) {
						boolean recent = recentLeft > 0 && level >= DemoSchools.CLASS_1 && random.nextInt(40) == 0;
						if (recent) {
							recentLeft--;
						}
						admit(String.valueOf(admissionNo++), level, sectionId, recent);
					}
					// Roll numbers in alphabetical order, as schools do at the start of the year.
					Map<Long, Long> enrollmentByStudent = new HashMap<>();
					enrollmentService.renumber(sectionId).forEach(s -> enrollmentByStudent.put(s.studentId(), s.enrollmentId()));
					pupils.replaceAll(p -> p.sectionId().equals(sectionId) && p.enrollmentId() == null
							? new Pupil(p.studentId(), p.sectionId(), p.level(), enrollmentByStudent.get(p.studentId()), p.ability(),
									p.attendance(), p.joinedOn(), p.recent())
							: p);
				}
			}
		}

		private void admit(String admissionNo, int level, Long sectionId, boolean recent) {
			boolean girl = random.nextBoolean();
			String surname = DemoNames.pick(random, DemoNames.SURNAMES);
			String first = girl ? DemoNames.pick(random, DemoNames.GIRLS) : DemoNames.pick(random, DemoNames.BOYS);
			int age = level + 3;
			LocalDate dob = yearStart.minusYears(age).minusDays(random.nextInt(365));
			LocalDate admitted;
			LocalDate joinedOn;
			if (recent) {
				admitted = today.minusDays(3 + random.nextInt(25));
				joinedOn = admitted;
			}
			else {
				int joinedLevel = random.nextInt(level + 1);
				admitted = yearStart.minusYears(level - joinedLevel).plusDays(random.nextInt(12));
				if (admitted.isAfter(today)) {
					admitted = today;
				}
				joinedOn = yearStart;
			}
			String fatherFirst = DemoNames.pick(random, DemoNames.MEN);
			String motherFirst = DemoNames.pick(random, DemoNames.WOMEN);
			List<StudentDto.GuardianInput> guardians = new ArrayList<>();
			guardians.add(new StudentDto.GuardianInput(Guardian.Relation.FATHER, fatherFirst + " " + surname,
					DemoNames.pick(random, DemoNames.OCCUPATIONS), phone(), null, true));
			guardians.add(new StudentDto.GuardianInput(Guardian.Relation.MOTHER, motherFirst + " " + surname,
					random.nextInt(3) == 0 ? DemoNames.pick(random, DemoNames.OCCUPATIONS) : "Homemaker",
					random.nextInt(2) == 0 ? phone() : null, null, false));
			String address = "H.No. " + (1 + random.nextInt(12)) + "-" + (1 + random.nextInt(9)) + "-" + (10 + random.nextInt(300))
					+ ", " + DemoNames.pick(random, DemoNames.LOCALITIES) + ", " + spec.town();
			StudentDto.Details details = new StudentDto.Details(admissionNo, admitted, first, surname, dob,
					girl ? Student.Gender.FEMALE : Student.Gender.MALE, motherTongue(), category(), religion(), null, null,
					null, address, recent ? "Previous school, " + spec.town() : null, null, guardians, null);
			Long studentId = studentService.create(new StudentDto.CreateRequest(details,
					new StudentDto.Placement(sectionId, null, joinedOn))).id();
			double ability = clamp(0.68 + random.nextGaussian() * 0.15, 0.25, 0.99);
			double attendanceRate = clamp(0.93 + random.nextGaussian() * 0.04, 0.75, 1.0);
			pupils.add(new Pupil(studentId, sectionId, level, null, ability, attendanceRate, joinedOn, recent));
		}

		/** 10 digits starting 900, unique within the run; nobody's real number. */
		private String phone() {
			return "900" + (DemoSchools.ALL.indexOf(spec) + 1) + String.format("%06d", ++phoneCounter);
		}

		private String motherTongue() {
			int r = random.nextInt(100);
			return r < 8 ? "Urdu" : r < 14 ? "Hindi" : spec.motherTongue().equals("English") && r < 40 ? "English" : "Telugu";
		}

		private Student.SocialCategory category() {
			Student.SocialCategory[] weighted = { Student.SocialCategory.GEN, Student.SocialCategory.GEN,
					Student.SocialCategory.BC_A, Student.SocialCategory.BC_B, Student.SocialCategory.BC_B,
					Student.SocialCategory.BC_D, Student.SocialCategory.BC_D, Student.SocialCategory.BC_E,
					Student.SocialCategory.SC, Student.SocialCategory.ST };
			return weighted[random.nextInt(weighted.length)];
		}

		private String religion() {
			int r = random.nextInt(100);
			return r < 84 ? "Hindu" : r < 94 ? "Muslim" : "Christian";
		}

		// --- Concessions and payments -------------------------------------

		private void concessions() {
			for (Pupil p : pupils) {
				boolean sibling = random.nextInt(100) < 6;
				boolean noTransport = spec.transportHead() != null && random.nextInt(100) < 40;
				if (!sibling && !noTransport) {
					continue;
				}
				for (FeeDto.Due due : feeCollection.studentFees(p.studentId()).dues()) {
					if (noTransport && due.feeHeadName().equals(spec.transportHead())) {
						feeDues.setConcession(due.id(), new FeeDto.ConcessionRequest(due.amount(), "Does not use school transport"));
					}
					else if (sibling && due.feeHeadName().equals("Tuition")) {
						feeDues.setConcession(due.id(), new FeeDto.ConcessionRequest(
								due.amount().multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.HALF_UP), "Sibling concession"));
					}
				}
			}
		}

		/**
		 * Most parents pay around the due date, some pay part, a few nothing yet.
		 * Receipts are collected in date order so their numbers follow the dates.
		 */
		private void payments() {
			TreeMap<LocalDate, Map<Long, List<FeeDto.CollectLine>>> byDate = new TreeMap<>();
			for (Pupil p : pupils) {
				int habit = random.nextInt(100); // < 65 prompt, < 85 partial, else not yet
				for (FeeDto.Due due : feeCollection.studentFees(p.studentId()).dues()) {
					BigDecimal net = due.amount().subtract(due.concession());
					if (net.signum() <= 0 || due.dueDate().isAfter(today)) {
						continue;
					}
					LocalDate earliest = p.recent() ? p.joinedOn() : due.dueDate().minusDays(10);
					LocalDate paidOn = p.recent() ? p.joinedOn() : due.dueDate().plusDays(random.nextInt(35) - 10);
					paidOn = max(max(paidOn, earliest), yearStart);
					if (paidOn.isAfter(today)) {
						continue; // would pay later: still due today
					}
					BigDecimal amount;
					if (p.recent() || habit < 65) {
						amount = net;
					}
					else if (habit < 85) {
						amount = net.multiply(BigDecimal.valueOf(0.4 + random.nextDouble() * 0.3)).setScale(-2, RoundingMode.DOWN);
					}
					else {
						continue;
					}
					if (amount.signum() > 0) {
						byDate.computeIfAbsent(paidOn, d -> new LinkedHashMap<>())
							.computeIfAbsent(p.studentId(), s -> new ArrayList<>())
							.add(new FeeDto.CollectLine(due.id(), amount));
					}
				}
			}
			byDate.forEach((date, students) -> students.forEach((studentId, lines) -> {
				PaymentMode mode = mode();
				String reference = switch (mode) {
					case CHEQUE -> String.format("%06d", 100000 + random.nextInt(900000));
					case UPI, BANK -> "TXN" + String.format("%09d", random.nextInt(1_000_000_000));
					default -> null;
				};
				feeCollection.collect(new FeeDto.CollectRequest(studentId, date, mode, reference, lines));
			}));
		}

		private PaymentMode mode() {
			int r = random.nextInt(100);
			return r < 50 ? PaymentMode.CASH : r < 85 ? PaymentMode.UPI : r < 95 ? PaymentMode.CHEQUE : PaymentMode.BANK;
		}

		// --- Attendance ----------------------------------------------------

		/** Past school days in full; today for about three in four sections (the rest show as pending). */
		private void attendance() {
			Map<Long, Pupil> byEnrollment = new HashMap<>();
			pupils.forEach(p -> byEnrollment.put(p.enrollmentId(), p));
			LocalDate from = max(yearStart, today.minusDays(options.attendanceDays()));
			for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
				if (calendar.nonWorkingReason(day).isPresent()) {
					continue;
				}
				for (List<Long> ids : sectionIds.values()) {
					for (Long sectionId : ids) {
						if (day.equals(today) && random.nextInt(4) == 0) {
							continue;
						}
						AttendanceDto.Sheet sheet = attendanceService.sheet(sectionId, day);
						if (!sheet.editable() || sheet.students().isEmpty()) {
							continue;
						}
						List<AttendanceDto.Entry> entries = sheet.students().stream()
							.map(s -> new AttendanceDto.Entry(s.enrollmentId(), status(byEnrollment.get(s.enrollmentId()))))
							.toList();
						attendanceService.save(sectionId, day, new AttendanceDto.SaveRequest(entries));
					}
				}
			}
		}

		private Attendance.Status status(Pupil p) {
			double rate = p == null ? 0.93 : p.attendance();
			if (random.nextDouble() < rate) {
				return Attendance.Status.P;
			}
			int r = random.nextInt(10);
			return r < 7 ? Attendance.Status.A : r < 9 ? Attendance.Status.L : Attendance.Status.H;
		}

		// --- Exams -----------------------------------------------------------

		/**
		 * Every exam of the year with papers for Classes 1–10. The first exam that
		 * is over gets marks, remarks and co-scholastic grades and is published; the
		 * next one that is over has marks for about two papers in three (draft), so
		 * teachers see work pending.
		 */
		private void exams() {
			Long scaleId = gradingService.addPreset(spec.grading()).id();
			List<Long> examClasses = classIds.entrySet().stream()
				.filter(e -> e.getKey() >= DemoSchools.CLASS_1)
				.map(Map.Entry::getValue)
				.toList();
			List<Long> areaIds = new ArrayList<>();
			for (int i = 0; i < spec.coScholastic().size(); i++) {
				areaIds.add(noteService.createArea(new ExamDto.AreaRequest(spec.coScholastic().get(i), i + 1)).id());
			}
			int past = 0;
			for (int i = 0; i < spec.exams().size(); i++) {
				DemoSchools.ExamSpec e = spec.exams().get(i);
				LocalDate start = inYear(e.start());
				LocalDate end = inYear(e.end());
				if (end.isAfter(yearEnd)) {
					continue;
				}
				Long examId = examService.create(new ExamDto.ExamRequest(yearId, e.name(), scaleId, start, end, i + 1,
						BigDecimal.valueOf(e.weightage()))).id();
				examService.applyToClasses(examId, new ExamDto.ApplyRequest(BigDecimal.valueOf(e.maxMarks()),
						new BigDecimal(e.passMarks()), examClasses));
				if (!end.isBefore(today)) {
					continue;
				}
				past++;
				if (past == 1) {
					enterMarks(examId, e, 1.0);
					enterNotes(examId, areaIds);
					examService.setPublished(examId, true);
				}
				else if (past == 2) {
					enterMarks(examId, e, 0.65);
				}
			}
		}

		private void enterMarks(Long examId, DemoSchools.ExamSpec e, double share) {
			Map<Long, Pupil> byEnrollment = new HashMap<>();
			pupils.forEach(p -> byEnrollment.put(p.enrollmentId(), p));
			for (int level = DemoSchools.CLASS_1; level < DemoSchools.CLASSES.size(); level++) {
				List<ExamDto.Paper> papers = examService.papers(examId, classIds.get(level));
				for (Long sectionId : sectionIds.get(level)) {
					for (ExamDto.Paper paper : papers) {
						if (random.nextDouble() >= share) {
							continue;
						}
						double subjectBias = random.nextGaussian() * 0.05;
						List<ExamDto.MarkEntry> entries = marksService.sheet(paper.id(), sectionId).rows().stream().map(row -> {
							Pupil p = byEnrollment.get(row.enrollmentId());
							if (p != null && p.joinedOn().isAfter(inYear(e.start()))) {
								return null; // admitted after the exam
							}
							if (random.nextInt(60) == 0) {
								return new ExamDto.MarkEntry(row.enrollmentId(), null, true, null);
							}
							double ability = p == null ? 0.65 : p.ability();
							double score = clamp(ability + subjectBias + random.nextGaussian() * 0.08, 0.05, 1.0) * e.maxMarks();
							return new ExamDto.MarkEntry(row.enrollmentId(), BigDecimal.valueOf(Math.round(score)), false, null);
						}).filter(Objects::nonNull).toList();
						if (!entries.isEmpty()) {
							marksService.save(paper.id(), sectionId, new ExamDto.SaveMarks(entries));
						}
					}
				}
			}
		}

		private static final List<String> STRONG = List.of("Excellent work; keep it up.",
				"Very attentive and participates well in class.", "Consistent and hard-working.",
				"A pleasure to teach; helps classmates.");

		private static final List<String> MIDDLE = List.of("Good progress; should read more at home.",
				"Regular and sincere; can improve in written work.", "Participates well; needs to improve handwriting.",
				"Capable of doing better with more practice.");

		private static final List<String> WEAK = List.of("Needs more practice in Mathematics.",
				"Should be more regular with homework.", "Needs support at home with reading.",
				"Must pay more attention in class.");

		private void enterNotes(Long examId, List<Long> areaIds) {
			Map<Long, Pupil> byEnrollment = new HashMap<>();
			pupils.forEach(p -> byEnrollment.put(p.enrollmentId(), p));
			for (int level = DemoSchools.CLASS_1; level < DemoSchools.CLASSES.size(); level++) {
				for (Long sectionId : sectionIds.get(level)) {
					List<ExamDto.NoteEntry> entries = noteService.sheet(examId, sectionId).rows().stream().map(row -> {
						Pupil p = byEnrollment.get(row.enrollmentId());
						double ability = p == null ? 0.65 : p.ability();
						String remark = DemoNames.pick(random, ability > 0.8 ? STRONG : ability > 0.55 ? MIDDLE : WEAK);
						Map<Long, String> grades = new LinkedHashMap<>();
						for (Long areaId : areaIds) {
							double g = ability + random.nextGaussian() * 0.15;
							grades.put(areaId, g > 0.75 ? "A" : g > 0.5 ? "B" : "C");
						}
						return new ExamDto.NoteEntry(row.enrollmentId(), remark, grades);
					}).toList();
					noteService.save(examId, sectionId, new ExamDto.SaveNotes(entries));
				}
			}
		}

		// --- Certificates --------------------------------------------------

		private void certificates() {
			List<String> purposes = List.of("applying for a scholarship", "opening a bank account", "a passport application",
					"a bus pass", "participating in a district sports meet");
			List<Pupil> settled = pupils.stream()
				.filter(p -> !p.recent() && p.level() >= DemoSchools.CLASS_1)
				.sorted(Comparator.comparing(Pupil::studentId))
				.toList();
			if (settled.size() < 8) {
				return;
			}
			for (int i = 0; i < 5; i++) {
				Pupil p = settled.get(random.nextInt(settled.size()));
				certificateService.issue(p.studentId(), new CertificateDto.IssueRequest(TemplateType.BONAFIDE, purposes.get(i), null));
			}
			List<String> reasons = List.of("Parent's transfer to Hyderabad", "Family moving to another town");
			for (int i = 0; i < 2; i++) {
				Pupil p = settled.get(settled.size() - 1 - i * 3);
				certificateService.issue(p.studentId(), new CertificateDto.IssueRequest(TemplateType.TC,
						"admission to another school", new CertificateDto.TcDetails(today.minusDays(5 + i * 9), reasons.get(i),
								"Good", "Yes, to the next class", null)));
			}
		}

		// --- Helpers ---------------------------------------------------------

		/** The date of a month-day within the current academic year. */
		private LocalDate inYear(MonthDay md) {
			int year = md.getMonthValue() >= spec.yearStartMonth() ? yearStart.getYear() : yearStart.getYear() + 1;
			return md.atYear(year);
		}

		private byte[] resource(String path) {
			try (InputStream in = DemoDataService.class.getResourceAsStream(path)) {
				if (in == null) {
					throw new IllegalStateException("Missing " + path);
				}
				return in.readAllBytes();
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

		private static LocalDate max(LocalDate a, LocalDate b) {
			return a.isAfter(b) ? a : b;
		}

		private static double clamp(double v, double min, double max) {
			return Math.max(min, Math.min(max, v));
		}

	}

}
