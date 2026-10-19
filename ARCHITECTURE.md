# Architecture

How the School ERP is built, as of the end of **Phase 8** (school dashboard).
For the rules that every change must follow, see [`CLAUDE.md`](CLAUDE.md); for the phase
plan, see [`PLAN.md`](PLAN.md); for running it, see [`README.md`](README.md).

---

## 1. Overview

```
Browser (React SPA)
   │  HTTPS, Authorization: Bearer <JWT>
   ▼
Spring Boot API  (/api/v1/**)              one deployment, many schools
   │  JDBC (every tenant row carries school_id)
   ▼
PostgreSQL 16    (schema owned by Flyway)
   +  FileStorage (local disk in dev; Cloudflare R2 planned for prod)
```

- **Multi-tenant, shared schema.** All schools live in one database. Every
  school-owned table has `school_id NOT NULL`. Isolation is enforced in the
  application, in several layers (section 4).
- **Modular monolith.** One Spring Boot app, split into feature modules with the same
  internal layout. Modules call each other's *services*, never each other's
  repositories.
- **Stateless API.** No server sessions. A JWT identifies the user and school, and
  every request re-checks the user is still active (section 5).

## 2. Technology

| Layer | Choice | Version |
|---|---|---|
| Language / build | Java, Maven (wrapper) | 21, Maven 3.10 |
| Framework | Spring Boot (Web MVC, Data JPA, Security, Validation, Actuator) | 4.1.1 |
| ORM | Hibernate | 7.4 |
| JSON | Jackson | 3.1 |
| Database | PostgreSQL, Flyway migrations | 16 |
| JWT | `spring-security-oauth2-jose` (Nimbus), HS256 | managed by Boot |
| PDF | openhtmltopdf (maintained fork `io.github.openhtmltopdf`) on PDFBox 3; DejaVu Sans from `net.sf.jasperreports:jasperreports-fonts` | 1.1.94; fonts 6.21.3 |
| Tests | JUnit 5, Testcontainers (Postgres 16), MockMvc, AssertJ | managed by Boot |
| Frontend | React, Vite, TypeScript | React 19, Vite 8, TS 6 |
| UI / data | Ant Design 6 (+ icons), TanStack Query 5, React Router 7, axios, dayjs | |
| Local infra | Docker Compose (Postgres) | |

Libraries added beyond the stack in `CLAUDE.md`:
- **`spring-security-oauth2-jose`:** JWT signing and verification, part of Spring
  Security.
- **`io.github.openhtmltopdf:openhtmltopdf-pdfbox`:** the PDF engine `CLAUDE.md` names; the
  original `com.openhtmltopdf` has had no release since 2021.
- **`net.sf.jasperreports:jasperreports-fonts`:** only for its DejaVu Sans TTF files. The
  built-in PDF fonts have no ₹ glyph and servers have no system fonts; the artifact has
  no dependencies, and a test checks the glyph is present.
- **`@ant-design/icons`:** already pulled in by antd; declared because it's imported
  directly.

The CSV reader is ~60 lines in-house rather than another dependency.

## 3. Backend modules

Package root `com.schoolerp`. Each module has `controller/ service/ repository/ entity/ dto/`.

| Module | Responsibility (so far) |
|---|---|
| `common` | `BaseEntity`, `TenantEntity`; errors (`ApiException`, `NotFoundException` 404, `ConflictException` 409, `BadRequestException` 400, `GlobalExceptionHandler`); `PageResponse`; `crypto` (AES-GCM `FieldCipher`, `EncryptedStringConverter`, Aadhaar Verhoeff check); `csv` (RFC 4180 reader/writer); `money` (`AmountInWords`, Indian lakh/crore); `pdf` (`HtmlPdf`) |
| `tenant` | `TenantContext` (ThreadLocal school id), `TenantIdResolver` (feeds the Hibernate filter), `TenantGuard` (404 unless the entity is this school's) |
| `auth` | Users and roles, login, JWT, per-request session check, rate limiting, user management, staff picker, `Access` role expressions, security config |
| `school` | School profile, typed settings (JSONB), logo upload, public branding, module toggles (`@RequiresModule` + `ModuleGuard`), dev seeder and `ProductionSafetyCheck` |
| `academic` | Academic years, classes, sections, subjects, class–subject mapping, teacher assignments, holidays, teacher scope, `SectionUsageCheck` (other modules veto section deletion) |
| `student` | Students, guardians, enrollments, search, photos, leaving, promotion, roll numbers, CSV import, `StudentEnrolledEvent` |
| `audit` | `AuditService` writing `audit_log` |
| `files` | `FileStorage` interface, `LocalFileStorage`, `ImageTypes` (detects image type from content) |
| `attendance` | Marking a section for a day (bulk upsert), access and date rules, monthly register (+ CSV), student month, school-wide day summary; behind `@RequiresModule(ATTENDANCE)` |
| `fee` | Fee heads, per-class installment plans (copy between years), student dues (created on `StudentEnrolledEvent`, or by "generate"), concessions, collection into gap-free receipts, cancellation, receipt PDF, collection/dues/class reports; behind `@RequiresModule(FEES)` |
| `exam` | Grading scales and bands (CBSE/TS presets), exams per year with weightage, papers per class + subject ("apply to all"), marks entry (`ExamAccess` limits teachers), publish lock, `ResultCalculator` (pure, unit-tested), section result sheet, student year results, report card PDFs; behind `@RequiresModule(EXAMS)` |
| `common.pdf` | `HtmlPdf`: shared openhtmltopdf rendering, DejaVu Sans fonts, escaping, ₹ formatting, embedded images (receipts and report cards) |
| `certificate` | Template mechanism shared with report cards (`TemplateType`, `TemplateFields`, `TemplateValues`, `TemplateDocument`, `TemplateService`: built-in default unless the school saved its own), issuing bonafide/TC with gap-free serials, data snapshot and stored PDF, register; behind `@RequiresModule(CERTIFICATES)` |
| `common.template` | `Placeholders`: `{{value}}` (escaped) and `{{{block}}}` (app-built HTML), validation against an allowed list, forbidden markup |
| `dashboard` | `GET /dashboard`: role-aware read model (`DashboardRepository`, one tenant-scoped SQL query per widget; reuses the attendance day summary and marks entry options); widgets of switched-off modules left out |

## 4. Tenant isolation (the most important design)

No user can ever see or change another school's data. It is enforced in layers, so
one mistake does not leak data:

1. **Tenant comes only from the validated JWT.** `JwtAuthFilter` sets
   `TenantContext` from the token's `schoolId` and clears it in `finally`. No endpoint
   accepts a school id from the request body, path or query.
   *Exception:* the login lookup, and `TenantContext.runAs(...)` for trusted
   server-side code (the dev seeder, and login bookkeeping after the password is
   verified). It is never called with request input.
2. **Automatic Hibernate filter.** `TenantEntity` declares `@FilterDef(autoEnabled = true,
   applyToLoadByKey = true)` with `school_id = :schoolId`, the parameter resolved from
   `TenantContext`.
   - Every session filters every tenant query, including load-by-id.
   - With no tenant in context, any tenant query **throws** rather than returning
     everything.
3. **Explicit scoping anyway.** Repositories take `schoolId` explicitly
   (`findByIdAndSchoolId`, `findBySchoolId…`). Plain `findById` is not used for tenant
   data. `TenantGuard.require(...)` exists for the same purpose.
4. **Native SQL always filters by school.** The student search (`StudentSearchRepository`),
   the login lookup and `SessionService` are plain SQL, and every joined table has
   `school_id = :schoolId`.
5. **Inserts are stamped.** `@PrePersist` sets `school_id` from the context, and throws
   if there is none or it doesn't match.
6. **404, not 403.** Records of another school are reported as not found, so their
   existence never leaks. References to another school's ids in a request (a class
   teacher, a section, a subject) are rejected the same way.
7. **Proven by tests.** `TenantFixtures.twoSchools()` builds School A and School B.
   Every module has a test where A tries to read, list, update, delete or reference
   B's records and gets 404 or an empty list.

The `school` table itself is not a tenant entity; it is the root of isolation and is
read by id from the context.

## 5. Authentication and authorization

- **Login:** `POST /api/v1/auth/login {schoolCode, username, password}`.
  - Usernames are unique per school, case-insensitive, and every school may have
    `principal`.
  - Wrong school, unknown user and wrong password all return the same 401. A dummy
    BCrypt hash is compared when the user doesn't exist, so timing doesn't reveal
    which usernames exist.
  - Inactive user or school: 403, shown only after a correct password.
  - On success: `last_login_at` is updated and a `LOGIN` audit entry written. Failed
    logins are audited as `LOGIN_FAILED` when the school exists.
- **Rate limit:** in memory. After 10 failures in 15 minutes per school code +
  username, further attempts get 429 until the oldest failure ages out. This works for
  a single instance only (fine for the MVP).
- **JWT:** HS256, 12-hour access token, no refresh token. Claims: `sub` (user id),
  `schoolId`, `roles`, `name`, `iss`.
- **Per-request session check (`SessionService`):** after verifying the token, one
  indexed SQL query confirms the user and school are still active and loads the
  *current* roles. Deactivation and role changes take effect on the next request, not
  after 12 hours.
- **Roles** (`PRINCIPAL`, `ADMIN`, `ACCOUNTANT`, `TEACHER`; a user can have several):
  - Controllers use `@PreAuthorize` with the shared expressions in `auth.Access`:
    `ANY_STAFF`, `PRINCIPAL`, `PRINCIPAL_OR_ADMIN`.
  - Services add ownership rules. A teacher only sees students enrolled in sections
    where they are the class teacher or have a subject assignment (`StudentAccess`,
    `TeacherScopeService`).
  - Sensitive student fields are filled in only for principal and admin.
- **Safety rules in user management:**
  - You cannot deactivate yourself.
  - You cannot remove the Principal role from, or deactivate, the last active
    principal.
  - Every change is audited.
- **CORS:** only `APP_CORS_ALLOWED_ORIGIN`. CSRF is off, because there are no cookies.
- **Security responses** (401/403) use the same `ProblemDetail` JSON as everything else.

## 6. Request lifecycle

```
HTTP request
 → Spring Security filter chain
     JwtAuthFilter: verify JWT → SessionService.refresh (active? current roles)
                    → SecurityContext + TenantContext        (cleared in finally)
     authorizeHttpRequests: public = health, login, /public/**; /api/** needs auth
 → ModuleGuard interceptor: @RequiresModule and module off for this school → 404
 → Controller (@PreAuthorize role check, @Valid DTO record)
 → Service (business rules, ownership checks, @Transactional, audit)
 → Repository (tenant filter on + explicit schoolId)
 → DTO record → JSON
Errors anywhere → GlobalExceptionHandler → ProblemDetail
```

## 7. Data model in use

The schema is `V1__core_schema.sql` (28 tables) plus `V2__certificates_and_report_card_notes.sql` (3 tables). V1 has never been edited after
running; changes would be new migrations. `ddl-auto=validate`, so Hibernate only
checks the schema and never alters it. Tables in use so far:

| Table | Entity | Notes |
|---|---|---|
| `school` | `School` | Tenant root. `settings` JSONB is mapped to the `SchoolSettings` record (defaults filled in) |
| `app_user`, `user_role` | `AppUser` (+ roles as an `@ElementCollection` of `Role`) | BCrypt hashes |
| `audit_log` | `AuditLog` | `details` JSONB as a map |
| `academic_year` | `AcademicYear` | Partial unique index enforces one current year |
| `school_class` | `SchoolClass` | Ordered by `display_order` |
| `section` | `Section` | Per year and class; optional class teacher |
| `subject` | `Subject` | Type: core / language / elective / co-curricular |
| `class_subject` | `ClassSubject` | Which subjects a class studies in a year, in order |
| `teacher_assignment` | `TeacherAssignment` | One teacher per section and subject |
| `holiday` | `Holiday` | Must fall within its year; one per date |
| `student` | `Student` | Aadhaar is encrypted (`bytea`); `aadhaar_last4` kept separately; `photo_url` holds the storage key |
| `guardian` | `Guardian` | 1–4 per student; one primary |
| `enrollment` | `Enrollment` | One per student per year; `ACTIVE / PROMOTED / DETAINED / LEFT` |
| `attendance` | `Attendance` | One row per enrollment per date; status `P/A/L/H` (`bpchar(1)`); `marked_by`, `marked_at` |
| `fee_head` | `FeeHead` | Inactive heads stay on old plans; a used head can only be deactivated |
| `fee_installment` | `FeeInstallment` | Year + class + head + name; amount and due date |
| `student_fee_due` | `StudentFeeDue` | One per enrollment per installment; amount, concession (+ reason), paid; DB checks keep 0 ≤ paid ≤ amount − concession |
| `fee_receipt` | `FeeReceipt` | Never deleted; `cancelled` + who, when, why |
| `fee_receipt_line` | `FeeReceiptLine` (`BaseEntity`, **no school_id**) | Only read by the ids of receipts already loaded through the tenant filter |
| `receipt_counter` | (plain SQL) | One row per school + year, locked `FOR UPDATE` |
| `grading_scale` | `GradingScale` | Name unique per school; can't be deleted while an exam uses it |
| `grading_band` | `GradingBand` (`BaseEntity`, **no school_id**) | min/max %, grade, grade point, remark; only read by the ids of scales already loaded through the tenant filter |
| `exam` | `Exam` | Per academic year; `display_order`, nullable `weightage`, `is_published` |
| `exam_subject` | `ExamSubject` ("paper") | One per exam + class + subject; max and pass marks, optional date |
| `mark` | `Mark` | One per paper + enrollment; `marks_obtained` null when absent, `is_absent`, `entered_by`, `updated_at`; no row = not entered |
| `co_scholastic_area` (V2) | `CoScholasticArea` | Per school; name unique (case-insensitive) |
| `report_card_note` (V2) | `ReportCardNote` | Per exam + enrollment: class teacher remark, `co_scholastic` JSONB (area id → grade) |
| `certificate_template` | `CertificateTemplate` | `type` BONAFIDE / TC / REPORT_CARD; a row exists only once the school saves its own version |
| `issued_certificate` | `IssuedCertificate` | Serial unique per school + type; `data` JSONB snapshot of every printed value; `pdf_url` = FileStorage key of the PDF |
| `certificate_counter` (V2) | (plain SQL) | One row per school + type, locked `FOR UPDATE` |

Foreign keys are mapped as plain `Long` ids, not JPA associations. Services batch-load
related rows into maps. This keeps every query explicit and tenant-scoped, and avoids
lazy-loading surprises.

Money is `NUMERIC(10,2)` ↔ `BigDecimal` (2 decimals, validated with `@Digits`). Dates are `LocalDate` and
timestamps `OffsetDateTime`. The JVM and JSON time zone is `Asia/Kolkata`.

## 8. Notable mechanisms

- **School settings:** `SchoolSettings` is a validated record (receipt/certificate
  prefix, weekly off days, module switches, report card options), stored as JSONB.
  It is also the request body for `PUT /school/settings`.
- **Module toggles:** controllers of optional modules carry
  `@RequiresModule(Module.FEES)` and similar. `ModuleGuard` answers 404 when that
  school has the module off. The frontend hides the matching menu entries using
  `school.modules` from `/auth/me`.
- **Files:** everything goes through `FileStorage` under keys
  `schools/{schoolId}/…`. `LocalFileStorage` refuses paths outside its root. Uploads
  are recognised by their content (`ImageTypes`: PNG, JPEG, WebP), never by name;
  SVG is rejected.
  - **School logos** are public, because the login page shows them:
    `/api/v1/public/schools/{code}/logo/{logo-<ts>.ext}`. Only the current file name
    is served, and it is cached for a year.
  - **Student photos** are private: `GET /students/{id}/photo` checks the same access
    rules as the student record. The frontend fetches it with the token and displays
    an object URL.
- **Aadhaar:** AES-256-GCM with a random IV per value, key from `APP_ENCRYPTION_KEY`.
  Numbers are validated with the Verhoeff check digit. The API never returns the full
  number, only `XXXX XXXX 1234` to principal and admin. On update, an omitted Aadhaar
  keeps the stored value and an empty string removes it.
- **Admission numbers:** the suggested next number is the highest trailing number plus
  one, keeping any prefix and zero padding (`SSV/2026/009` → `SSV/2026/010`).
- **Roll numbers:** a new placement takes the next free number in its section unless
  one is given. *Renumber* re-assigns 1..n alphabetically.
- **Promotion:** per section, in one transaction.
  - Selected students: the old enrollment becomes `PROMOTED` and a new one is created
    in the target section of a *later* year.
  - The rest become `DETAINED`, and can optionally be placed in the same class next
    year.
  - Refused if anyone already has a place in the target year.
- **CSV import:** template download, then a dry run that returns row-by-row errors
  with field names. The real import runs in one transaction through the same
  admission service: all rows or none. Up to 2,000 rows / 2 MB, UTF-8 with or without
  BOM.
- **Attendance:**
  - **Saving:** one section for one day at a time, bulk upsert. The save must list
    every student on that day's roster, otherwise 400 "class list has changed", so
    nobody is silently skipped. Unchanged marks are not rewritten, which keeps
    `marked_by` honest.
  - **Roster for a date:** enrollments of the section with
    `joined_on <= date <= left_on` (or not left). Students who joined later or left
    earlier drop out of that day.
  - **Blocked days**, checked in `AttendanceService.blockedReason`:
    - outside the section's year, or a future date: 400
    - a holiday or weekly off day, via `SchoolCalendarService` (holidays plus
      settings): 409
    - for teachers, any day but today: 403

    The sheet shows the same reason read-only.
  - **Who:** `AttendanceAccess` gives principal/admin every section; teachers get
    `TeacherScopeService` sections, with 404 outside them; accountants are refused
    by role.
  - **Percentage** = (P + ½H) ÷ days marked, one decimal; null when nothing is
    marked.
  - **Summary:** the school-wide day summary is one SQL query per day
    (`AttendanceReportRepository`). Sections with nobody enrolled that day are not
    counted as "to mark".
- **Fees:**
  - **Dues:** created from the class plan when a student is enrolled (the fee module
    listens to `StudentEnrolledEvent`, in the same transaction). "Generate dues" fills
    in what is missing for students already enrolled, and is idempotent.
  - **Plan edits:** changing an installment's amount updates its dues, and is refused if
    a student has paid or been given a concession above the new amount. Deleting one
    is refused once payments exist.
  - **Collecting:** one receipt for one student and one academic year. Each line is at
    most that due's balance (otherwise 409). Cheques need a number.
  - **Locking:** the dues are locked `FOR UPDATE` in id order, then the
    `receipt_counter` row is locked and incremented.
  - **Numbers** are `PREFIX/YY-YY/000123` (the receipt prefix from settings). A failed
    payment rolls its number back, and cancelled receipts keep theirs, so numbers are
    unique and gap-free. A test runs 8 concurrent payments.
  - **Cancelling** (principal/accountant, reason required) subtracts each line from its
    due and keeps the receipt.
  - **Receipt PDF:** an XHTML template rendered by openhtmltopdf with embedded DejaVu
    Sans. Every value is HTML-escaped; amounts appear as ₹1,23,456.00, with the amount
    in Indian words (lakh/crore). The school logo is embedded when it's an uploaded
    PNG/JPEG/WebP.
  - **Reports** are plain SQL (`FeeReportRepository`):
    - collection by day and mode (cancelled receipts excluded)
    - dues per student, with overdue being past the due date
    - expected vs. collected per class
- **Holiday on a marked day:** `HolidayService` asks the `HolidayAttendance` SPI (implemented by
  attendance) how many students are marked on the date.
  - With no decision in the request it returns 409 with `attendanceStudents` and `attendanceSections`
    (`ApiException` properties become `ProblemDetail` properties), and the screen asks.
  - `clearAttendance: true` deletes that school's marks for the day (audited); `false` keeps them.
- **Templates** (certificates and report cards):
  - Each school sees the built-in template (classpath `pdf-templates/*.html`) until it
    saves its own; "reset" deletes the school's row.
  - **Placeholders:** `{{value}}` is HTML-escaped. `{{{block}}}` is HTML built by the
    app: the logo, marks and co-scholastic tables, result summary, optional lines.
    No loops or conditions; anything repeated is a block.
  - **Checked before saving or previewing:**
    - every placeholder must be in `TemplateFields` for that type (with a hint when
      the wrong number of braces is used)
    - no `<script>`, `<iframe>`, `<object>`, `<embed>`, `<link>`, DOCTYPE, entities or
      processing instructions
    - at most 50,000 characters
    - it must render with sample data, otherwise "not valid XHTML: …"
  - `TemplateDocument` wraps the filled bodies with the page setup, the shared CSS
    classes and the school colour. A `<style>` block in a template is moved into the head.
  - **`HtmlPdf` loads only `data:` URIs** for every PDF (external-resource access
    control, before and after URI resolution). An edited template therefore cannot
    make the server fetch a URL (e.g. cloud metadata) or read a local file.
- **Dashboard:**
  - **Role shaping:** `DashboardService` builds a `DashboardDto` whose widgets are null
    when the role may not see them or the module is off.
    - Principal/admin: everything.
    - Accountant: students and fees.
    - Teacher: only `teacher` (their current-year sections from `TeacherScopeService`,
      whether today is marked, and unpublished-exam papers with marks missing,
      via `MarksService.entryOptions` so the assignment rules apply).
  - **Queries:** `DashboardRepository` holds the native SQL; every table is filtered by
    `school_id`. There is one query per widget:
    - students by class (ACTIVE enrollments of the current year)
    - attendance per day for the last 14 days (same P + ½H rule as the attendance module)
    - receipts per day, and month to date
    - dues outstanding and overdue (same rules as the dues report)
    - top 5 overdue classes
    - upcoming exams
    - recent admissions
    - Today's attendance reuses `AttendanceReportService.today`, so holidays and empty
      sections are handled the same way as on the summary page.
  - **Speed:** about 90 ms warm on the dev data (target < 300 ms; re-measure with the
    Phase 9 demo data).
  - **Charts** (Recharts): single series, one validated hue (`#2a78d6`, ≥ 3:1 on white),
    hairline grid, hover tooltips, no legend (the card title names the series), dates
    as DD-MM and amounts as compact ₹.
- **Certificates:**
  - **Rules:** a bonafide only for active students. A TC needs a leaving date and
    reason when the student is still active. Issuing the TC runs
    `StudentService.markTcIssued`: close the enrollment, set leaving details, set
    status `TC_ISSUED`. A second TC is refused, naming the first one's number.
  - **Serial** `PREFIX/BON|TC/0001` from `certificate_counter`, locked `FOR UPDATE`
    in the issuing transaction, so it is gap-free like receipts.
  - The printed values are snapshotted in `issued_certificate.data`, and the PDF is
    stored at `schools/{id}/certificates/…`.
  - **Re-download** serves the stored file. Only if that file is missing is it
    rebuilt from the snapshot with the current template.
  - **Preview** renders without taking a number. Issuing is audited (`ISSUE certificate`).
- **Report card notes:** class teacher remark and A/B/C co-scholastic grades per
  exam and student. Written by the section's class teacher or principal/admin;
  locked when the exam is published. The annual card uses the last published exam's
  notes.
- **Exams:**
  - **Access** (`ExamAccess`):
    - Principal/admin can do everything.
    - A teacher enters marks only for (section, subject) pairs in `teacher_assignment`.
    - A teacher sees results and report cards for sections they teach or class-teach.
    - Anything else returns 404, the same as another school's record.
  - **Marks** are saved per paper + section; only the rows sent are touched.
    - Blank marks with no absent flag deletes the row (back to "not entered").
    - Marks must be ≤ max.
    - Everything is refused with 409 once the exam is published; papers are locked too.
  - **`ResultCalculator`** is pure functions, unit-tested:
    - subject % = marks ÷ max (2 decimals, half-up)
    - grade = the band with the highest min % reached
    - absent = 0 and fails the subject; below pass marks fails it
    - any subject not entered makes the result incomplete (`passed = null`, no rank)
    - final % = Σ(w × %) ÷ Σw over weighted published exams, graded on the last
      weighted exam's scale; it passes only if every counted exam passed
    - rank is competition style (1, 2, 2, 4)
  - **Subject order** on sheets and cards follows Setup → Class subjects.
  - **Report cards:** one XHTML document, one page per student (`page-break-after`),
    rendered once.
    - It is for one published exam, or the "annual" card (every published exam plus
      the final).
    - School name, address, logo and primary colour come from the school record.
    - Rank and attendance % follow `settings.reportCard`; attendance only appears
      when the attendance module is on.
    - Printing writes an `ISSUE report_card` audit entry; publish/unpublish are audited.
- **Events:** `StudentEnrolledEvent` is published on every new enrollment (admission,
  promotion, enrolling later). The fee module listens to it to create fee dues.
- **Audit:** `AuditService.log(action, entity, id, details)`. Logged so far:
  - login, failed login
  - school profile, settings and logo changes
  - user create, update and role changes, password reset, activation
  - student create, update, status change, photo
  - section change, renumbering, promotion
  - receipt create and cancel
  - exam publish/unpublish, report card printing

## 9. API (all under `/api/v1`)

Public: `POST /auth/login`, `GET /public/schools/{code}/branding`, `GET /public/schools/{code}/logo/{file}`, `GET /actuator/health`.

| Area | Endpoints | Roles |
|---|---|---|
| Session | `GET /auth/me` | any staff |
| School | `GET /school` · `PUT /school/profile` · `PUT /school/settings` · `POST /school/logo` | read: any; write: principal |
| Years | `GET /academic-years` · `GET /academic-years/current` · `GET /academic-years/{id}` · `POST` · `PUT /{id}` · `POST /{id}/make-current` · `DELETE /{id}` | read: any; write: principal/admin |
| Classes | `GET /classes` · `POST` · `PUT /{id}` · `DELETE /{id}` | same |
| Sections | `GET /sections?academicYearId&classId` · `GET /{id}` · `POST` · `PUT /{id}` · `DELETE /{id}` | same |
| Subjects | `GET /subjects` · `POST` · `PUT /{id}` · `DELETE /{id}` | same |
| Class subjects | `GET /class-subjects?academicYearId&classId` · `PUT /class-subjects` (replace list) | same |
| Teacher assignments | `GET /teacher-assignments?academicYearId&sectionId&teacherId` · `PUT` (upsert) · `DELETE /{id}` | same |
| Holidays | `GET /holidays?academicYearId` · `POST` · `PUT /{id}` · `DELETE /{id}` | same |
| Users | `GET /users?q&role&active&page&size&sort` · `GET /{id}` · `POST` · `PUT /{id}` · `POST /{id}/reset-password` · `POST /{id}/deactivate` · `POST /{id}/activate` | principal |
| Staff picker | `GET /staff?role` | principal/admin |
| Students | `GET /students?q&academicYearId&classId&sectionId&status&page&size` · `GET /{id}` · `GET /next-admission-no` · `POST` · `PUT /{id}` · `POST /{id}/leave` · `POST /{id}/enrollments` · `POST /{id}/photo` · `GET /{id}/photo` | read: any (teachers scoped); write: principal/admin |
| Import | `GET /students/import/template` · `POST /students/import?dryRun` | principal/admin |
| Enrollment | `GET /sections/{id}/students?includeInactive` · `POST /sections/{id}/renumber` · `PUT /enrollments/{id}/section` · `POST /promotions` | read: any (teachers scoped); write: principal/admin |
| Attendance | `GET /attendance/sections?date` · `GET /attendance/sections/{id}?date` · `PUT /attendance/sections/{id}?date` · `GET /attendance/sections/{id}/register?month=yyyy-MM` · `GET …/register.csv?month` | principal/admin/teacher (teachers: own sections, today only for changes) |
| Attendance reports | `GET /attendance/students/{id}?month` · `GET /attendance/today?date` | student month: any staff who may see the student; today: principal/admin |
| Fee setup | `GET/POST /fees/heads` · `PUT/DELETE /fees/heads/{id}` · `GET /fees/plan?academicYearId&classId` · `POST /fees/plan` · `PUT/DELETE /fees/plan/{id}` · `POST /fees/plan/copy` · `POST /fees/dues/generate` | principal/admin/accountant |
| Fees | `GET /fees/students/{id}` · `PUT /fees/dues/{id}/concession` · `POST /fees/receipts` · `GET /fees/receipts?from&to&q&cancelled&page` · `GET /fees/receipts/{id}` · `GET /fees/receipts/{id}/pdf` · `POST /fees/receipts/{id}/cancel` | principal/admin/accountant; **cancel: principal/accountant** |
| Fee reports | `GET /fees/reports/collection?from&to` · `GET /fees/reports/dues?academicYearId&classId&sectionId&overdueOnly` · `GET /fees/reports/class-summary?academicYearId` | principal/admin/accountant |
| Grading scales | `GET /exams/grading-scales` · `POST` · `POST /exams/grading-scales/presets/{CBSE_9_POINT\|TS_CCE}` · `PUT /{id}` · `DELETE /{id}` | read: principal/admin/teacher; write: principal/admin |
| Exams | `GET /exams?academicYearId` · `GET /exams/{id}` · `POST` · `PUT /{id}` · `DELETE /{id}` · `POST /{id}/publish` · `POST /{id}/unpublish` | same |
| Papers | `GET /exams/{id}/papers?classId` · `PUT /exams/{id}/papers` (upsert class + subject) · `POST /exams/{id}/papers/apply` · `DELETE /exams/papers/{paperId}` | same |
| Marks | `GET /exams/{id}/entry-options` · `GET /exams/papers/{paperId}/marks?sectionId` · `PUT …/marks?sectionId` | principal/admin; teachers for their assigned subject + section |
| Co-scholastic areas | `GET /exams/co-scholastic-areas` · `POST` · `PUT /{id}` · `DELETE /{id}` | read: principal/admin/teacher; write: principal/admin |
| Remarks & grades | `GET /exams/{id}/notes?sectionId` · `PUT …/notes?sectionId` | principal/admin; the section's class teacher |
| Report card template | `GET /exams/report-card-template` · `PUT` · `DELETE` (reset) · `POST …/preview` | principal |
| Certificates | `GET /certificates?type&from&to&q&page&size` · `GET /certificates/{id}/pdf` · `GET /certificates/students/{id}` · `POST /certificates/students/{id}/preview` · `POST /certificates/students/{id}` (issue) | principal/admin |
| Certificate templates | `GET /certificates/templates/{BONAFIDE\|TC}` · `PUT` · `DELETE` (reset) · `POST …/preview` | principal |
| Dashboard | `GET /dashboard` (shape depends on role: principal/admin everything; accountant students + fees; teacher their sections, attendance to take, marks to enter) | any staff |
| Results | `GET /exams/result-sections?academicYearId` · `GET /exams/{id}/results?sectionId` · `GET /exams/students/{id}/results?academicYearId` · `GET /exams/report-cards.pdf?sectionId&examId&studentId` | principal/admin; teachers for sections they teach or class-teach |

Conventions:
- Requests and responses are Java `record` DTOs; entities are never exposed.
- Bean Validation runs on requests. Field errors come back as
  `ProblemDetail.errors` (`{"student.firstName": "First name is required"}`).
- `PageResponse {content, page, size, totalElements, totalPages}` for paged lists.
  Page size is capped at 100, and user sorting is restricted to an allow-list of
  columns.
- Status codes: 400 validation, 401 not signed in, 403 role, 404 missing or another
  school's, 409 conflict or still in use, 429 login rate limit.

## 10. Frontend

```
src/
  api/          client.ts (one axios instance: adds the JWT; 401 → /login; error helpers)
                auth.ts school.ts academic.ts users.ts students.ts attendance.ts fees.ts exams.ts certificates.ts dashboard.ts (typed endpoints)
  auth/         tokenStore (memory + sessionStorage), AuthProvider/useAuth (me query),
                guards (RequireAuth, RequireRole, RequireModule)
  layout/       AppShell (sidebar + header, mobile drawer), menu.tsx (role/module filtered),
                BrandTheme (school colour → Ant Design ConfigProvider)
  features/     auth/ dashboard/ settings/ students/ attendance/ fees/ exams/ certificates/   (pages + small feature components)
  components/   PageHeader, SchoolLogo, useFormErrors (backend field errors → form)
  utils/        format (DD-MM-YYYY, ₹ en-IN), download (save Blob), csv, openPdf (authed PDF in a new tab)
```

- **Server state:** only TanStack Query, with shared query keys in
  `features/settings/queries.ts`. Mutations invalidate by key prefix. No ad-hoc
  fetching in components.
- **Session:** the token lives in memory and `sessionStorage` (per tab, so two tabs
  can be two schools). The last school code is remembered in `localStorage`.
- **Branding:** the login page shows a school's logo, name and colour as soon as a
  valid code is typed. After login the whole app is themed from `/auth/me`.
- **Forms:** Ant Design `Form` with rules matching the backend. A failed save maps
  backend field errors onto the form, and anything else becomes a toast. Modal forms
  include a hidden submit button, so Enter submits.
- **Mobile:** the sidebar becomes a drawer below the `lg` breakpoint; tables scroll
  sideways; forms reflow to one column.
- **Routes:**
  - `/login`
  - `/` (dashboard)
  - `/students`, `/students/new`, `/students/:id`, `/students/:id/edit`,
    `/students/promotion`, `/students/import`
  - `/attendance` (`?section=&date=`), `/attendance/register`, `/attendance/today`
  - `/fees/collect` (`?student=`), `/fees/receipts`, `/fees/reports`, `/fees/setup`
  - `/exams` (marks entry, `?exam=&paper=&section=`), `/exams/results` (`?exam=&section=`), `/exams/remarks`, `/exams/setup`, `/exams/template`
  - `/certificates` (register), `/certificates/templates` (`?type=TC`)
  - `/settings/{school,preferences,years,classes,subjects,class-subjects,assignments,holidays,users}`

  Every route has a role guard that matches its API.

## 11. Configuration

`application.yml` has three profiles:

| Profile | Behaviour |
|---|---|
| default (`dev`) | Imports `../.env`; safe local defaults; runs `DevSeeder` (2 schools) |
| `prod` | Every value from the environment, no defaults |
| `test` (tests only) | Testcontainers datasource, test secrets, uploads under `target/test-files` |

Environment variables (see `.env.example`):
- **Database:** `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- **App:** `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`, `APP_CORS_ALLOWED_ORIGIN`
- **Secrets:** `JWT_SECRET` (≥ 32 bytes), `APP_ENCRYPTION_KEY` (base64, 32 bytes)
- **Files and dev:** `FILE_STORAGE_LOCAL_DIR`, `DEV_SEED_PASSWORD`

Other settings:
- Multipart limit 2 MB per file.
- JVM time zone `Asia/Kolkata`, set in `main()`; tests set it via surefire.
- `.gitattributes` forces LF line endings so Flyway checksums stay stable on Windows.

## 12. Testing strategy

- **121 backend tests.** `./mvnw clean test` runs them against a real Postgres 16 in
  Testcontainers. One Spring context is shared, plus one extra for the module-guard
  probe.
- **`IntegrationTest`** base class: MockMvc, request helpers (`getAs`, `postAs`,
  `putAs`, `deleteAs`, `idOf`), and fixtures.
- **`TenantFixtures`:** fresh schools with unique codes per test, users with tokens,
  `inSchool(school, …)` to create data as that school.
- **`AcademicFixtures`:** a year, class, section and subject set up in one call.
- **Coverage so far:**
  - login rules and rate limit
  - tenant filter behaviour, including "no tenant → throws"
  - branding and logo validation, settings and module guard
  - every academic endpoint, including isolation
  - user management, including immediate revocation
  - admission, Aadhaar encryption at rest, masking per role
  - teacher scope, search, section change, leaving, promotion and detention
  - CSV import, including rollback
  - attendance: marking and correcting, whole-class rule, holiday, off-day,
    future and other-year blocks, teacher scope and today-only, accountant refused,
    register, CSV, student month, day summary, module off, School A vs B
  - production safety check (dev seed password and profile)
  - fees: dues on admission, idempotent generate, partial pay, overpay 409, cheque number,
    cancel restores dues, receipt numbering, concessions, plan edits blocked by
    payments, copy plan, reports, roles (teacher refused, admin can't cancel), module
    off, 8 concurrent payments (gap-free numbers, no lost update), School A vs B on
    every fee endpoint, amount in words, ₹ glyph in the bundled font
  - exams: apply-to-all papers, teacher limited to their subject (entry options, sheet,
    save, results, result sections), marks > max refused, incomplete vs. complete
    results, absent and below-pass fail, rank, clearing a mark, publish locks marks and
    papers, report cards only when published (section, one student, annual),
    unpublish reopens, weighted final over published exams only, grading-scale rules
    (preset twice, must start at 0 %, in use), accountant refused, module off, School A
    vs B on every exam endpoint
  - report card notes: class teacher only (other teacher and other section 404), grades
    upper-cased, locked when published, printed on exam and annual cards, own template
    printed, wrong braces refused, School A vs B
  - certificates: preview without a number, serials /BON/0001 then 0002, stored PDF
    text, register search, roles (admin issues, accountant and teacher refused), TC
    needs a leaving date, TC sets TC_ISSUED and closes the enrollment, second TC and
    bonafide after TC refused, TC for a student who already left, template rules
    (unknown placeholder, bad XHTML, script, wrong braces), external image never
    fetched, issued PDF unchanged after reset, admin cannot edit templates, module
    off, School A vs B
  - holiday on a marked day: asks (409 with counts), clears only this school's
    marks, or keeps them
  - dashboard: every number for the principal (students by class, today's attendance
    before and after marking, collected today and this month, outstanding and overdue,
    top overdue classes, 14-day trends, upcoming exams, recent admissions); accountant
    and teacher shapes; another teacher sees nothing; switched-off modules drop their
    widgets; School A counts only its own data; no academic year means empty
  - unit tests for Verhoeff, AES-GCM, CSV and `ResultCalculator` (grade boundaries such
    as 90.99 → A2 and 91 → A1, absent, incomplete, weighting, ranking)
- **Frontend:** `tsc` and `oxlint` must be clean.
- **Phase-end checks:**
  - The acceptance flow is run through the API, and the PDFs are rendered and inspected.
  - From Phase 7, every phase also ends with a browser check of its main flows at
    desktop and ≈360px phone width (CLAUDE.md).
  - Phase 7's browser check was done by hand by the product owner; Phase 8's was
    automated in Chrome (desktop, and the app in a 360 px frame).

## 13. Decisions and deviations

| Decision | Why |
|---|---|
| Repo root is the project folder (no `school-erp/` subfolder) | The files were already there |
| Hibernate filter auto-enabled for every session, including load-by-id | Isolation without relying on every developer remembering to enable it; fails closed |
| Roles as an element collection, not a `UserRole` entity | `user_role` is just (user_id, role) |
| Per-request DB check of user/school status and roles | So deactivation is immediate; costs one indexed query per request |
| FKs as `Long` ids, not JPA associations | Explicit, tenant-scoped queries; no lazy-loading leaks |
| Full Aadhaar never returned, even to principals | Simplest way to guarantee it never leaks; last 4 digits are enough on screen |
| Logos public, student photos private | Logos are needed before login; photos are personal data |
| New admission takes the next roll number; alphabetical renumber is a separate action | Avoids reshuffling a section mid-year |
| CSV import all-or-nothing | No half-imported files to clean up |
| Social category includes `OBC` (column comment listed GEN/BC/SC/ST/OTHER) | CBSE schools use OBC; no DB constraint is affected |
| Admission form ignores empty contact rows | The default Mother row must not block saving when only one parent is known |
| `DevSeeder` is `@Profile("dev & !prod")`; `ProductionSafetyCheck` stops a prod start if `DEV_SEED_PASSWORD` is set or `dev` is also active | `dev` is the default profile, so a misconfigured server must fail loudly rather than seed demo logins |
| Social category has `BC_A`..`BC_E` (shown BC-A..BC-E) alongside `BC`; CSV accepts "BC-A", "bc a", and "OC" as General | Telangana and AP schools record BC sub-groups; other states use plain BC |
| Section delete asks other modules through `SectionUsageCheck` (students: "N students are enrolled in Class 5 – A …") | A clear message without the academic module depending on the student module |
| Attendance saves the whole section at once and rejects partial lists | The screen defaults everyone to Present; a partial save would leave students silently unmarked |
| Teachers change only today; principal/admin correct past days | Matches the plan, and keeps the register trustworthy after the day ends |
| The day summary counts only sections that had students that day | An empty or not-yet-started section is not "pending" |
| Fee dues are locked `FOR UPDATE` while collecting or cancelling | Two payments on the same due could otherwise overwrite each other's `paid_amount` (found while writing the concurrency test) |
| One receipt per student per academic year | `fee_receipt` has one `enrollment_id` and `academic_year_id`; arrears from last year go on their own receipt |
| Receipt lines have no `school_id` and are not a `TenantEntity` | The V1 table has no such column; lines are only reached through receipts already filtered by school |
| Fee report CSVs are built in the browser from the report data | No extra endpoints; the data on screen is what is exported |
| Report cards and certificates share one template mechanism; a school's template is a row only once it edits it | No seeding for existing or new schools; "reset" is a delete; the default ships with the code |
| Placeholders without loops or conditions; tables are app-built blocks | Simple enough for a principal to edit, and nothing in a template can run code |
| Issued certificates keep the PDF file, not just the data | Re-downloads are identical even after the template or student details change |
| Recharts for dashboard charts (the first chart library) | PLAN names it; Ant Design has no charts; SVG, React 19 support, tree-shakeable |
| One dashboard endpoint, shaped by role | One request per screen load; the backend decides what each role may see, the same as everywhere else |
| Dashboard SQL lives in its own read-only repository, not in each module | One place to review for tenant scoping and speed; the modules' write rules are untouched |
| "Active students" counts ACTIVE enrollments only | Promoted, detained-and-moved or left students are not in the class any more; the attendance roster for the year may still list promoted ones |
| Certificate and report card templates are principal-only | They are school settings (CLAUDE.md: admin does everything except settings and users) |
| Co-scholastic grades are free text up to 5 characters (the screen offers A/B/C) | CBSE uses A/B/C; some state schools use other letters |
| Grading presets are added with one click instead of being seeded into every school | Schools use one or the other; nothing to delete for the unused one |
| `grading_band` has no `school_id` and is not a `TenantEntity` | The V1 table has no such column; bands are only read through scales already filtered by school |
| Marks not entered are "incomplete", not 0 | A half-entered sheet must not show students as failing |
| Marks save sends only changed rows; others are untouched | Two teachers (or tabs) on different rows don't overwrite each other |
| Annual final graded on the last weighted exam's scale | An exam per scale is rare; one rule keeps the final simple |
| Sidebar menu is not mounted while the mobile drawer is closed | Inside a collapsed `Sider`, antd turns the inline menu into popups that opened over the page |

## 14. Known limitations / technical debt

- Login rate limiting is in memory, so it would need a shared store to run more than
  one instance.
- **Login lockout can be abused.** The limit is per school code + username, not per
  client. Anyone who knows a school code and a username (say, `principal`) can make
  10 wrong attempts and lock that real user out for 15 minutes, again and again. It
  is acceptable for the demo. Before real schools rely on it, add per-IP limits
  (Caddy or the app), a CAPTCHA after a few failures, and alerting on repeated
  lockouts.
- **The encryption key must be backed up separately.** Aadhaar numbers are encrypted
  with `APP_ENCRYPTION_KEY`; without that exact key they cannot be recovered.
  Database backups alone are therefore not enough.
  - Store the key in a password manager or secret store, apart from the database
    backups, so one leak doesn't expose both.
  - Restore tests must include it.
  - There is no key rotation yet: one key, and no key id in the ciphertext.
    Rotating would need a migration that re-encrypts every row.
- No refresh tokens; users sign in again after 12 hours.
- `FileStorage` has only the local implementation. R2 arrives in Phase 10, along with
  backups and HTTPS.
- Student list avatars show initials only (photos load on the profile), to avoid one
  request per row.
- The admission form has a photo upload but no other documents (birth certificate,
  previous TC) yet.
- The IDE's Java extension writes into `backend/target/classes` without Lombok
  support, which can break test runs. `./mvnw clean test` fixes it.

- **Attendance history follows the enrollment.** A section change moves the student's
  existing enrollment, so their earlier marks this year appear under the new
  section's register. A per-section history would need a separate placement history.
- **Attendance changes are not in the audit log.** Only the latest change is kept,
  via `marked_by` and `marked_at`; earlier values are overwritten.

- **Seeded placeholder logos don't print.** The SVG data-URI logos of the dev schools are
  not embedded in PDFs (openhtmltopdf would need its SVG add-on); uploaded PNG/JPEG/WebP
  logos are. Phase 9 seeds PNG logos for this reason.
- **No fee refunds or advance payments.** Money only goes against existing dues; a
  mistake is corrected by cancelling the receipt.

- **Exams:**
  - **Per-subject remarks are not printed.** `mark.remarks` is stored but not shown;
    the class teacher's overall remark is.
  - **Marks changes are not audited one by one.** `entered_by` and `updated_at`
    are kept; publish is audited.
  - **Report card printing is synchronous.** A section of ~40 renders in about a
    second; whole-school printing would need a background job.
  - **Results include students who were promoted or detained**, but not those who
    left, because the roster is the enrollment in that year.

- **Certificates:**
  - **Only bonafide and TC.** Conduct and study certificates (in the V1 comment)
    would be two more `TemplateType`s.
  - **No cancelling or reissuing.** A TC issued by mistake needs a database fix.
  - **No check on fee dues before a TC.** The TC does not show outstanding fees.
  - **The template editor is a plain textarea.** The preview is a PDF in an iframe,
    which phones may not display; it is a desktop task.

- **Dashboard:**
  - **Fixed 14-day window.** The charts have no date picker yet.
  - **Charts are light-only.** The app has no dark mode yet.
  - **The teacher's "marks to enter" checks every unpublished exam's papers one by one.**
    That is fine for a teacher's handful of sections, but it would want one query if
    exams pile up.
  - **Fee trend shows only real receipt dates.** Receipts are always dated the day they
    are collected, so the demo chart fills up as fees are collected (or via the
    Phase 9 seeder).

## 15. What's next

Phase 9 is **demo data**:
- a `demo`-profile seeder, run by a CLI flag and safe to repeat
- three fictional schools (TS Telugu medium, CBSE English medium, state English medium)
  with their own PNG logos, colours, grading and fee structures
- Nursery–Class 10, 2 sections each, 300–500 students per school
- two months of attendance, fee payments with partial dues, a published exam with
  marks, and some issued certificates
- logins per role, documented in the README; only fake data

That lets the dashboard's < 300 ms target be re-measured at realistic size.
Phases 10–11 follow the order in `PLAN.md`.
