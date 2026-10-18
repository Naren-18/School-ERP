# Architecture

How the School ERP is built, as of the end of **Phase 3** (students and enrollment).
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
| Tests | JUnit 5, Testcontainers (Postgres 16), MockMvc, AssertJ | managed by Boot |
| Frontend | React, Vite, TypeScript | React 19, Vite 8, TS 6 |
| UI / data | Ant Design 6 (+ icons), TanStack Query 5, React Router 7, axios, dayjs | |
| Local infra | Docker Compose (Postgres) | |

Libraries added beyond the stack in `CLAUDE.md`:
- **`spring-security-oauth2-jose`:** JWT signing and verification, part of Spring
  Security.
- **`@ant-design/icons`:** already pulled in by antd; declared because it's imported
  directly.

The CSV reader is ~60 lines in-house rather than another dependency.

## 3. Backend modules

Package root `com.schoolerp`. Each module has `controller/ service/ repository/ entity/ dto/`.

| Module | Responsibility (so far) |
|---|---|
| `common` | `BaseEntity`, `TenantEntity`; errors (`ApiException`, `NotFoundException` 404, `ConflictException` 409, `BadRequestException` 400, `GlobalExceptionHandler`); `PageResponse`; `crypto` (AES-GCM `FieldCipher`, `EncryptedStringConverter`, Aadhaar Verhoeff check); `csv` (RFC 4180 reader/writer) |
| `tenant` | `TenantContext` (ThreadLocal school id), `TenantIdResolver` (feeds the Hibernate filter), `TenantGuard` (404 unless the entity is this school's) |
| `auth` | Users and roles, login, JWT, per-request session check, rate limiting, user management, staff picker, `Access` role expressions, security config |
| `school` | School profile, typed settings (JSONB), logo upload, public branding, module toggles (`@RequiresModule` + `ModuleGuard`), dev seeder |
| `academic` | Academic years, classes, sections, subjects, class–subject mapping, teacher assignments, holidays, teacher scope |
| `student` | Students, guardians, enrollments, search, photos, leaving, promotion, roll numbers, CSV import, `StudentEnrolledEvent` |
| `audit` | `AuditService` writing `audit_log` |
| `files` | `FileStorage` interface, `LocalFileStorage`, `ImageTypes` (detects image type from content) |
| `attendance`, `fee`, `exam`, `certificate`, `dashboard` | Empty, filled in by Phases 4–8 |

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

The schema is `V1__core_schema.sql` (28 tables). It has never been edited after
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

Foreign keys are mapped as plain `Long` ids, not JPA associations. Services batch-load
related rows into maps. This keeps every query explicit and tenant-scoped, and avoids
lazy-loading surprises.

Money (`NUMERIC(10,2)` ↔ `BigDecimal`) starts in Phase 5. Dates are `LocalDate` and
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
- **Events:** `StudentEnrolledEvent` is published on every new enrollment (admission,
  promotion, enrolling later). Phase 5 will listen to it to create fee dues.
- **Audit:** `AuditService.log(action, entity, id, details)`. Logged so far:
  - login, failed login
  - school profile, settings and logo changes
  - user create, update and role changes, password reset, activation
  - student create, update, status change, photo
  - section change, renumbering, promotion

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
                auth.ts school.ts academic.ts users.ts students.ts (typed endpoints)
  auth/         tokenStore (memory + sessionStorage), AuthProvider/useAuth (me query),
                guards (RequireAuth, RequireRole)
  layout/       AppShell (sidebar + header, mobile drawer), menu.tsx (role/module filtered),
                BrandTheme (school colour → Ant Design ConfigProvider)
  features/     auth/ dashboard/ settings/ students/   (pages + small feature components)
  components/   PageHeader, SchoolLogo, useFormErrors (backend field errors → form)
  utils/        format (DD-MM-YYYY, ₹ en-IN), download (save Blob)
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

- **76 backend tests.** `./mvnw clean test` runs them against a real Postgres 16 in
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
  - unit tests for Verhoeff, AES-GCM and CSV
- **Frontend:** `tsc` and `oxlint` must be clean. Flows were checked by hand in the
  browser at the end of each phase.

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

## 14. Known limitations / technical debt

- Login rate limiting is in memory, so it would need a shared store to run more than
  one instance.
- No refresh tokens; users sign in again after 12 hours.
- `FileStorage` has only the local implementation. R2 arrives in Phase 10, along with
  backups and HTTPS.
- Student list avatars show initials only (photos load on the profile), to avoid one
  request per row.
- The admission form has a photo upload but no other documents (birth certificate,
  previous TC) yet.
- A section with enrolled students can't be deleted. The database refuses it, and the
  user sees a generic 409.
- The IDE's Java extension writes into `backend/target/classes` without Lombok
  support, which can break test runs. `./mvnw clean test` fixes it.

## 15. What's next

Phase 4 is **attendance**:
- mobile-first marking per section and date
- teachers limited to their sections and same-day edits
- marking blocked on holidays and weekly off days
- daily, monthly and school-wide reports

It will reuse `SectionStudent`, `TeacherScopeService`, the holidays and the weekly
off days already in place. Phases 5–11 follow the order in `PLAN.md`.
