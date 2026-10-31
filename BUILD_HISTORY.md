# Build history

How the School ERP was built from Phase 0 to today, phase by phase: what each
phase delivered, how it works, the decisions taken, the problems found along the
way, and how they were fixed.

[`ARCHITECTURE.md`](ARCHITECTURE.md) describes the system **as it is now** (a reference);
this file tells **how it got there** (the story). [`PLAN.md`](PLAN.md) is the original
plan, [`CLAUDE.md`](CLAUDE.md) the rules every phase followed, and [`README.md`](README.md)
explains how to run it.

---

## Contents

1. [The product in one page](#1-the-product-in-one-page)
2. [How we worked](#2-how-we-worked)
3. [The shape of the system](#3-the-shape-of-the-system)
4. [Timeline](#4-timeline)
5. [Phase 0: repository and local environment](#phase-0-repository-and-local-environment)
6. [Phase 1: foundations (tenancy, auth, errors, audit)](#phase-1-foundations-tenancy-auth-errors-audit)
7. [Phase 2: school settings and academic setup](#phase-2-school-settings-and-academic-setup)
8. [Phase 3: students and enrollment](#phase-3-students-and-enrollment)
9. [Phase 4: attendance](#phase-4-attendance)
10. [Phase 5: fees and receipts](#phase-5-fees-and-receipts)
11. [Phase 6: exams, marks and report cards](#phase-6-exams-marks-and-report-cards)
12. [Phase 7: certificates and editable templates](#phase-7-certificates-and-editable-templates)
13. [Phase 8: school dashboard](#phase-8-school-dashboard)
14. [Phase 9: demo data](#phase-9-demo-data)
15. [Hosted demo deployment](#hosted-demo-deployment)
16. [Dashboard charts (after the demo went live)](#dashboard-charts-after-the-demo-went-live)
17. [Cross-cutting threads](#17-cross-cutting-threads)
18. [Lessons learned](#18-lessons-learned)
19. [Where it stands and what's next](#19-where-it-stands-and-whats-next)

---

## 1. The product in one page

**What:** a hosted, multi-tenant school management system for Indian schools,
built as an MVP for sales demos. Many schools share one deployment, and each school
is completely isolated: its own logins and its own data. No user can ever see or
touch another school's data. Schools are created by us (a seed or onboarding
script), not through the UI.

**Who uses it** (one user can hold several roles):

| Role | Does |
|---|---|
| Principal | Everything in the school, including settings, users and templates |
| Admin | Everything except users and school settings |
| Accountant | Fees, receipts and fee reports; reads students |
| Teacher | Attendance and marks for their own sections and subjects; reads those students |

**What a school can do today:**
- Set up the academic year: classes, sections, subjects, teachers, holidays.
- Admit students (with guardians, photo, encrypted Aadhaar), import them from CSV,
  move them between sections, mark them left, promote them at year end.
- Take attendance on a phone, see monthly registers and the school-wide day summary.
- Plan fees per class, collect payments, print receipts with gap-free numbers,
  cancel receipts, see dues and collection reports.
- Run exams with grading scales, enter marks on a phone, publish results, print
  report cards for one student, a section, or the whole year.
- Issue bonafide certificates and transfer certificates (TCs), and edit the
  certificate and report card templates.
- See a role-aware dashboard with stats and charts.

**Demo:** three fictional schools (`vjhs`, `lvps`, `sems`) with about 390 students
each, live at https://erp.169-58-40-189.sslip.io.

**Priorities, in order** (from CLAUDE.md):
1. Tenant isolation is never broken.
2. The demo flows work smoothly and look professional.
3. The code stays clean enough to grow into the full product.

---

## 2. How we worked

The same loop ran for every phase:

1. **Read the rules and the plan.** `CLAUDE.md` holds the rules that never change
   (tenant isolation, security, conventions). `PLAN.md` holds one checklist per
   phase with an acceptance check at the end.
2. **One phase at a time.** The next phase did not start until the current one's
   acceptance check passed.
3. **Pre-phase fixes.** Feedback from trying the previous phase (bugs, missing
   rules, things a real school would expect) was fixed at the start of the next
   phase, before new work. Phases 4 and 7 open with such a list.
4. **Backend first, then frontend.** Entities, services, controllers and tests, then
   the typed API client and the pages.
5. **Tests for every rule,** always including a "School A tries to touch School B's
   records" test for every new endpoint.
6. **Docs every phase.** `ARCHITECTURE.md` (modules, API, data model, mechanisms,
   decisions, limitations) and the README Status table were updated before a phase
   counted as done. This rule was added to CLAUDE.md in Phase 4.
7. **Browser check at the end of a phase** of its main flows at desktop width and
   at about 360 px (phone). Added to CLAUDE.md in Phase 7.
8. **Commit** with a message like `phase-3: students & enrollment`, listing what was
   built, the tests, and the reason for any new library.

Working rules agreed along the way:
- The product owner makes the commits; each piece of work ends with a ready
  commit message, a list of what was built, and how to test it.
- While working, only the tests touching the changed code run; the whole suite
  runs once at the end of a phase.
- Browser checks stay short (main flows only), because details are covered by tests.
- The owner's own dev servers (8080 and 5173) are left alone; checks run on
  separate ports (8081 and 5174).
- Only fake data, anywhere, ever.

---

## 3. The shape of the system

```
Browser (React SPA, Ant Design)
   │  HTTPS, Authorization: Bearer <JWT>
   ▼
Caddy (prod: HTTPS, static files, /api proxy)
   ▼
Spring Boot API  /api/v1/**   one deployment, many schools
   │  JDBC — every school-owned row carries school_id
   ▼
PostgreSQL 16 (schema owned by Flyway)   +   FileStorage (local disk; R2 planned)
```

- **Modular monolith:** one Spring Boot app split into feature modules (`auth`,
  `school`, `academic`, `student`, `attendance`, `fee`, `exam`, `certificate`,
  `dashboard`, plus `common`, `tenant`, `audit`, `files`). Each module has
  `controller/ service/ repository/ entity/ dto/`. Modules call each other's
  services, never each other's repositories.
- **Shared schema multi-tenancy:** all schools in one database. Isolation is enforced
  in the application in several layers (see Phase 1).
- **Stateless API:** a JWT identifies the user and school; nothing is kept in
  server sessions.
- **Frontend:** React + Vite + TypeScript, Ant Design for UI, TanStack Query for all
  server state, one axios instance, React Router.

| | Backend | Frontend |
|---|---|---|
| Language | Java 21 | TypeScript |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, Actuator), Hibernate 7 | React 19, Vite 8 |
| Data | PostgreSQL 16, Flyway | TanStack Query 5, axios |
| UI / output | openhtmltopdf (PDFs) | Ant Design 6, Recharts, dayjs |
| Tests | JUnit 5, Testcontainers, MockMvc, AssertJ | `tsc` + `oxlint` clean |

---

## 4. Timeline

| # | Commit | What | Files | Lines added | Backend tests after |
|---|---|---|---|---|---|
| 0 | `e6cef96` | Repository and local environment | 51 | 4,770 | 3 |
| 1 | `fd37c61` | Tenancy, auth, errors, audit | 74 | 2,432 | 27 |
| 2 | `f448010` | School settings and academic setup | 98 | 5,753 | 57 |
| 3 | `388acc6` | Students and enrollment | 47 | 4,298 | 76 |
| – | `cdab29c` | Fix: admission with blank contacts | 2 | 57 | 76 |
| – | `b6f7689` | Docs: README and ARCHITECTURE.md | 2 | 506 | 76 |
| 4 | `89ebe71` | Attendance (+ pre-phase fixes) | 46 | 2,236 | 90 |
| 5 | `d95ec49` | Fees and receipts | 49 | 3,857 | 100 |
| 6 | `4dc28c4` | Exams, marks and report cards | 42 | 3,859 | 110 |
| 7 | `e518123` | Certificates, editable templates, report card remarks | 65 | 3,671 | 117 |
| 8 | `3f59299` | School dashboard | 15 | 1,655 | 121 |
| 9 | `23dc2d0` | Demo data | 19 | 1,415 | 122 |
| – | `f6105d1` | Hosted demo deployment | 11 | 339 | 122 |
| – | `b976383` | Dashboard: donut, monthly and comparison charts | 10 | 756 | 123 |

Size today: about 200 Java source files (~14,800 lines), 35 test files (~3,850
lines), and 78 frontend TypeScript files (~9,400 lines).

---

## Phase 0: repository and local environment

**Goal:** an empty but runnable project: database up, schema applied, backend
healthy, frontend showing a page.

**Built:**
- **Backend:** Spring Boot 4.1.1 on Java 21 with Maven wrapper; Web, Data JPA,
  Security, Validation, PostgreSQL, Flyway, Lombok, Actuator, Testcontainers.
- **Schema:** the agreed `V1__core_schema.sql` (28 tables) placed in
  `db/migration/`. Flyway owns the schema; Hibernate runs with
  `ddl-auto=validate`, so it only checks that entities match and never changes a table.
- **Config:** `application.yml` with a `dev` profile (reads `../.env`, safe local
  defaults) and a `prod` profile (every value from environment variables, no defaults).
  Time zone `Asia/Kolkata`.
- **Security placeholder:** stateless; only `/actuator/health` is public, everything
  else locked.
- **Infra:** `docker-compose.yml` with Postgres 16 configured from `.env`;
  `.env.example` lists every variable; `.env` is git-ignored.
- **Frontend:** React + Vite + TS with antd, TanStack Query, React Router, axios and
  dayjs; Vite proxies `/api` to `localhost:8080`; a placeholder page.
- **README:** run locally in three commands.

**Problem found:** on Windows, Git converted the migration to CRLF line endings,
which changes Flyway's checksum of a migration that has already run.
**Fix:** `.gitattributes` forces LF, so the checksum is the same on every machine.

**Tests:** Flyway creates all 28 tables, health is UP, other routes are locked.

---

## Phase 1: foundations (tenancy, auth, errors, audit)

**Goal:** the safety net everything else stands on. Log in to a school, and from
then on every query sees only that school.

### Tenant isolation, in layers

The design principle: **one forgotten line must not leak data**, so isolation is
enforced in several independent layers.

1. **The school comes only from the JWT.** `JwtAuthFilter` verifies the token, puts
   its `schoolId` into `TenantContext` (a ThreadLocal), and clears it in `finally`.
   No endpoint accepts a school id from the body, path or query.
2. **Automatic Hibernate filter.** Every school-owned entity extends `TenantEntity`,
   which declares a filter `school_id = :schoolId`. It is switched on for *every*
   session (`autoEnabled`) and also applies to load-by-id (`applyToLoadByKey`), which
   plain Hibernate filters do not. If no school is in context, the query **throws**
   instead of returning every school's rows: it fails closed.
3. **Explicit scoping anyway.** Repositories take `schoolId` explicitly
   (`findByIdAndSchoolId`). Plain `findById` is never used for tenant data.
   `TenantGuard.require(entity)` returns 404 when an entity belongs to another school.
4. **Inserts are stamped.** `@PrePersist` sets `school_id` from the context, and
   throws if there is none.
5. **404, not 403.** Another school's record is "not found", so its existence never
   leaks.
6. **Proven by tests.** `TenantFixtures.twoSchools()` creates School A and School B
   with tokens for both. Every later phase reuses it to prove A cannot read, list,
   update, delete or reference B's records.

### Authentication

- `POST /api/v1/auth/login {schoolCode, username, password}` returns a 12-hour HS256
  JWT with claims `sub` (user id), `schoolId`, `roles`, `name`.
- Passwords are BCrypt. Usernames are unique per school, so every school can have
  a `principal`.
- Wrong school, unknown user and wrong password all return the same 401. When the
  user does not exist, a dummy BCrypt hash is still compared, so response time does
  not reveal which usernames exist.
- An inactive user or school gets 403, but only after a correct password.
- **Rate limit:** 10 failed attempts in 15 minutes per school code + username, then
  429. In memory, which is fine for a single server.
- `GET /auth/me` returns the user, roles and school summary.
  `GET /public/schools/{code}/branding` gives the login page the school's name, logo
  and colour.

### Errors, audit, seed

- **Errors:** one `@RestControllerAdvice` turns everything into Spring `ProblemDetail`
  JSON, including 401/403 from the security layer, and field errors from Bean
  Validation (`{"student.firstName": "First name is required"}`).
- **Audit:** `AuditService.log(action, entity, id, details)` writes `audit_log`;
  `LOGIN` and `LOGIN_FAILED` from day one.
- **Dev seed:** a `dev`-profile runner creates two fictional schools (`ssv`, `gps`)
  with a principal each.

### Frontend

- Login page that shows the school's logo, name and colour as soon as a valid code
  is typed.
- Auth context: the token lives in memory and `sessionStorage` (per tab, so two tabs
  can be two different schools).
- One axios instance adds the JWT; a 401 sends the user to login.
- App shell: sidebar filtered by role, header with school logo and name, sign out,
  drawer on phones.
- The whole app is themed from the school's primary colour via Ant Design
  `ConfigProvider`.

**New library:** `spring-security-oauth2-jose` (JWT signing and verification, part of
Spring Security), instead of a third-party JWT library.

**Tests (27):** login success and failure, wrong school, inactive user and school,
same username in two schools, rate limit, validation, tenant filter and guard,
branding.

**Acceptance:** principal of School A and School B logged in in two browsers; each
saw only its own name, logo and colour.

---

## Phase 2: school settings and academic setup

**Goal:** a principal can set up a new school entirely from the UI.

**Built:**
- **School profile and settings** (principal only). Settings are a validated Java
  record stored as JSONB: receipt and certificate prefixes, weekly off days, module
  switches (attendance, fees, exams, certificates), report card options.
- **Logo upload** through a `FileStorage` interface (local disk in dev, keys like
  `schools/{id}/…`). The file type is detected from the **content**, not the file
  name (PNG, JPEG, WebP; SVG refused), 1 MB max. Logos are public because the login
  page shows them; the URL carries a timestamp so it can be cached for a year.
- **Module switches:** `@RequiresModule(FEES)` on a controller plus a `ModuleGuard`
  interceptor returns 404 when the school has that module off; the menu hides the
  matching entries.
- **Academic setup:** academic years (the first is current; exactly one current,
  enforced by a partial unique index; safe delete), classes (ordered), sections (per
  year, class teacher must be an active teacher), subjects, class–subject mapping per
  year, teacher assignments (section + subject → teacher), holidays.
- **User management** (principal): create staff, edit roles, reset password,
  deactivate and activate. You cannot deactivate yourself or remove the last
  principal. Every change is audited. A `/staff` picker serves principal and admin.
- Duplicates and "still in use" return 409; list sorting is restricted to an
  allow-list of columns and page size is capped at 100.

**Key decision: re-check the session on every request.** A JWT lives for 12 hours.
To make deactivation and role changes take effect immediately, `SessionService`
runs one indexed SQL query per request to confirm the user and school are still
active and to load the *current* roles. Cost: one small query per request.

**Frontend:** a Setup section with a page for each of the above; sidebar groups
filtered by role and modules; route guards that match the API's roles; a setup
checklist on the dashboard; shared helpers (`PageHeader`, year picker, mapping
backend field errors onto forms, Indian date and ₹ formats).

**Tests (57):** isolation for every new endpoint, the one-current-year rule, admin
blocked from settings and users, immediate revocation, module guard, logo
validation.

---

## Phase 3: students and enrollment

**Goal:** admit a student, find them by a guardian's phone, change their section,
promote them to the next year.

**Built:**
- **Students and guardians:** 1–4 guardians, one primary, at least one mobile
  number. Admission numbers are unique per school, and the next one is suggested,
  keeping any prefix and zero padding (`SSV/2026/009` → `SSV/2026/010`).
- **Aadhaar:** encrypted with AES-256-GCM through a JPA `AttributeConverter` (random
  IV per value, key from `APP_ENCRYPTION_KEY`), validated with the Verhoeff check
  digit, last 4 digits stored separately. The API **never** returns the full number,
  only `XXXX XXXX 1234`, and only to principal and admin.
- **Sensitive fields** (caste, religion, social category, Aadhaar) are filled in
  only for principal and admin.
- **Photos** are private: `GET /students/{id}/photo` applies the same access rules as
  the student record; the frontend fetches it with the token and shows an object URL.
- **Enrollment:** placed in a section on admission with the next free roll number;
  section change, enrol later, mark LEFT (closes the enrollment), alphabetical
  renumbering.
- **Promotion** per section in one transaction: selected students become `PROMOTED`
  with a new enrollment in a later year; the rest become `DETAINED` and can be placed
  in the same class next year. Refused if anyone already has a place in the target
  year.
- **Search:** name, admission number, guardian phone, class, section, status;
  paginated, native SQL with `school_id` on every joined table. Teachers see only
  students in sections they teach or class-teach.
- **CSV import:** template download, then a dry run with row-by-row errors, then the
  real import through the same admission service in **one transaction: all rows or
  none.** The CSV reader is ~60 lines in-house rather than a new library.
- **`StudentEnrolledEvent`** is published on every new enrollment, a hook for fees in
  Phase 5.

**Frontend:** student list with search and filters; admission/edit form; profile with
tabs (Details, Class history, and tabs filled in by later phases); change section,
mark left and enrol dialogs; promotion wizard; CSV import screen.

**Follow-up fix (`cdab29c`):** the admission form shows Father and Mother rows by
default, and an empty Mother row blocked saving. Empty contact rows are now skipped,
a failed validation shows a message, and the profile shows "not found" only for a
real 404.

**Docs (`b6f7689`):** the first full `README.md` (running, dev logins, roles,
walkthroughs, testing, troubleshooting) and `ARCHITECTURE.md`.

**Tests (76):** isolation for every endpoint, masking per role, teacher scope,
promotion and detention rules, Aadhaar encrypted at rest, CSV errors and rollback.

---

## Phase 4: attendance

### Pre-phase fixes

- **Production safety:** `DevSeeder` runs only under `dev & !prod`, and
  `ProductionSafetyCheck` stops a prod start if `DEV_SEED_PASSWORD` is set or the
  `dev` profile is active. `dev` is the default profile, so a misconfigured server
  must fail loudly rather than seed known logins.
- **Telangana categories:** BC-A to BC-E added next to BC; the CSV accepts "BC-A",
  "bc a" and "OC".
- **Section delete explains why it can't:** "N students are enrolled in Class 5 – A".
  The academic module asks other modules through a `SectionUsageCheck` interface, so
  it doesn't depend on the student module.
- **Known limitations written down:** login lockout can be abused, and the encryption
  key must be backed up separately from the database.

### Attendance

**Goal:** on a phone, a teacher marks a 40-student section in under a minute.

- **Saving** is one section for one day, as a bulk upsert. Everyone defaults to
  Present. The save must list **every** student on that day's roster, otherwise 400
  "class list has changed", so nobody is silently skipped. Unchanged marks are not
  rewritten, which keeps `marked_by` honest.
- **The roster for a date** is the section's enrollments with
  `joined_on ≤ date ≤ left_on`, so students who joined later or left earlier drop out.
- **Blocked days:** a future date or one outside the year (400); a holiday or weekly
  off day (409); for teachers, any day except today (403). Principal and admin can
  correct past school days.
- **Who:** principal and admin see every section; teachers see their own sections
  (class teacher or assigned) and get 404 for others; accountants are refused.
- **Percentage** = (Present + ½ Half day) ÷ days marked, one decimal.
- **Reports:** section sheet, monthly register (students × days, with CSV), a
  student's month, and the school-wide day summary by class with the sections not
  yet marked (one SQL query per day).

**Frontend:** a phone-first marking screen with big P/A/L/H toggles, the register
grid, the day summary, and a calendar on the student profile.

**Problem fixed:** inside a collapsed sidebar, Ant Design turned the inline menu into
popups that opened over the page on phones. The menu is now not mounted while the
mobile drawer is closed.

**Tests (90):** attendance rules, teacher scope and today-only, holiday and off-day
blocks, reports, module off, production safety check, School A vs B.

---

## Phase 5: fees and receipts

**Goal:** collect a partial payment, print the receipt, cancel it, and see the dues
report update correctly.

**Built:**
- **Setup:** fee heads (Tuition, Books, Transport…); per-class installment plans per
  year (head, name, amount, due date); copy a plan from another year.
- **Dues:** created automatically when a student is enrolled (the fee module listens
  to `StudentEnrolledEvent` in the same transaction), plus an idempotent "generate
  dues" for students already enrolled. Changing an installment amount updates its
  dues, but is refused if payments or concessions exceed the new amount.
- **Concessions** per due, with a reason, never above the unpaid amount.
- **Collecting:** one receipt for one student and one academic year; partial payments
  allowed; a line above the due's balance is refused (409); cheques need a number.
- **Cancelling** (principal or accountant, reason required) subtracts each line from
  its due. The receipt is kept and shown as cancelled.
- **Receipt PDF:** school header and logo, the amount in Indian words (lakh/crore),
  ₹1,23,456.00 formatting, a cancelled watermark.
- **Reports:** collection by day and payment mode, dues (with "overdue only"), and
  expected vs. collected per class; CSV export built in the browser from the data on
  screen.

### Gap-free receipt numbers (the hard part)

Receipt numbers look like `PREFIX/YY-YY/000123` and must be unique and without gaps,
even with several accountants collecting at once.
- Inside one transaction, the dues being paid are locked `FOR UPDATE` (in id order,
  so two payments can't deadlock), then the school's `receipt_counter` row is locked
  `FOR UPDATE` and incremented.
- If anything fails, the whole transaction, including the number, rolls back.
  Cancelled receipts keep their number. So numbers never have holes.
- **Bug found while writing the concurrency test:** two payments on the same due could
  overwrite each other's `paid_amount`. Locking the dues (not only the counter) fixed
  it. The test runs 8 concurrent payments and checks the numbers and totals.

### PDFs and the rupee sign

- **New library:** `io.github.openhtmltopdf:openhtmltopdf-pdfbox`, the PDF engine named
  in CLAUDE.md. The maintained fork was chosen, because the original has had no
  release since 2021.
- **Problem:** the built-in PDF fonts have no ₹ glyph, and servers have no system
  fonts. **Fix:** embed DejaVu Sans, taken from `jasperreports-fonts` (font files
  only, no dependencies). A test checks the glyph is in the bundled font.
- The shared `HtmlPdf` helper (escaping, ₹ formatting, embedded images) was later
  reused by report cards and certificates.

**Frontend:** Collect fee (search student → outstanding dues → pay → print), receipts
list (reprint, cancel), reports, fee setup, and a Fees tab on the student profile.

**Tests (100):** 8 concurrent payments, partial pay, overpay 409, cancel restores
dues, receipt numbering, concessions, plan edits blocked by payments, roles (teacher
refused, admin can't cancel), module off, School A vs B on every fee endpoint, amount
in words, ₹ glyph.

---

## Phase 6: exams, marks and report cards

**Goal:** set up FA1 for Class 5, enter marks for a section, publish, download every
report card as one PDF.

**Built:**
- **Grading scales** with bands (min %, grade, grade point, remark), and one-click
  presets for **CBSE 9-point** and **TS state CCE** instead of seeding both into every
  school.
- **Exams** per year with order, optional weightage and grading scale.
- **Papers** per exam + class + subject with max and pass marks and a date; "apply to
  all class subjects" creates them in one go.
- **Marks entry** per paper + section: only the rows that changed are sent, so two
  teachers on different rows don't overwrite each other; absent flag; blank clears
  the mark back to "not entered". Teachers can enter only for subject + section pairs
  they are assigned (`ExamAccess`; anything else is 404). Marks above max are refused.
- **Publish / unpublish** (audited): once published, marks and papers are locked
  (409).
- **`ResultCalculator`:** pure functions, unit-tested:
  - subject % = marks ÷ max (2 decimals, half-up); grade = the band with the highest
    min % reached
  - absent counts as 0 and fails the subject; below pass marks fails it
  - **any subject not entered makes the result "incomplete"**, not a fail, so a
    half-entered sheet doesn't show students as failing
  - final % = Σ(weight × %) ÷ Σ weight over published weighted exams
  - rank is competition style (1, 2, 2, 4)
- **Results:** section result sheet (students × subjects, CSV export), a student's
  year results.
- **Report cards:** one PDF document with one page per student, for one exam or the
  annual card (every published exam plus the final); for a section or one student.
  Rank and attendance follow the school's settings. Printing is audited.

**Frontend:** exam setup, a keyboard-friendly marks grid that works on a phone,
results with CSV export, report card download, and a Marks tab on the profile.

**Tests (110):** grade boundaries (90.99 → A2, 91 → A1), absent, incomplete,
weighting, ranking; teacher limits, publish lock, PDFs, roles, module off, School A
vs B. No new libraries.

---

## Phase 7: certificates and editable templates

### Pre-phase fixes

- **Adding a holiday on a day that already has attendance** used to be silent. Now the
  API answers 409 with how many students and sections are marked, and the screen asks:
  clear those marks (audited) or keep them. The attendance module implements a
  `HolidayAttendance` interface, so the academic module doesn't depend on it.
- **CLAUDE.md:** every phase now ends with a browser check at desktop and phone width.
- **PLAN:** demo logos must be PNG, because SVG doesn't print in the PDFs.

### Certificates

**Goal:** issue a bonafide and a TC; both PDFs carry the school's own logo and
numbering.

- **Bonafide** only for active students. A **TC** needs a leaving date and reason;
  issuing it closes the enrollment and sets the student to `TC_ISSUED`. A second TC is
  refused, naming the first one's number.
- **Serials** `PREFIX/BON/0001`, `PREFIX/TC/0001`: gap-free per school and type, with
  the same lock-the-counter-row approach as receipts (`certificate_counter`, new in V2).
- **Snapshot:** every printed value is stored in `issued_certificate.data`, and the PDF
  file itself is stored. Re-downloads are identical even after the template or the
  student changes.
- **Preview** renders without taking a number. Issuing is audited.
- **Register** with search, type and date filters.

### One template mechanism for certificates and report cards

- The built-in templates ship with the code (`pdf-templates/*.html`). A school sees
  the built-in one until it saves its own; "reset" deletes the school's row. Nothing
  needs seeding for new schools.
- **Placeholders:** `{{student.fullName}}` is HTML-escaped; `{{{marksTable}}}` is HTML
  built by the app (logo, marks table, co-scholastic table). No loops or conditions, so
  a principal can edit it and nothing in a template can run code.
- **Checked before saving:** every placeholder must be allowed for that template type
  (with a hint when the wrong number of braces is used); no `<script>`, `<iframe>`,
  `<link>`, DOCTYPE or entities; at most 50,000 characters; it must render with sample
  data.
- **Security fix:** an edited template could have pointed an `<img>` at a URL (for
  example cloud metadata) or a local file. `HtmlPdf` now loads **only `data:` URIs**,
  for every PDF.
- Principal-only editor: a textarea with click-to-insert placeholders and a live PDF
  preview.

### Report card remarks

- New in V2: co-scholastic areas (set up per school) and, per exam and student, a
  class teacher remark and A/B/C grades, entered on a new Remarks & grades page by the
  section's class teacher. Locked when the exam is published; the annual card uses
  the last published exam's notes.

**Migration V2** (V1 was never edited): `certificate_counter`, `co_scholastic_area`,
`report_card_note`.

**Tests (117):** serials, stored PDF text, roles, TC rules, template validation,
external image never fetched, issued PDF unchanged after reset, holiday prompt, School
A vs B. The browser check was done by hand by the product owner.

---

## Phase 8: school dashboard

**Goal:** the dashboard is the first screen after login and looks good on a laptop
and a phone; it loads in under 300 ms with demo data.

**Built:**
- **One endpoint, `GET /dashboard`, shaped by role.** Widgets the role may not see, or
  of a switched-off module, are null.
  - Principal/admin: the whole school.
  - Accountant: students and fees.
  - Teacher: their own day only (their sections, whether today is marked, marks still
    to enter).
- **`DashboardRepository`:** one read-only repository with one native SQL query per
  widget, every table filtered by `school_id`, in one place to review for scoping and
  speed: students by class, attendance per day, receipts per day and month to date,
  dues outstanding and overdue, top 5 overdue classes, upcoming exams, recent
  admissions. Today's attendance reuses the attendance day summary so holidays are
  handled the same way.
- About 90 ms warm on dev data.
- **Frontend:** stat tiles, three charts (attendance % and fees over 14 days, students
  by class), lists (sections not marked, overdue classes, upcoming exams, recent
  admissions), a teacher view with "Take attendance" and marks-to-enter links, and the
  setup checklist only while setup is incomplete.

**New library:** `recharts`. PLAN names it; Ant Design has no charts; SVG based,
supports React 19.

**Tests (121):** every number for the principal, accountant and teacher shapes,
modules off, School A counts only its own data, no academic year. The browser check
was automated in Chrome (desktop, and the app inside a 360 px frame).

---

## Phase 9: demo data

**Goal:** a fresh database plus the seeder, and every screen in every school looks
populated and realistic.

### Three fictional schools

| Code | Board / medium | Grading and exams | Fees | Other |
|---|---|---|---|---|
| `vjhs` | State, Telugu medium | TS CCE, FA1–4 / SA1–2; Telugu, Hindi, English | Tuition terms + books | |
| `lvps` | CBSE, English medium | 9-point, Term 1 / Term 2 | Quarterly | 5-day week |
| `sems` | State, English medium | TS CCE | Tuition + transport (opt-outs as concessions) | |

Each: Nursery to Class 10 × 2 sections, about 390 students with guardians, about 60
days of attendance, on-time / partial / unpaid fees with sibling concessions, the
first past exam published with marks, remarks and co-scholastic grades, the next exam
partly entered, 5 bonafides and 2 TCs, holidays, and a PNG logo.

Logins per school: `principal`, `admin`, `accountant`, `teacher1`, `teacher2`
(`teacher3`…`teacher26` exist so every section and subject has a teacher). The
password comes from `DEMO_SEED_PASSWORD`.

### How it works

- **Through the app's own services, not SQL inserts.** `DemoDataService` signs in as
  each school's principal (security context and `TenantContext` set for the run,
  cleared in `finally`) and calls the same services the UI calls. So every rule holds:
  dues on admission, gap-free receipt and certificate numbers, attendance only on
  school days, marks only on unpublished exams, audit entries. Cost: a few minutes per
  run.
- **Order:** profile and logo → staff → year, classes, sections, subjects,
  assignments → holidays and fee plan (before admissions, so dues follow) → admissions
  and roll numbers → concessions and backdated payments → attendance → exams → certificates.
- **Repeatable:** a fixed random seed per school; dates are placed relative to today,
  so the data always looks current.
- **Safe:** runs only with the `demo` profile **and** `--seed-demo`, needs
  `DEMO_SEED_PASSWORD` (no default), skips school codes that exist, then exits.
- **Only fake data:** random name combinations, `9xxxxxxxxx` phone numbers, no Aadhaar
  or caste.

### Problems realistic data exposed

- **Teacher dashboard took 1–1.8 s.** Marks entry options loaded a class list per
  paper. Replaced with two grouped counts per exam: about 250 ms.
- Teachers saw "marks to enter" for exams that hadn't started; now only started exams.
- Results needed an empty state for classes with no papers.
- Dashboard money tiles were cramped on phones; now full width.
- Payments dated after "today" were collected on the seeding day, making a spike in
  "collected today"; such payments are now skipped.

**Tests (122):** `DemoDataTest` seeds a small version (2 students per section, 6 days)
and checks every school is complete, every documented login works, dashboards are
populated, and a second run skips all three. No new libraries.

---

## Hosted demo deployment

Added before Phase 10, so the demo could be shown from a real URL without buying a
domain yet.

- **`backend/Dockerfile`:** multi-stage (JDK 21 builds, JRE 21 runs), non-root user,
  `-XX:MaxRAMPercentage=60`, uploaded files on a volume.
- **`deploy/demo/web.Dockerfile` + `Caddyfile`:** the frontend is built and served by
  Caddy; `/api` and `/actuator/health` are proxied to the backend; zstd/gzip; a
  year-long cache for hashed assets; basic security headers.
- **`deploy/demo/docker-compose.yml`:** app + Postgres 16 + Caddy as its own project;
  the database port is not published; memory limits; named volumes.
- **HTTPS without a domain:** an `sslip.io` hostname (`erp.169-58-40-189.sslip.io`)
  resolves to the server's IP, and Caddy gets a certificate for it automatically.
- `.env.example` and a README for the server: prod profile, generated secrets, the
  demo password, seeding, everyday commands. `.dockerignore` keeps `.env` and build
  output out of images.

**Updating the live demo:** on the server,
`cd /opt/school-erp/deploy/demo && git pull && docker compose up -d --build`.

---

## Dashboard charts (after the demo went live)

The first feature change after the demo: a richer dashboard with pie charts and more
kinds of charts.

**Layout,** top to bottom:
1. **Stat tiles** (Students now shows "183 boys · 207 girls").
2. **Donuts:** today's attendance; the year's fees (collected / not yet due /
   overdue, % collected in the centre); payment modes; collected by fee head (only
   when there is more than one head).
3. **Trends:** attendance over 14 days; fees per month for the academic year, with a
   Monthly / Daily switch.
4. **Comparisons:** students by class split into boys and girls; attendance by class
   this month; the latest published exam's subject averages (hover for the pass rate);
   most overdue classes.
5. **To-do lists:** sections not marked, upcoming exams, recent admissions.

**Backend:** new tenant-scoped queries in `DashboardRepository`: boys/girls per class,
attendance per class, receipts per month, per payment mode and per fee head (fee
receipt lines have no `school_id`, so they're scoped through the receipt, due,
installment and head joins), and the latest published exam's subject averages and
pass rate, using the same pass rule as the results.

**Colours:** a fixed categorical palette in `palette.ts`, checked with a colour-blind
validator. Red/green status colours were tried for slices and failed that check (they
blur together for the most common colour blindness), so they aren't used. A donut has
at most 6 slices (more fold into "Other") and a legend with values and shares, so
colour never carries meaning alone.

**Problems fixed:**
- Postgres `numeric` couldn't be read as a Java `Double`; the averages are cast to
  `float8` in SQL.
- The fee-head donut ended up alone on a row; donut widths now adapt to how many are
  shown.
- The class and subject bars looked flat because the demo data is evenly random; a %
  label at the end of each bar keeps them readable.

**Tests:** `DashboardTest` checks the new figures and that another school sees none of
them (5 tests). Checked in the browser at desktop and 360 px with no sideways scroll.

---

## Parent messages, online payment and demo wins (Phase 9b)

Built for sales demos, after the user's market questions (what schools ask for) and a
cost check (WhatsApp utility messages cost a fraction of a rupee; about ₹1,100 a month
for a 1,000-student school without announcements).

**Decisions taken with the owner:** messages go to real guardian numbers (test mode:
up to 5 registered phones); principal, admin and teachers send absence alerts;
online payment is Razorpay test mode at the counter; one platform account each from
`.env`; absent only, primary contact only, English, re-sending allowed with a warning,
delivery ticks later, payment confirmations for cash too.

**Built:** WhatsApp consent, the `notification` module and log, absence alerts, fee
reminders, payment confirmations, results messages, per-school message switches,
Razorpay at the counter, a TC dues warning, `DEMO.md`, and a dashboard redesign
(greeting band with quick actions, icon tiles, grouped sections). Details in
ARCHITECTURE.md §8.

**Problems found:**
- **Java groups en-IN amounts in threes** (₹123,456.00). Receipts and report cards
  were wrong for amounts of a lakh and more; demo amounts were smaller, so nobody saw
  it. Fixed with a small `Rupees` formatter, unit-tested.
- **Sending from a transaction listener would hold two connections** per payment
  (the committed one is still held during after-commit callbacks), which can stall
  the pool under load. Confirmations are sent by the controller after the
  transaction instead, which also keeps the demo seeder from sending anything.
- **PLAN.md had Phase 10 and 11 ticked from the start.** Unticked, except what the
  demo deployment really did.
- **No Docker on the development laptop.** The suite was run against a portable
  PostgreSQL 16 from a scratch copy (Testcontainers swapped for a local URL, outside
  the repository), and the browser check used a production build served locally
  with headless Edge screenshots. The project's own test setup is unchanged.
- A `postcss.config.mjs` in a folder above the repository (from another project)
  broke `vite build`; noted in README troubleshooting.

**Tests (140):** absence alerts (consent, failures, re-send, teacher scope, switches,
log, School A vs B, module off), fee reminders, payment confirmations, online
payment (forged signature, double completion, refused payment, dues changed, School
A vs B), results messages, CSV consent column, `Rupees`. WhatsApp and Razorpay are
replaced by stand-ins in tests (`FakeIntegrations`).

**Browser check:** desktop and 360 px for the dashboard, attendance alerts (sent and
logged), dues reminders, results dialog, TC warning, preferences, message log,
admission form; no sideways scroll.

**Real services (31-10-2026):**
- **Razorpay test keys:** orders created, the payment window opened, a netbanking
  test payment became an Online receipt. UPI did not show: Razorpay's `/methods`
  reported it off for the account (an account setting, not the app).
- **WhatsApp:** Meta business portfolio, developer app with the test number, and a
  permanent system-user token (the API Setup token expired after about an hour, not
  24 hours). The four templates were created through the Graph API because the
  template page in WhatsApp Manager kept failing. Meta refused `payment_received` at
  first ("variables can't be at the start or end"), so its text now ends with
  "Thank you." All four were approved about a day later. An absence alert from the
  attendance screen then arrived on a registered phone.

---

## 17. Cross-cutting threads

### Security, as built

| Rule | How |
|---|---|
| Tenant isolation | JWT-only school id, auto-enabled Hibernate filter that fails closed, explicit `schoolId` in repositories, `school_id` on every table in native SQL, stamped inserts, 404 for other schools, an A-vs-B test per module |
| Passwords | BCrypt; constant-time-ish failure path; rate limit |
| Immediate revocation | Per-request session check of user, school and roles |
| Aadhaar | AES-256-GCM, last 4 stored apart, full number never returned |
| Sensitive fields | Caste, religion, category and Aadhaar only for principal and admin |
| Files | Type detected from content; SVG refused; keys under `schools/{id}/`; paths outside the storage root refused; photos private |
| PDFs | Only `data:` URIs load; template markup restricted and validated |
| Secrets | Environment variables only; prod has no defaults; prod refuses to start with the dev seed |
| CORS | Only the configured frontend origin |
| Audit | Login, failed login, settings, users, students, sections, promotion, receipts, exams publish, report card print, certificate issue, holiday attendance clear |

### Number sequences

Receipts and certificates both use a counter row per school (and year or type)
locked `FOR UPDATE` in the same transaction as the business write, so numbers are
unique and gap-free under concurrency.

### Modules without tangles

When one module needs another's knowledge, it defines a small interface and the
other implements it (`SectionUsageCheck`, `HolidayAttendance`), or it publishes an
event (`StudentEnrolledEvent`). The academic module never depends on students,
attendance or fees.

### Libraries added beyond the stack, and why

| Library | Why |
|---|---|
| `spring-security-oauth2-jose` | JWT signing and verification, part of Spring Security |
| `@ant-design/icons` | Already pulled in by antd; declared because it's imported directly |
| `io.github.openhtmltopdf:openhtmltopdf-pdfbox` | The PDF engine CLAUDE.md names (maintained fork) |
| `net.sf.jasperreports:jasperreports-fonts` | DejaVu Sans TTF for the ₹ glyph; font files only |
| `recharts` | Dashboard charts; PLAN names it; Ant Design has none |

Everything else (CSV reading, amount in words, Verhoeff check, template placeholders)
is small in-house code.

### Testing

- About 123 backend tests against a real Postgres 16 in Testcontainers, sharing one
  Spring context. `IntegrationTest` gives MockMvc helpers; `TenantFixtures` and
  `AcademicFixtures` build schools and setup in one call.
- Unit tests for the pure pieces: Verhoeff, AES-GCM, CSV, amount in words,
  `ResultCalculator`.
- Frontend: `tsc` and `oxlint` must be clean.
- Phase-end browser checks at desktop and 360 px (from Phase 7).

---

## 18. Lessons learned

- **Fail closed beats remembering.** A tenant filter that throws with no school in
  context caught mistakes that "remember to add a where clause" would have let through.
- **Realistic data finds real bugs.** The 1.8-second teacher dashboard, the payments
  after "today" and the empty results page only showed up with ~390 students and two
  months of history.
- **Concurrency tests earn their keep.** The lost update on `paid_amount` was found
  only because the test fired 8 payments at once.
- **Build demo data through the services.** It took longer to run, but it can never
  hold data the app itself couldn't have created.
- **Windows needs care:** LF line endings for Flyway checksums; the IDE writing broken
  classes into `target/` (fixed with `./mvnw clean test`).
- **PDFs are their own world:** no system fonts on servers, no SVG, and templates must
  not be able to fetch URLs.
- **Accessible colour is computable.** The chart palette was checked by a script, not
  by eye, and the "obvious" red/green failed.

---

## 19. Where it stands and what's next

**Done:** Phases 0–9, the hosted demo, and the richer dashboard. The demo runs at
https://erp.169-58-40-189.sslip.io with three schools.

**Known limitations** (full list in ARCHITECTURE.md §14): in-memory login rate limit
(single server only, and the lockout can be abused); no refresh tokens; files on local
disk only; attendance changes and individual mark edits not audited; no fee refunds or
advance payments; no TC cancellation; report card printing is synchronous; the
dashboard has fixed windows and no dark mode; demo data ages and should be re-seeded
shortly before a demo.

**Next:**
- **Feature improvements for the demo** (current focus), one change at a time.
- **Phase 10, production deployment** (parked): a real domain with per-school
  subdomains (`ssv.schoolerp.com`), the Cloudflare R2 `FileStorage`, nightly `pg_dump`
  backups to R2 with a tested restore, health monitoring, `make deploy`.
- **Phase 11, demo polish:** loading skeletons and empty states everywhere, test on a
  budget Android phone over mobile data, fix anything slower than a second, and a
  `DEMO.md` click path.

**Out of scope for the MVP** (roadmap only): parent/student app, SMS/WhatsApp,
online payments, transport, library, hostel, payroll, timetable, homework, group
admin, self-service onboarding.
