# CLAUDE.md — School ERP (multi-tenant SaaS MVP)

Read this file and `PLAN.md` before doing anything. `PLAN.md` holds the phased
build plan; this file holds the rules that apply to every phase.

## What we are building

A hosted, multi-tenant school management system. Many schools share one
deployment, but **each school is completely isolated**: its own logins, its own
data, no user can ever see or touch another school's data. There is no
cross-school or group admin in the product. New schools are created by us via
a seed/onboarding script, not through the UI.

This is an MVP for a sales demo. Priorities, in order:
1. Tenant isolation is never broken.
2. The demo flows work smoothly and look professional.
3. Code stays clean enough to grow into the full product.

## Stack (do not swap without asking)

- **Backend:** Spring Boot (latest stable), Java 21, Maven, modular monolith
- **DB:** PostgreSQL 16, schema managed only by Flyway
- **Auth:** Spring Security, stateless JWT (access token only for MVP, 12h expiry)
- **PDF:** openhtmltopdf (HTML templates → PDF) for receipts, report cards, certificates
- **Frontend:** React + Vite + TypeScript, Ant Design, TanStack Query, React Router, axios
- **Tests:** JUnit 5, Testcontainers (Postgres), MockMvc
- **Infra:** Docker Compose; production = one VPS (Bangalore region) + Caddy + Cloudflare
- **File storage:** local volume in dev, S3-compatible (Cloudflare R2) in prod, behind one `FileStorage` interface

## Repository layout

```
school-erp/
├── CLAUDE.md
├── PLAN.md
├── docker-compose.yml          # local Postgres
├── docker-compose.prod.yml     # app + db + caddy
├── backend/
│   └── src/main/java/com/schoolerp/
│       ├── common/        # base entity, errors, pagination, utils
│       ├── tenant/        # TenantContext, Hibernate filter wiring, guards
│       ├── auth/          # login, JWT, users, roles
│       ├── school/        # school profile, settings, branding
│       ├── academic/      # years, classes, sections, subjects, assignments
│       ├── student/       # students, guardians, enrollment, promotion
│       ├── attendance/
│       ├── fee/
│       ├── exam/
│       ├── certificate/
│       ├── dashboard/
│       ├── audit/
│       └── files/         # FileStorage interface + impls
│   Each module: controller/, service/, repository/, entity/, dto/
└── frontend/
    └── src/
        ├── api/           # axios client, interceptors, typed endpoints
        ├── auth/          # auth context, protected routes, role guard
        ├── layout/        # shell, sidebar, header with school branding
        ├── features/<module>/   # pages + components + hooks per module
        └── components/    # shared UI
```

## Database rules

- The initial schema is `backend/src/main/resources/db/migration/V1__core_schema.sql`.
  **Never edit V1 once it has run.** Every change is a new migration (`V2__...`, `V3__...`).
- `spring.jpa.hibernate.ddl-auto=validate`. Hibernate never creates or alters tables.
- Money is `NUMERIC(10,2)` in SQL and `BigDecimal` in Java. Never `double`/`float`.
- Dates are `LocalDate`; timestamps are `OffsetDateTime` (stored as `TIMESTAMPTZ`), server zone `Asia/Kolkata` for display.

## Tenant isolation rules (critical — read twice)

1. Every tenant-owned entity extends `TenantEntity` (a `@MappedSuperclass` with
   `schoolId`), annotated with a Hibernate `@Filter` on `school_id`.
2. A `TenantContext` (ThreadLocal) holds the current `schoolId`. It is set **only**
   from the validated JWT in the auth filter and cleared in a `finally` block.
   Never accept `schoolId` from a request body, path, or query parameter.
3. The tenant filter is enabled on the Hibernate session for every request.
4. **Hibernate filters do not apply to `findById` / `EntityManager.find`.** Never use
   plain `findById` for tenant entities. Use `findByIdAndSchoolId(...)` or the
   shared `TenantGuard.require(entity)` helper that throws 404 on mismatch.
5. `schoolId` is set automatically on insert via `@PrePersist` from `TenantContext`.
   Inserting with no tenant in context must throw.
6. Native queries and JPQL with joins must include `school_id = :schoolId` explicitly.
7. Return **404, not 403**, when a record belongs to another school (don't leak existence).
8. Every module gets an integration test that logs in as School A and proves it
   cannot read, list, update, or delete School B's records.

## Security rules

- Passwords: BCrypt only.
- Aadhaar: AES-GCM encrypted via an `AttributeConverter`, key from env var
  `APP_ENCRYPTION_KEY`; store last 4 digits separately; never return full Aadhaar in list APIs.
- Caste, religion, social category, Aadhaar: only visible to PRINCIPAL and ADMIN roles.
- All secrets come from environment variables. Nothing secret in git.
- CORS restricted to the configured frontend origin.
- Log in-app audit entries (`audit_log`) for: login, student create/update/status change,
  receipt create/cancel, marks publish, certificate issue, user/role changes.

## Roles

| Role       | Can do |
|------------|--------|
| PRINCIPAL  | Everything in their school, including settings and users |
| ADMIN      | Everything except users/roles and school settings |
| ACCOUNTANT | Fees, receipts, fee reports; read-only students |
| TEACHER    | Attendance + marks for their assigned sections/subjects; read-only students of those sections |

Enforce with `@PreAuthorize` on controllers **and** ownership checks in services
(e.g. a teacher can only mark attendance for sections they are assigned to).

## API conventions

- Base path `/api/v1`. Public (no auth): `POST /auth/login`, `GET /public/schools/{code}/branding`.
- Request/response DTOs are Java `record`s with Bean Validation. Never expose entities.
- Errors use Spring `ProblemDetail` via one `@RestControllerAdvice`.
- Lists are paginated (`page`, `size`, `sort`) and support a `q` search where useful.
- Login request: `{ schoolCode, username, password }`. JWT claims: `sub` (userId),
  `schoolId`, `roles`, `name`.

## Frontend conventions

- One axios instance; interceptor adds the JWT and redirects to login on 401.
- All server state via TanStack Query; no ad-hoc fetching in components.
- Forms with Ant Design `Form` + validation matching backend rules.
- School branding (logo, name, primary color) loads after login and themes the app
  via Ant Design `ConfigProvider`.
- Fully usable on a phone (≥ 360px wide), especially attendance and marks entry.
- Indian formats: `₹1,23,456.00`, dates `DD-MM-YYYY`.

## How to work

- Work **one phase of PLAN.md at a time**. Don't start the next phase until the
  current phase's acceptance checks pass.
- After each phase: run all tests, fix failures, update the checklist in PLAN.md,
  and commit with a message like `phase-3: students & enrollment`.
- Every phase also updates the docs before it is considered done:
  `ARCHITECTURE.md` (modules, API catalogue, data model in use, mechanisms,
  decisions, known limitations, "what's next") and the **Status** table in
  `README.md` (plus any new run/test/troubleshooting steps).
- If a requirement is unclear or a rule here blocks you, stop and ask instead of guessing.
- Prefer boring, readable code over clever code. No new libraries without a reason
  stated in the commit message.
