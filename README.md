# School ERP

A hosted, multi-tenant school management system (MVP for a sales demo). Many schools
share one deployment; each school is fully isolated, with its own logins and data.

- **Rules for building it:** [`CLAUDE.md`](CLAUDE.md)
- **Phased build plan and progress:** [`PLAN.md`](PLAN.md)
- **How it is built (design, modules, API, decisions):** [`ARCHITECTURE.md`](ARCHITECTURE.md)

## Status

| Phase | What | State |
|---|---|---|
| 0 | Repository and local environment | Done |
| 1 | Tenancy, login, errors, audit | Done |
| 2 | School settings and academic setup | Done |
| 3 | Students, guardians, enrollment, promotion, CSV import | Done |
| 4 | Attendance: mobile marking, monthly register, school summary | Done |
| 5 | Fees: plans, dues, collection, receipts (PDF), cancellation, reports | Done |
| 6 | Exams, marks and report cards | Next |
| 7–11 | Certificates, dashboard, demo data, deployment, polish | Not started |

## Run locally

Prerequisites: Docker Desktop (running), Java 21, Node 20+.

```sh
cp .env.example .env && docker compose up -d     # Postgres 16
cd backend && ./mvnw spring-boot:run             # API on :8080 (Flyway migrates on start)
cd frontend && npm install && npm run dev        # UI on http://localhost:5173 (proxies /api → :8080)
```

Open http://localhost:5173. Health check: `curl http://localhost:8080/actuator/health`

### Dev logins

The `dev` profile seeds two fictional schools on startup. The password for both is
`DEV_SEED_PASSWORD` (default `Demo@12345`).

| School code | School | Username | Role |
|---|---|---|---|
| `ssv` | Sri Saraswathi Vidyalayam (State board, Telugu medium) | `principal` | Principal |
| `gps` | Greenfield Public School (CBSE, English medium) | `principal` | Principal |

Any other users (teachers, admins, accountants) are created by a principal under
**Setup → Users**. All data in this repository and its seeds is fictional.

The login password is case-sensitive and spaces count, so don't add a leading space.

## What you can do today

The sidebar shows only what your role allows.

| Area | Principal | Admin | Accountant | Teacher |
|---|---|---|---|---|
| Dashboard (setup checklist) | ✔ | ✔ | placeholder | placeholder |
| Students: list, search, profile | all | all | all, read-only | only sections they teach |
| Admit / edit / promote / import students | ✔ | ✔ | — | — |
| Caste, religion, category, Aadhaar | ✔ | ✔ | hidden | hidden |
| Mark attendance | any section, any past school day | any section, any past school day | — | own sections, today only |
| Monthly register, CSV export | all sections | all sections | — | own sections |
| Attendance summary (school-wide) | ✔ | ✔ | — | — |
| Fee setup, collect fees, concessions, receipts, fee reports | ✔ | ✔ | ✔ | — |
| Cancel a receipt | ✔ | — | ✔ | — |
| Setup: years, classes, sections, subjects, class subjects, teacher assignments, holidays | ✔ | ✔ | — | — |
| Setup: school profile and logo, modules and preferences, users | ✔ | — | — | — |

### Setting up a new school (principal)

The dashboard checklist walks through these:

1. **Setup → Academic years:** add `2026-27`. The first year becomes the current one.
2. **Setup → Classes & sections:** add classes in teaching order, then sections per class.
3. **Setup → Users:** add teachers (role *Teacher*), plus admins and accountants.
4. **Setup → Subjects**, then **Class subjects:** tick what each class studies.
5. **Setup → Teacher assignments:** pick a teacher for each subject in each section.
6. Optional: **School profile** (logo, colour), **Modules & preferences**, **Holidays**.

### Students

- **Admit student:** admission number (next one suggested), class–section (roll number
  is automatic if left empty), student details, 1–4 parents/guardians (at least one
  mobile number; empty contact rows are skipped), photo, and Aadhaar/category
  (principal and admin only).
- **All students:** search by name, admission number or parent's phone; filter by
  section and status.
- **Profile:** details, guardians, class history; **Edit**, **Change section**,
  **Mark as left**, and **Enrol this year** (for students without a place this year).
- **Promotion:** choose a section, choose next year's section, untick students to
  detain, confirm. Next year and its sections must exist first.
- **Import CSV:** download the template, fill it (dates `DD-MM-YYYY`), upload. The file
  is checked first; nothing is saved unless every row is valid.

### Attendance

Switch it on or off under **Setup → Modules & preferences**; when off, it disappears
from the menu and its pages and APIs answer "not found".

- **Mark attendance:** pick a section (a ✓ means already marked). Everyone starts as
  **P**; tap **A** (absent) or **L** (leave) for the few who aren't present, then
  **Save**. Made for phones: big buttons, and the Save bar stays at the bottom.
- **Rules:**
  - Holidays, weekly off days (set in preferences), future dates and dates outside
    the academic year can't be marked; the page says why.
  - Teachers mark only today, for sections they class-teach or teach a subject in.
  - Principal and admin can open any past school day (date picker, or `?date=` in
    the URL) and correct it.
- **Monthly register:** students × days for a month with totals and percentage;
  **Export CSV**.
- **Summary** (principal/admin): the day's attendance %, absent and on-leave counts,
  per class, and a list of sections not marked yet.
- **Student profile → Attendance:** a month calendar with that student's marks.
- **Percentage** = (present + ½ half-days) ÷ days marked.

### Fees

Switch it on or off under **Setup → Modules & preferences**.

1. **Fees → Fee setup:**
   - Add fee heads (Tuition, Transport, …). Pick the year and a class, then add
     installments (name, amount, due date).
   - New admissions get the class's dues automatically. For students already
     enrolled, press **Generate dues** (safe to repeat).
   - **Copy from another year** brings a whole plan forward, moving the due dates.
2. **Fees → Collect fee:**
   - Search the student and tick dues; you can lower the amount for a part payment.
   - Choose the mode: cheques need a number; UPI and others take an optional
     reference.
   - Then **Collect** and **Print receipt**. Receipt numbers look like
     `SSV/26-27/000123`: the receipt prefix from preferences, the academic year,
     and a counter with no gaps.
3. **Receipts:** search and filter the receipt book; open a receipt to print it again.
   Principal or accountant can **Cancel** it with a reason; the dues become payable
   again, and the receipt and its number are kept.
4. **Student profile → Fees:** the ledger for every year, concessions (with a reason),
   and that student's receipts.
5. **Fees → Reports:**
   - **Collection:** money received per day and mode.
   - **Dues:** who owes what, optionally overdue only, with Call and Collect links.
   - **By class:** expected vs. collected.

   Each report has **Export CSV**.

## Testing

### Automated

```sh
cd backend && ./mvnw clean test       # 100 tests; needs Docker running (Testcontainers)
cd frontend && npm run build && npm run lint
```

The backend suite includes a **School A vs School B isolation test for every module**.
Each one proves that one school cannot read, list, change or delete another school's
records.

### Manual walkthrough

1. Sign in to `ssv` as `principal`. The app takes on the school's colour and logo.
2. In a second browser tab, sign in to `gps`. Each tab shows only its own school.
3. Follow the dashboard checklist in `gps` to set up a school from scratch.
4. Admit a student, find them by the parent's phone, change their section, then create
   next year and promote them.
5. Create a teacher, sign in as them in another tab, and confirm they see only their
   sections' students and no sensitive fields.
   As them, open **Attendance → Mark attendance**, mark today, and check that
   yesterday is view-only for them.
6. As principal, deactivate that teacher. Their open session is signed out on the
   next click.

### Look at the data directly

```sh
docker exec -it school-erp-db psql -U school -d school_erp
#   select action, entity, entity_id, created_at from audit_log order by id desc limit 20;
#   select admission_no, first_name, aadhaar_last4, aadhaar_encrypted from student;   -- Aadhaar is ciphertext
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Tests fail with `Unresolved compilation problem` | The IDE's Java extension compiled into `backend/target/classes` without Lombok. Run `./mvnw clean test`. Better: enable Lombok in the IDE or give it a separate output folder. |
| `invalid value for parameter "TimeZone": "Asia/Calcutta"` | Start the app through `main()` or Maven (both pin `Asia/Kolkata`); don't run the class with a bare JVM. |
| Login says "Invalid school code, username or password" | Check for spaces in the password; school codes are `ssv` / `gps` in dev. After 10 failures in 15 minutes the username is locked for 15 minutes. |
| Tests fail with `Unresolved compilation problem` while your backend is running | Don't `clean` (the running app locks `target/`). Delete just the broken files: `grep -rlE "Unresolved compilation" --include=*.class target | xargs rm -f`, then run the tests again. |
| Docker / database not reachable | Start Docker Desktop, then `docker compose up -d`. |

## Project layout

```
backend/    Spring Boot 4 API (Java 21, Maven)
  src/main/java/com/schoolerp/<module>/    controller · service · repository · entity · dto
  src/main/resources/db/migration/         Flyway migrations (V1 is never edited)
  src/test/java/…                          integration tests (Testcontainers) and unit tests
frontend/   React 19 + Vite + TypeScript + Ant Design
  src/api/        typed API clients (one axios instance)
  src/auth/       auth context, guards
  src/layout/     app shell, sidebar menu, branding theme
  src/features/   pages per module (auth, dashboard, settings, students)
  src/components/ shared UI
docker-compose.yml   local Postgres
```
