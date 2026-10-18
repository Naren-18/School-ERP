# PLAN.md — School ERP MVP build plan

Follow the rules in `CLAUDE.md` throughout. Work through phases in order.
Tick each checkbox as it is completed. Each phase ends with its **Acceptance**
checks passing and a commit.

Target: a hosted demo with 3 fictional schools that a school owner can click
through on a laptop and a phone. Rough timeline: 4 weeks part-time.

---

## Phase 0 — Repository & local environment

- [x] Create the repo layout from `CLAUDE.md`
- [x] `docker-compose.yml` with Postgres 16 (db `school_erp`, user/pass from `.env`)
- [x] `.env.example` listing every env var; `.env` git-ignored
- [x] Backend: Spring Boot project (Web, Data JPA, Security, Validation, PostgreSQL, Flyway, Lombok, Testcontainers)
- [x] Place `V1__core_schema.sql` in `backend/src/main/resources/db/migration/`
- [x] `application.yml` with profiles `dev` and `prod`; `ddl-auto=validate`; timezone Asia/Kolkata
- [x] Frontend: `npm create vite@latest frontend -- --template react-ts`, add antd, @tanstack/react-query, react-router-dom, axios, dayjs
- [x] Vite dev proxy `/api` → `http://localhost:8080`
- [x] README with "how to run locally" (3 commands max)

**Acceptance:** `docker compose up -d`, start backend → Flyway applies V1 (28 tables).
Frontend dev server shows a placeholder page. Backend `GET /actuator/health` is UP.

---

## Phase 1 — Foundations: tenancy, auth, errors, audit

Backend:
- [x] `common`: `TenantEntity` (`@MappedSuperclass`, `schoolId`, `@FilterDef`/`@Filter` on `school_id`), `ProblemDetail` exception handler, `NotFoundException`, pagination DTO
- [x] `tenant`: `TenantContext` (ThreadLocal), Hibernate filter enabled per request, `@PrePersist` sets `schoolId` (throws if missing), `TenantGuard.require(entity)`
- [x] `auth`: `AppUser` entity + `Role` enum (`user_role` mapped as an element collection), BCrypt, `POST /api/v1/auth/login {schoolCode, username, password}` → JWT
- [x] JWT filter: validate token, set `SecurityContext` + `TenantContext`, clear in `finally`
- [x] Reject login if school or user is inactive; update `last_login_at`
- [x] `GET /api/v1/auth/me` → user, roles, school summary
- [x] `GET /api/v1/public/schools/{code}/branding` → name, logo, primary color (for the login page)
- [x] `audit`: `AuditService.log(action, entity, entityId, details)`; log logins
- [x] Basic login rate-limit (e.g. 10 failed attempts / 15 min per school+username, in-memory)
- [x] Minimal dev seed (Flyway `R__dev_seed.sql` or a `dev`-profile `CommandLineRunner`): 2 schools, a principal in each

Frontend:
- [x] Login page: school code, username, password; shows school logo/name once a valid code is entered
- [x] Auth context (token in memory + `sessionStorage`), axios interceptor, 401 → login
- [x] App shell: sidebar (menu items filtered by role), header with school name/logo, logout
- [x] Theme from school primary color via `ConfigProvider`
- [x] Empty dashboard page

Tests:
- [x] Login success/failure, inactive user, wrong school code
- [x] **Tenant isolation test harness**: helper that creates School A + School B with data and returns tokens for both — reused by every later phase
- [x] Same username in two schools logs into the correct school

**Acceptance:** Log in as `principal` of School A and School B in two browsers;
each sees only its own name, logo and color.

---

## Phase 2 — School settings & academic setup

Backend:
- [x] School profile + settings (PRINCIPAL only). `settings` JSON shape, validated in Java:
  ```json
  {
    "receiptPrefix": "SVS",
    "certificatePrefix": "SVS",
    "weekOffDays": ["SUNDAY"],
    "modules": { "attendance": true, "fees": true, "exams": true, "certificates": true },
    "reportCard": { "showRank": false, "showAttendance": true }
  }
  ```
- [x] Logo upload via `FileStorage` (local in dev)
- [x] CRUD: academic years (exactly one current), classes (ordered), sections (per year, class teacher), subjects
- [x] Class-subject mapping per year; teacher assignments (section + subject → teacher)
- [x] Holidays CRUD
- [x] User management (PRINCIPAL): create staff, assign roles, reset password, deactivate

Frontend:
- [x] Settings pages: school profile & branding, modules toggles, academic years, classes & sections, subjects, class-subject mapping, teacher assignments, holidays, users
- [x] Disabled modules disappear from the sidebar and their APIs return 404 (`@RequiresModule` + `module` on menu entries; applied as module pages arrive in phases 4-7)

Tests:
- [x] Isolation tests for every new endpoint
- [x] Only one current academic year; ADMIN cannot change settings or users

**Acceptance:** A principal can set up a new school's year, classes, sections,
subjects and staff entirely from the UI.

---

## Phase 3 — Students, guardians & enrollment

Backend:
- [x] Student CRUD with guardians; admission number unique per school (auto-suggest next number)
- [x] Aadhaar encryption converter; sensitive fields hidden for non-ADMIN/PRINCIPAL
- [x] Photo upload
- [x] Enrollment on admission (year + section + roll no); roll-no auto-assign option
- [x] Section change within a year
- [x] Mark student LEFT (with date & reason) → enrollment LEFT
- [x] Year-end promotion: for a source section, promote selected students to a target section in the next year; others DETAINED
- [x] Search & filter: name, admission no, guardian phone, class/section, status; paginated
- [x] CSV import of students (template download + validation report) — schools will ask for bulk import
- [x] Generate fee dues for a student on enrollment (hook for Phase 5; no-op until then) — `StudentEnrolledEvent` published on every new enrollment

Frontend:
- [x] Student list with filters, search, pagination
- [x] Admission form (multi-section: student, guardians, previous school, documents) with photo
- [x] Student profile page with tabs: Details, Attendance, Fees, Marks, Certificates (tabs filled in later phases)
- [x] Promotion wizard
- [x] CSV import screen

Tests:
- [x] Isolation; TEACHER sees only students of their sections; sensitive-field masking per role
- [x] Promotion creates correct next-year enrollments

**Acceptance:** Admit a student, find them by guardian phone, change section, promote to next year.

---

## Phase 4 — Attendance

Backend:
- [x] Mark attendance for a section and date (bulk upsert), default all Present
- [x] Teacher may only mark sections they are class teacher of (or assigned to); edits allowed same day for teachers, any day for ADMIN/PRINCIPAL
- [x] Block marking on holidays/week-off days
- [x] Reports: section daily sheet, student monthly summary, school-wide today % by class

Frontend:
- [x] **Mobile-first** marking screen: pick section → list with big P/A/L toggles → save
- [x] Monthly register view (students × days grid) with export to CSV
- [x] Student profile → Attendance tab with monthly calendar

Tests:
- [x] Isolation; teacher cannot mark other sections; holiday blocked

**Acceptance:** On a phone, a teacher marks a 40-student section in under a minute.

---

## Phase 5 — Fees & receipts

Backend:
- [x] Fee heads CRUD; fee plan = installments per class per year (head, name, amount, due date)
- [x] "Copy plan from previous year"
- [x] Generate `student_fee_due` rows for all enrolled students of a class (idempotent) and on new enrollment
- [x] Concessions per due row (amount + reason)
- [x] Collect payment: choose dues, amounts (partial allowed), mode, reference → receipt
  - single transaction; `receipt_counter` row locked `FOR UPDATE`; receipt no `PREFIX/YY-YY/000123`
  - update `paid_amount`; never exceed net due
- [x] Cancel receipt (PRINCIPAL/ACCOUNTANT, reason required) → reverses `paid_amount`, audit logged
- [x] Receipt PDF (school logo, details, amount in words in Indian format)
- [x] Reports: day collection by mode, dues list (class/section, overdue only), student ledger, collection vs. expected per class

Frontend:
- [x] Fee setup pages (heads, plan per class)
- [x] Collect fee screen: search student → outstanding dues → pay → print/download receipt
- [x] Student profile → Fees tab (ledger)
- [x] Reports pages with CSV export

Tests:
- [x] Concurrent receipt creation yields unique gap-free numbers
- [x] Overpayment rejected; cancel restores dues; isolation

**Acceptance:** Collect a partial payment, print the receipt, cancel it, and see the dues report update correctly.

---

## Phase 6 — Exams, marks & report cards

Backend:
- [ ] Grading scales + bands CRUD; seed two presets: "TS state CCE" and "CBSE 9-point"
- [ ] Exams per year (name, order, weightage, grading scale)
- [ ] Exam subjects per class (max marks, pass marks, date); "apply to all classes" helper
- [ ] Marks entry per section + exam subject (bulk upsert, absent flag); teachers only for their assigned subject+section
- [ ] Publish/unpublish exam (locks marks entry when published)
- [ ] Result computation service: per subject %, grade from band, totals, overall %/grade, weighted final result across exams, pass/fail
- [ ] Report card PDF per student and bulk per section (one PDF), using a per-school HTML template

Frontend:
- [ ] Exam setup pages
- [ ] Marks entry grid (students × one subject), keyboard-friendly, works on phone
- [ ] Section result sheet (students × subjects) with export
- [ ] Report card preview + download; student profile → Marks tab

Tests:
- [ ] Grade calculation edge cases (boundaries, absent, weightage)
- [ ] Teacher restriction; published exam is locked; isolation

**Acceptance:** Set up FA1 for Class 5, enter marks for a section, publish, download all report cards as one PDF.

---

## Phase 7 — Certificates

Backend:
- [ ] Default templates per school for BONAFIDE and TC (seeded, editable HTML with placeholders `{{student.fullName}}`, `{{school.name}}`, etc.)
- [ ] Issue certificate: serial number per school+type, snapshot data into `issued_certificate.data`, generate PDF
- [ ] Issuing a TC sets student status `TC_ISSUED`
- [ ] Re-download previously issued certificates

Frontend:
- [ ] Issue certificate dialog from student profile (type, purpose, preview)
- [ ] Certificates register (list, filter, re-download)
- [ ] Template editor (textarea + live preview is enough for MVP)

**Acceptance:** Issue a bonafide and a TC; both PDFs carry the school's own logo and numbering.

---

## Phase 8 — School dashboard

Backend:
- [ ] `GET /api/v1/dashboard` (role-aware): total active students (by class), today's attendance %, sections not yet marked today, fee collected today / this month, total outstanding dues, top 5 overdue classes, upcoming exams, recent admissions
- [ ] Single efficient query per widget; must load < 300 ms with demo data

Frontend:
- [ ] Dashboard with stat cards + 2–3 charts (Ant Design charts or Recharts)
- [ ] TEACHER sees a simplified dashboard: their sections, attendance pending, marks pending

**Acceptance:** Dashboard is the first screen after login and looks good on laptop and phone.

---

## Phase 9 — Demo data

- [ ] `demo` profile seeder (separate from dev seed), idempotent, run via a CLI flag
- [ ] 3 fictional schools with distinct logos/colors/settings:
  1. State board, Telugu medium (TS CCE grading, FA1–4/SA1–2, Telugu/Hindi/English)
  2. CBSE, English medium (9-point grading, Term 1/Term 2)
  3. State board, English medium (different fee structure, transport fee head)
- [ ] Each: current year, Nursery–Class 10, 2 sections each, ~300–500 students with realistic Telugu/Indian names, guardians, phone numbers (fake, e.g. 9xxxxxxxxx pattern)
- [ ] 2 months of attendance history, fee payments with realistic partial dues, one published exam with marks, a few issued certificates
- [ ] Logins per school: principal, admin, accountant, 2 teachers (documented in README; password from env)
- [ ] **Only fake data. No real student information anywhere.**

**Acceptance:** Fresh database + seeder → every screen in every school looks populated and realistic.

---

## Phase 10 — Production deployment

- [x] Backend multi-stage `Dockerfile` (build with Maven, run on JRE 21 slim, `-XX:MaxRAMPercentage=60`)
- [x] Frontend built to static files, served by Caddy
- [x] `docker-compose.prod.yml`: app, postgres (volume), caddy (auto HTTPS, `/api` → app, everything else → SPA with fallback to `index.html`)
- [x] Prod config from env; `FileStorage` R2 implementation
- [x] Nightly `pg_dump` → compressed → uploaded to R2, keep 14 days; documented restore steps (test a restore once)
- [x] Health check endpoint for uptime monitoring
- [x] Deployment script / Makefile: `make deploy` (pull, build, migrate, restart)
- [x] Gzip/Brotli + long cache headers for hashed frontend assets

**Acceptance:** App runs at the real domain over HTTPS; a restore from backup works.

---

## Phase 11 — Demo polish

- [x] Loading skeletons, empty states, friendly error messages everywhere
- [x] Consistent page titles, breadcrumbs, Indian number/date formats
- [x] Test every demo flow on a ~₹15k Android phone over mobile data
- [x] Fix anything slower than 1 second
- [x] Write `DEMO.md`: the exact click path (login → dashboard → admission → attendance on phone → fee collection + receipt → report cards → TC → switch school to show a completely different setup)

**Acceptance:** Full demo path runs end-to-end on production with no errors.

---

## Out of scope for the MVP (roadmap slide only)

Parent/student app, SMS/WhatsApp notifications, online payment gateway, transport,
library, hostel, payroll/HR, timetable, homework, multi-school group admin,
self-service school onboarding UI.
