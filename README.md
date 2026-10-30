# School ERP

A hosted, multi-tenant school management system (MVP for a sales demo). Many schools
share one deployment; each school is fully isolated, with its own logins and data.

- **Rules for building it:** [`CLAUDE.md`](CLAUDE.md)
- **Phased build plan and progress:** [`PLAN.md`](PLAN.md)
- **How it is built (design, modules, API, decisions):** [`ARCHITECTURE.md`](ARCHITECTURE.md)
- **How it was built, phase by phase (what, how, problems and fixes):** [`BUILD_HISTORY.md`](BUILD_HISTORY.md)

## Status

| Phase | What | State |
|---|---|---|
| 0 | Repository and local environment | Done |
| 1 | Tenancy, login, errors, audit | Done |
| 2 | School settings and academic setup | Done |
| 3 | Students, guardians, enrollment, promotion, CSV import | Done |
| 4 | Attendance: mobile marking, monthly register, school summary | Done |
| 5 | Fees: plans, dues, collection, receipts (PDF), cancellation, reports | Done |
| 6 | Exams: grading scales, papers, marks entry, results, report cards (PDF) | Done |
| 7 | Certificates (bonafide, TC), editable templates, report card remarks and co-scholastic grades | Done |
| 8 | School dashboard: role-aware stats, donut, trend and comparison charts, teacher view | Done |
| 9 | Demo data: three fictional schools, about 390 students each, a one-off seeder | Done |
| 9b | Parent messages on WhatsApp (absence, fee reminder, payment, results; consent; log), online fee payment at the counter (Razorpay), TC dues warning, dashboard redesign | Done (WhatsApp and Razorpay tested with stand-ins; real keys not yet tried) |
| 10 | Production deployment | Next |
| 11 | Demo polish | Not started |

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

### Demo schools (for sales demos)

Three fully populated fictional schools, made by a separate one-off seeder: the `demo`
profile plus the `--seed-demo` flag. It needs `DEMO_SEED_PASSWORD`, which has no
default. It creates each school whose code doesn't exist yet, so running it again
changes nothing. Then it exits.

```sh
cd backend
./mvnw -q -DskipTests package
DEMO_SEED_PASSWORD='choose-a-password' java -jar target/school-erp-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev,demo --seed-demo --server.port=0
```

- Run it from `backend/` so the logos go into the same file store as the app.
- It takes a few minutes; the log shows each step.
- Smaller runs: `DEMO_STUDENTS_PER_SECTION` (default 15) and `DEMO_ATTENDANCE_DAYS`
  (default 60).
- `--server.port=0` picks a free port, so your normal backend can keep running.

| School code | School | Board, medium | Grading and exams | Fees |
|---|---|---|---|---|
| `vjhs` | Vidya Jyothi High School, Hanamkonda | State, Telugu | TS CCE; FA1–4, SA1–2 | 3 tuition terms + books & uniform |
| `lvps` | Lotus Valley Public School, Hyderabad | CBSE, English | CBSE 9-point; Term 1, Term 2 | Quarterly tuition + annual charges; 5-day week |
| `sems` | Sunrise English Medium School, Khammam | State, English | TS CCE; FA1, FA2, SA1, SA2 | 2 tuition terms + transport (about 40% opt out) |

Each school has:
- Nursery to Class 10, 2 sections each, about 390 students with guardians.
- Up to 60 days of attendance, with today partly marked.
- Fees paid on time, in part or not yet, plus sibling concessions.
- The first exam that is over published, with marks, remarks and co-scholastic grades;
  the next one has marks partly entered.
- 5 bonafide certificates and 2 TCs.
- Holidays, a PNG logo and its own colour.

Logins, all with the `DEMO_SEED_PASSWORD`:

| Username | Role | Notes |
|---|---|---|
| `principal` | Principal | everything, including settings and templates |
| `admin` | Admin | everything except users and settings |
| `accountant` | Accountant | fees and receipts |
| `teacher1` | Teacher | class teacher of Class 5 – A; Mathematics in Classes 5–7 |
| `teacher2` | Teacher | class teacher of Class 8 – B; English in Classes 8–10 |

There are also `teacher3`… `teacher26`, so every subject and section has a teacher.

**Everything is made up:**
- Names are random combinations of common first names and surnames.
- Phone numbers are in the `900…` range.
- Email addresses use the `.example` domain.
- No Aadhaar number or caste is recorded.

## What you can do today

The sidebar shows only what your role allows.

| Area | Principal | Admin | Accountant | Teacher |
|---|---|---|---|---|
| Dashboard | whole school: students, attendance, fees, exams, admissions, charts | same as principal | students and fees | their sections, attendance to take, marks to enter |
| Students: list, search, profile | all | all | all, read-only | only sections they teach |
| Admit / edit / promote / import students | ✔ | ✔ | — | — |
| Caste, religion, category, Aadhaar | ✔ | ✔ | hidden | hidden |
| Mark attendance | any section, any past school day | any section, any past school day | — | own sections, today only |
| Monthly register, CSV export | all sections | all sections | — | own sections |
| Attendance summary (school-wide) | ✔ | ✔ | — | — |
| Fee setup, collect fees, concessions, receipts, fee reports | ✔ | ✔ | ✔ | — |
| Cancel a receipt | ✔ | — | ✔ | — |
| Exam setup: grading scales, exams, papers, publish | ✔ | ✔ | — | — |
| Enter marks | any section and subject | any section and subject | — | only their assigned subject in that section |
| Results sheet, CSV, report cards (PDF), student Marks tab | all sections | all sections | — | sections they teach or class-teach |
| Co-scholastic areas | ✔ | ✔ | — | — |
| Remarks & co-scholastic grades | any section | any section | — | sections they class-teach |
| Issue certificates, register, re-download | ✔ | ✔ | — | — |
| Certificate and report card templates | ✔ | — | — | — |
| Setup: years, classes, sections, subjects, class subjects, teacher assignments, holidays | ✔ | ✔ | — | — |
| Setup: school profile and logo, modules and preferences, users | ✔ | — | — | — |

### Dashboard

The first screen after login. It refreshes every 5 minutes, or with the refresh button.

- **Everyone:** a greeting band in the school's colour with the date, the academic year
  and quick actions for the role (Mark attendance, Collect fee, Admit student).
- **Principal and admin:** grouped as At a glance, Trends, Classes & exams, To do & recent.
  - Headline numbers: active students; today's attendance % and how many sections are
    marked; fees collected today and this month; dues outstanding and overdue.
  - Charts: attendance % and fees collected over the last 14 days, and students by
    class. Hover for exact values.
  - Lists: sections not yet marked today (click to mark), the 5 classes with the most
    overdue fees, upcoming exams, and recent admissions.
  - Until the school is set up, the setup checklist shows on top.
- **Accountant:** students, the fee numbers and chart, and overdue classes.
- **Teacher:** their sections with a **Take attendance** button until today is marked,
  and the papers they still have marks to enter for (unpublished exams).
- Widgets for a module that is switched off are left out.

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
  - Adding a holiday on a day that already has attendance asks whether to **clear
    those marks** (so the day doesn't count) or keep them.
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
     Tick students and press **Remind on WhatsApp** to send each parent their balance.
   - **By class:** expected vs. collected.

   Each report has **Export CSV**.
6. **Online payment (Razorpay):** when the Razorpay keys are set, Collect fee also shows
   **Pay online**. The payment window takes UPI, card or netbanking; the receipt
   (mode Online, the Razorpay payment id as reference) is made only after the server
   has checked the payment with Razorpay.
7. **Every receipt** (cash or online) sends the parent a WhatsApp confirmation, if the
   parent agreed and the school has payment confirmations switched on.

### WhatsApp messages to parents

- **Consent:** tick *Parent agrees to receive WhatsApp messages* on the admission form
  (or put `YES` in the `whatsapp_consent` CSV column). Nobody else is messaged. It goes
  to the primary contact's mobile number.
- **Absence alerts:** after saving attendance, **Tell parents of absent students** lists
  the absentees (ticked unless already messaged that day) with one send button.
  Teachers send for their own sections, today only.
- **Fee reminders** (Fee reports → Dues), **payment confirmations** (automatic) and
  **results messages** (Results → **Tell parents**, published exams only, principal/admin).
- **Setup → Modules & preferences → WhatsApp messages** switches each kind on or off.
- **WhatsApp messages** (sidebar, principal/admin): every message tried, sent or failed
  with the reason, filterable by date, kind, status, student or phone.
- Without WhatsApp keys, messages are only written to the backend log and the screens
  say *WhatsApp is not connected yet*. See [WhatsApp and Razorpay](#whatsapp-and-razorpay).

### Exams

Switch it on or off under **Setup → Modules & preferences**.

1. **Exams → Exam setup → Grading scales:** add **CBSE 9-point** or **TS state CCE** with
   one click, then edit the bands if your school differs. A percentage gets the grade
   with the highest "from %" it reaches.
2. **Exam setup → Exams:** add FA1, SA1, … with a grading scale and an optional
   weightage (e.g. FA 20, SA 80) for the final result.
   - **Papers** sets max/pass marks per class and subject.
   - **Apply to all class subjects** creates a paper for every subject mapped under
     Setup → Class subjects.
3. **Exams → Enter marks:** pick the exam, then a "class – section · subject".
   - Type marks and press **Enter** to move down. **AB** (or **a**) means absent; empty
     means not entered. Then **Save**; only changed rows are sent.
   - Works on a phone (numeric keypad).
   - Teachers only see the subjects assigned to them.
4. **Publish** (Exam setup) locks marks and allows report cards. **Unpublish** to correct.
5. **Exams → Results & report cards:**
   - Students × subjects with total, %, grade, pass/fail and rank, plus **CSV** export.
   - **All report cards** prints one PDF for the section (one page per student).
     The PDF icon on a row prints one student.
   - **Annual cards** combine every published exam with the weighted final.
   - Rank and attendance on the card follow **Setup → Modules & preferences → Report card**.
6. **Student profile → Marks:** published exams of the year and the final result.
7. **Co-scholastic grades and remarks:**
   - Set up the areas under **Exam setup → Co-scholastic & report card** (Art, Sports, Discipline, …).
   - The class teacher fills in **Exams → Remarks & grades**: an A/B/C per area and a
     remark per student, for each exam. It locks when the exam is published.
   - Both print on the report card; the annual card uses the last published exam's.
8. **Report card template** (principal): **Edit report card template** opens an HTML
   editor with a live preview, just like the certificate templates.

Result rules:
- Absent counts as 0 and fails that subject.
- Below pass marks fails the subject.
- A subject that is not entered makes the result "Incomplete"; it is not counted as 0.
- The final % is Σ(weightage × exam %) ÷ Σ weightage.

### Certificates

Switch it on or off under **Setup → Modules & preferences**.

1. **Student profile → Certificates → Issue certificate:**
   - **Bonafide** (students studying now), with a purpose.
   - **Transfer certificate**: date and reason for leaving, conduct, promotion and
     remarks. Issuing a TC marks an active student as left and sets the status to
     **TC issued**. A TC can be issued only once.
   - When the student still owes fees, the TC dialog shows the balance (and how much
     is overdue) with a **Collect fee** link; issuing needs a tick on *Issue the TC
     anyway*.
   - **Preview** shows the PDF first; **Issue** numbers it and opens it for printing.
2. **Numbers** look like `SSV/BON/0001` and `SSV/TC/0001`: the certificate prefix from
   preferences, the type, and a gap-free counter per school and type.
3. **Certificates → Register:** every certificate issued, searchable by number,
   student or admission no., with type and date filters. Any of them can be
   downloaded again, exactly as issued.
4. **Certificates → Templates** (principal):
   - Edit the bonafide and TC wording in HTML. The live preview uses sample data,
     and you can click a placeholder such as `{{student.fullName}}` to insert it.
   - The editor refuses unknown placeholders and markup that won't print.
   - **Reset to built-in** goes back to the default. Certificates already issued keep
     the version they were printed with.
5. **The logo prints only when it is a PNG, JPEG or WebP**, uploaded under School profile.
   The dev seed's SVG logos don't print.

## Hosted demo

To put the demo online on a VPS (Docker Compose, HTTPS through sslip.io, no domain
needed), follow [`deploy/demo/README.md`](deploy/demo/README.md). It runs the `prod`
profile with its own database and secrets, and seeds the three demo schools once.
The production setup (R2, backups, `make deploy`) is Phase 10.

## WhatsApp and Razorpay

Both are optional. Keys go **only** in `.env` (git-ignored; on the demo server
`deploy/demo/.env`), never in the code. Empty keys: WhatsApp messages are written to
the backend log, and online payment is hidden. Restart the backend after changing them.

```
WHATSAPP_ACCESS_TOKEN=
WHATSAPP_PHONE_NUMBER_ID=
RAZORPAY_KEY_ID=
RAZORPAY_KEY_SECRET=
```

### Razorpay (test mode is free)

1. Sign up at dashboard.razorpay.com (test mode needs no KYC; choose an individual /
   unregistered business if asked for a PAN).
2. Switch on **Test Mode** → **Account & Settings → API Keys → Generate Test Key**.
3. Put the Key ID (`rzp_test_…`) and the secret in `.env`.
4. In the payment window use Razorpay's test UPI ids or cards (listed in their docs);
   no real money moves. Live keys need full KYC, and the money then goes to the
   account that owns the keys (one platform account for now; per-school accounts
   are future work).

### WhatsApp (Meta Cloud API)

1. developers.facebook.com → **Create app** → WhatsApp use case. (A brand-new Facebook
   account may have to wait before it can create a business portfolio.)
2. **WhatsApp → API Setup** shows the test sender number and its **Phone number ID**.
   Add up to 5 of your own phones as test recipients there; test mode only delivers
   to those, so give a few demo students one of these numbers as primary contact
   and tick their consent.
3. Use a **system user token** (Business settings → Users → System users, permissions
   `whatsapp_business_messaging` and `whatsapp_business_management`); the token on
   the API Setup page expires in 24 hours.
4. Create these templates (**WhatsApp Manager → Message templates**), category
   **Utility**, language **English** (`en`), with exactly these names and placeholders:

| Name | Body |
|---|---|
| `absence_alert` | Dear Parent, {{1}} of {{2}} was marked absent at {{3}} on {{4}}. If you were not aware of this, please contact the school. |
| `fee_reminder` | Dear Parent, a fee balance of ₹{{1}} is pending for {{2}} of {{3}} at {{4}}. Please pay at the school office at the earliest. Kindly ignore this message if already paid. |
| `payment_received` | Dear Parent, we have received ₹{{1}} towards the fees of {{2}} ({{3}}). Receipt no. {{4}} dated {{5}}, issued by {{6}}. Thank you. |
| `results_published` | Dear Parent, the results of {{1}} for {{2}} of {{3}} are now available at {{4}}. Please contact the class teacher for the report card. |

Placeholders: student name, class, school, date (DD-MM-YYYY), amount (`1,23,456.00`),
receipt number, exam name, in the order the app sends them (see `MessageType`).
Going live needs Meta business verification, a dedicated number and a payment method
on Meta; messages are charged per message (utility rate). Parents not on WhatsApp
show as *Failed* in the log.

## Testing

### Automated

```sh
cd backend && ./mvnw clean test       # 140 tests; needs Docker running (Testcontainers)
cd frontend && npm run build && npm run lint
```

The backend suite includes a **School A vs School B isolation test for every module**.
Each one proves that one school cannot read, list, change or delete another school's
records.

### Manual walkthrough

1. Sign in to `ssv` as `principal`. The app takes on the school's colour and logo, and the
   dashboard shows donuts (today's attendance, fees collected vs due, payment modes, fee
   heads), fees per month, boys/girls and attendance per class, exam subject averages and
   the to-do lists. Sign in as `sunitha` (demo teacher, same dev password) to see the teacher dashboard.
2. In a second browser tab, sign in to `gps`. Each tab shows only its own school.
3. Follow the dashboard checklist in `gps` to set up a school from scratch.
4. Admit a student, find them by the parent's phone, change their section, then create
   next year and promote them.
5. Create a teacher, sign in as them in another tab, and confirm they see only their
   sections' students and no sensitive fields.
   As them, open **Attendance → Mark attendance**, mark today, and check that
   yesterday is view-only for them.
6. As principal, open **Exams → Results & report cards** for FA1 · Class 5 – A (demo marks
   are already entered and FA1 is published) and press **All report cards**. SA1 has marks
   in draft: publish it under Exam setup and the student **Marks** tab shows the weighted final.
   The FA1 cards show co-scholastic grades and the class teacher's remark (demo data).
7. As principal, open a student in Class 5 – A → **Certificates → Issue certificate** and
   issue a bonafide (Preview first). **Certificates → Register** lists it next to the demo
   `SSV/BON/0001` and `SSV/TC/0001`. Under **Certificates → Templates**, change a word and
   watch the live preview.
8. Add a holiday on a day that has attendance: the page asks whether to clear that day's marks.
9. As principal, deactivate that teacher. Their open session is signed out on the
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
| Frontend build fails with `Cannot find module '@tailwindcss/postcss'` | Vite found a `postcss.config.*` in a folder *above* the project (from another project). Move or rename that file; this project has no PostCSS config. |
| WhatsApp screens say "not connected" | `WHATSAPP_ACCESS_TOKEN` or `WHATSAPP_PHONE_NUMBER_ID` is empty for the backend. Set both in `.env` and restart it. |
| A WhatsApp message failed | The reason is in **WhatsApp messages** (sidebar). Common: the template isn't approved yet, the number isn't a test recipient (test mode), or the token expired. |
| No "Pay online" on Collect fee | `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` are empty for the backend. |

## Project layout

```
backend/    Spring Boot 4 API (Java 21, Maven)
  src/main/java/com/schoolerp/<module>/    controller · service · repository · entity · dto
  src/main/resources/db/migration/         Flyway migrations (V1 is never edited)
  src/main/resources/pdf-templates/        built-in certificate and report card templates
  src/test/java/…                          integration tests (Testcontainers) and unit tests
frontend/   React 19 + Vite + TypeScript + Ant Design
  src/api/        typed API clients (one axios instance)
  src/auth/       auth context, guards
  src/layout/     app shell, sidebar menu, branding theme
  src/features/   pages per module (auth, dashboard, settings, students, attendance, fees, exams, certificates)
  src/components/ shared UI
  src/utils/      formatting (DD-MM-YYYY, ₹), CSV export, file download, PDF in a new tab
docker-compose.yml   local Postgres
```
