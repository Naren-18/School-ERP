-- =====================================================================
-- School Management SaaS — V1 core schema (PostgreSQL 16)
-- Multi-tenant: every tenant-owned table carries school_id NOT NULL.
-- Each school is fully isolated: its own logins, no cross-school access.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Tenancy & identity
-- ---------------------------------------------------------------------

CREATE TABLE school (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    code            VARCHAR(40)  NOT NULL UNIQUE,      -- subdomain: code.yourapp.in
    board           VARCHAR(20)  NOT NULL,             -- STATE / CBSE / ICSE / OTHER
    medium          VARCHAR(30)  NOT NULL DEFAULT 'ENGLISH',
    address         TEXT,
    phone           VARCHAR(15),
    email           VARCHAR(120),
    logo_url        TEXT,
    primary_color   VARCHAR(9)   DEFAULT '#1e40af',
    -- Free-form per-school config: enabled modules, receipt prefix,
    -- week-off days, certificate footer text, etc. Validated in the app.
    settings        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_school_board CHECK (board IN ('STATE','CBSE','ICSE','OTHER'))
);

-- Staff + admins. Every user belongs to exactly one school and can
-- only ever see that school's data.
CREATE TABLE app_user (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id       BIGINT       NOT NULL REFERENCES school(id),
    username        VARCHAR(60)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,             -- BCrypt, never plain text
    full_name       VARCHAR(120) NOT NULL,
    designation     VARCHAR(60),                       -- 'Maths Teacher', 'Accountant'
    phone           VARCHAR(15),
    email           VARCHAR(120),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Usernames are unique within a school, so every school can have 'principal'.
CREATE UNIQUE INDEX uq_user_school_username ON app_user(school_id, lower(username));

CREATE TABLE user_role (
    user_id BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_role CHECK (role IN ('PRINCIPAL','ADMIN','ACCOUNTANT','TEACHER'))
);

-- ---------------------------------------------------------------------
-- 2. Academic structure
-- ---------------------------------------------------------------------

CREATE TABLE academic_year (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id   BIGINT      NOT NULL REFERENCES school(id),
    name        VARCHAR(20) NOT NULL,                  -- '2026-27'
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    is_current  BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (school_id, name),
    CONSTRAINT chk_year_dates CHECK (end_date > start_date)
);
-- Only one current year per school.
CREATE UNIQUE INDEX uq_current_year ON academic_year(school_id) WHERE is_current;

-- "class" is a Java keyword, hence school_class.
CREATE TABLE school_class (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id     BIGINT      NOT NULL REFERENCES school(id),
    name          VARCHAR(30) NOT NULL,                -- 'Nursery', 'LKG', 'Class 5'
    display_order INT         NOT NULL,
    UNIQUE (school_id, name)
);

CREATE TABLE section (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id        BIGINT      NOT NULL REFERENCES school(id),
    academic_year_id BIGINT      NOT NULL REFERENCES academic_year(id),
    class_id         BIGINT      NOT NULL REFERENCES school_class(id),
    name             VARCHAR(10) NOT NULL,             -- 'A', 'B', 'Rose'
    class_teacher_id BIGINT      REFERENCES app_user(id),
    capacity         INT,
    UNIQUE (academic_year_id, class_id, name)
);
CREATE INDEX idx_section_school_year ON section(school_id, academic_year_id);

CREATE TABLE subject (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id  BIGINT      NOT NULL REFERENCES school(id),
    name       VARCHAR(60) NOT NULL,                   -- 'Telugu', 'Mathematics'
    code       VARCHAR(15),
    type       VARCHAR(20) NOT NULL DEFAULT 'CORE',
    UNIQUE (school_id, name),
    CONSTRAINT chk_subject_type CHECK (type IN ('CORE','LANGUAGE','ELECTIVE','CO_CURRICULAR'))
);

-- Which subjects a class studies in a given year (replaces hardcoded columns).
CREATE TABLE class_subject (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id        BIGINT NOT NULL REFERENCES school(id),
    academic_year_id BIGINT NOT NULL REFERENCES academic_year(id),
    class_id         BIGINT NOT NULL REFERENCES school_class(id),
    subject_id       BIGINT NOT NULL REFERENCES subject(id),
    display_order    INT    NOT NULL DEFAULT 0,
    UNIQUE (academic_year_id, class_id, subject_id)
);

-- Who teaches what to which section.
CREATE TABLE teacher_assignment (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id   BIGINT NOT NULL REFERENCES school(id),
    section_id  BIGINT NOT NULL REFERENCES section(id),
    subject_id  BIGINT NOT NULL REFERENCES subject(id),
    teacher_id  BIGINT NOT NULL REFERENCES app_user(id),
    UNIQUE (section_id, subject_id)
);

-- ---------------------------------------------------------------------
-- 3. Students
-- ---------------------------------------------------------------------

-- The permanent person record. Year-specific placement lives in enrollment.
CREATE TABLE student (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id            BIGINT       NOT NULL REFERENCES school(id),
    admission_no         VARCHAR(30)  NOT NULL,
    admission_date       DATE         NOT NULL,
    first_name           VARCHAR(60)  NOT NULL,
    last_name            VARCHAR(60),
    date_of_birth        DATE         NOT NULL,
    gender               VARCHAR(10)  NOT NULL,
    mother_tongue        VARCHAR(30),
    -- Sensitive fields required on Indian admission/TC forms.
    -- Restrict to ADMIN/PRINCIPAL in the API and mask in lists.
    social_category      VARCHAR(10),                  -- GEN / BC / SC / ST / OTHER
    religion             VARCHAR(30),
    caste                VARCHAR(50),
    aadhaar_encrypted    BYTEA,                        -- encrypted in the app layer
    aadhaar_last4        CHAR(4),                      -- for display only
    identification_marks TEXT,
    address              TEXT,
    photo_url            TEXT,
    previous_school      VARCHAR(150),
    previous_tc_no       VARCHAR(50),
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    left_on              DATE,
    leaving_reason       TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (school_id, admission_no),
    CONSTRAINT chk_student_status CHECK (status IN ('ACTIVE','LEFT','TC_ISSUED','PASSED_OUT')),
    CONSTRAINT chk_gender CHECK (gender IN ('MALE','FEMALE','OTHER'))
);
CREATE INDEX idx_student_school_status ON student(school_id, status);
CREATE INDEX idx_student_name ON student(school_id, lower(first_name), lower(last_name));

CREATE TABLE guardian (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id   BIGINT       NOT NULL REFERENCES school(id),
    student_id  BIGINT       NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    relation    VARCHAR(20)  NOT NULL,                 -- FATHER / MOTHER / GUARDIAN
    full_name   VARCHAR(120) NOT NULL,
    occupation  VARCHAR(80),
    phone       VARCHAR(15),
    email       VARCHAR(120),
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_guardian_student ON guardian(student_id);
CREATE INDEX idx_guardian_phone ON guardian(school_id, phone);

-- One row per student per academic year: handles promotion, detention,
-- section changes and history without touching the student record.
CREATE TABLE enrollment (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id        BIGINT      NOT NULL REFERENCES school(id),
    student_id       BIGINT      NOT NULL REFERENCES student(id),
    academic_year_id BIGINT      NOT NULL REFERENCES academic_year(id),
    section_id       BIGINT      NOT NULL REFERENCES section(id),
    roll_no          INT,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_on        DATE        NOT NULL,
    left_on          DATE,
    UNIQUE (student_id, academic_year_id),
    CONSTRAINT chk_enrollment_status CHECK (status IN ('ACTIVE','PROMOTED','DETAINED','LEFT'))
);
CREATE INDEX idx_enrollment_section ON enrollment(section_id, status);
CREATE INDEX idx_enrollment_school_year ON enrollment(school_id, academic_year_id);

-- ---------------------------------------------------------------------
-- 4. Attendance (daily, per student)
-- ---------------------------------------------------------------------

CREATE TABLE attendance (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id     BIGINT      NOT NULL REFERENCES school(id),
    enrollment_id BIGINT      NOT NULL REFERENCES enrollment(id),
    att_date      DATE        NOT NULL,
    status        CHAR(1)     NOT NULL,                -- P / A / L(leave) / H(half-day)
    marked_by     BIGINT      REFERENCES app_user(id),
    marked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (enrollment_id, att_date),
    CONSTRAINT chk_att_status CHECK (status IN ('P','A','L','H'))
);
-- Powers "today's attendance %" on the school and group dashboards.
CREATE INDEX idx_attendance_school_date ON attendance(school_id, att_date);

-- Declared holidays so working-day counts are correct.
CREATE TABLE holiday (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id        BIGINT       NOT NULL REFERENCES school(id),
    academic_year_id BIGINT       NOT NULL REFERENCES academic_year(id),
    holiday_date     DATE         NOT NULL,
    description      VARCHAR(100) NOT NULL,
    UNIQUE (school_id, holiday_date)
);

-- ---------------------------------------------------------------------
-- 5. Fees
-- ---------------------------------------------------------------------

CREATE TABLE fee_head (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id  BIGINT      NOT NULL REFERENCES school(id),
    name       VARCHAR(60) NOT NULL,                   -- Tuition, Transport, Exam, Books
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (school_id, name)
);

-- The fee plan: what each class owes, split into dated installments.
CREATE TABLE fee_installment (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id        BIGINT        NOT NULL REFERENCES school(id),
    academic_year_id BIGINT        NOT NULL REFERENCES academic_year(id),
    class_id         BIGINT        NOT NULL REFERENCES school_class(id),
    fee_head_id      BIGINT        NOT NULL REFERENCES fee_head(id),
    name             VARCHAR(40)   NOT NULL,           -- 'Term 1', 'June', 'Annual'
    amount           NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    due_date         DATE          NOT NULL,
    UNIQUE (academic_year_id, class_id, fee_head_id, name)
);

-- What a specific student owes, generated from the plan at enrollment.
-- Concessions (sibling, staff ward, scholarship) are applied per row.
CREATE TABLE student_fee_due (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id          BIGINT        NOT NULL REFERENCES school(id),
    enrollment_id      BIGINT        NOT NULL REFERENCES enrollment(id),
    fee_installment_id BIGINT        NOT NULL REFERENCES fee_installment(id),
    amount             NUMERIC(10,2) NOT NULL,
    concession         NUMERIC(10,2) NOT NULL DEFAULT 0,
    concession_reason  VARCHAR(100),
    paid_amount        NUMERIC(10,2) NOT NULL DEFAULT 0,  -- maintained on receipt save/cancel
    UNIQUE (enrollment_id, fee_installment_id),
    CONSTRAINT chk_due_amounts CHECK (concession >= 0 AND concession <= amount
                                      AND paid_amount >= 0 AND paid_amount <= amount - concession)
);
CREATE INDEX idx_fee_due_school ON student_fee_due(school_id);
CREATE INDEX idx_fee_due_enrollment ON student_fee_due(enrollment_id);

CREATE TABLE fee_receipt (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id        BIGINT        NOT NULL REFERENCES school(id),
    academic_year_id BIGINT        NOT NULL REFERENCES academic_year(id),
    receipt_no       VARCHAR(30)   NOT NULL,           -- e.g. 'SVS/26-27/000123'
    enrollment_id    BIGINT        NOT NULL REFERENCES enrollment(id),
    paid_on          DATE          NOT NULL,
    total_amount     NUMERIC(10,2) NOT NULL CHECK (total_amount > 0),
    payment_mode     VARCHAR(10)   NOT NULL,
    reference_no     VARCHAR(60),                      -- UPI ref / cheque no
    collected_by     BIGINT        NOT NULL REFERENCES app_user(id),
    -- Receipts are never deleted, only cancelled, for an honest audit trail.
    cancelled        BOOLEAN       NOT NULL DEFAULT FALSE,
    cancelled_by     BIGINT        REFERENCES app_user(id),
    cancelled_at     TIMESTAMPTZ,
    cancel_reason    VARCHAR(200),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (school_id, receipt_no),
    CONSTRAINT chk_payment_mode CHECK (payment_mode IN ('CASH','UPI','CHEQUE','CARD','BANK','ONLINE'))
);
CREATE INDEX idx_receipt_school_date ON fee_receipt(school_id, paid_on);
CREATE INDEX idx_receipt_enrollment ON fee_receipt(enrollment_id);

CREATE TABLE fee_receipt_line (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_id         BIGINT        NOT NULL REFERENCES fee_receipt(id),
    student_fee_due_id BIGINT        NOT NULL REFERENCES student_fee_due(id),
    amount             NUMERIC(10,2) NOT NULL CHECK (amount > 0)
);
CREATE INDEX idx_receipt_line_receipt ON fee_receipt_line(receipt_id);

-- Gap-free receipt numbers per school per year.
-- Use SELECT ... FOR UPDATE on this row inside the receipt transaction.
CREATE TABLE receipt_counter (
    school_id        BIGINT NOT NULL REFERENCES school(id),
    academic_year_id BIGINT NOT NULL REFERENCES academic_year(id),
    next_value       BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (school_id, academic_year_id)
);

-- ---------------------------------------------------------------------
-- 6. Exams & marks (no per-subject columns: works for any board)
-- ---------------------------------------------------------------------

CREATE TABLE grading_scale (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id  BIGINT      NOT NULL REFERENCES school(id),
    name       VARCHAR(60) NOT NULL,                   -- 'TS CCE 10-point', 'CBSE 9-point'
    UNIQUE (school_id, name)
);

CREATE TABLE grading_band (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grading_scale_id BIGINT       NOT NULL REFERENCES grading_scale(id) ON DELETE CASCADE,
    min_percent      NUMERIC(5,2) NOT NULL,
    max_percent      NUMERIC(5,2) NOT NULL,
    grade            VARCHAR(5)   NOT NULL,            -- A1, A2, B1 ...
    grade_point      NUMERIC(4,2),
    remark           VARCHAR(60),
    CONSTRAINT chk_band CHECK (min_percent >= 0 AND max_percent <= 100 AND min_percent <= max_percent)
);

CREATE TABLE exam (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id        BIGINT      NOT NULL REFERENCES school(id),
    academic_year_id BIGINT      NOT NULL REFERENCES academic_year(id),
    name             VARCHAR(40) NOT NULL,             -- 'FA1', 'SA1', 'Term 1', 'Unit Test 2'
    grading_scale_id BIGINT      NOT NULL REFERENCES grading_scale(id),
    start_date       DATE,
    end_date         DATE,
    display_order    INT         NOT NULL DEFAULT 0,
    -- Weight in the final result, e.g. FA = 20, SA = 80. NULL = not counted.
    weightage        NUMERIC(5,2),
    is_published     BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (academic_year_id, name)
);

-- Per class, per subject settings for an exam.
CREATE TABLE exam_subject (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id   BIGINT       NOT NULL REFERENCES school(id),
    exam_id     BIGINT       NOT NULL REFERENCES exam(id) ON DELETE CASCADE,
    class_id    BIGINT       NOT NULL REFERENCES school_class(id),
    subject_id  BIGINT       NOT NULL REFERENCES subject(id),
    max_marks   NUMERIC(5,2) NOT NULL CHECK (max_marks > 0),
    pass_marks  NUMERIC(5,2),
    exam_date   DATE,
    UNIQUE (exam_id, class_id, subject_id)
);

CREATE TABLE mark (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id       BIGINT       NOT NULL REFERENCES school(id),
    exam_subject_id BIGINT       NOT NULL REFERENCES exam_subject(id) ON DELETE CASCADE,
    enrollment_id   BIGINT       NOT NULL REFERENCES enrollment(id),
    marks_obtained  NUMERIC(5,2),
    is_absent       BOOLEAN      NOT NULL DEFAULT FALSE,
    remarks         VARCHAR(150),
    entered_by      BIGINT       REFERENCES app_user(id),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (exam_subject_id, enrollment_id),
    CONSTRAINT chk_mark CHECK (
        (is_absent AND marks_obtained IS NULL) OR (NOT is_absent AND marks_obtained >= 0))
);
CREATE INDEX idx_mark_enrollment ON mark(enrollment_id);

-- ---------------------------------------------------------------------
-- 7. Certificates (bonafide, TC, ...) with per-school templates
-- ---------------------------------------------------------------------

CREATE TABLE certificate_template (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id     BIGINT      NOT NULL REFERENCES school(id),
    type          VARCHAR(30) NOT NULL,                -- BONAFIDE / TC / CONDUCT / STUDY
    html_template TEXT        NOT NULL,                -- placeholders like {{student.fullName}}
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_active_template ON certificate_template(school_id, type) WHERE active;

CREATE TABLE issued_certificate (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id   BIGINT      NOT NULL REFERENCES school(id),
    student_id  BIGINT      NOT NULL REFERENCES student(id),
    type        VARCHAR(30) NOT NULL,
    serial_no   VARCHAR(30) NOT NULL,
    issued_on   DATE        NOT NULL,
    issued_by   BIGINT      NOT NULL REFERENCES app_user(id),
    purpose     VARCHAR(150),
    data        JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- snapshot of values printed
    pdf_url     TEXT,
    UNIQUE (school_id, type, serial_no)
);
CREATE INDEX idx_cert_student ON issued_certificate(student_id);

-- ---------------------------------------------------------------------
-- 8. Audit log (who changed what — schools will ask, especially for fees)
-- ---------------------------------------------------------------------

CREATE TABLE audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id   BIGINT       NOT NULL REFERENCES school(id),
    user_id     BIGINT       REFERENCES app_user(id),
    action      VARCHAR(30)  NOT NULL,                 -- CREATE / UPDATE / DELETE / LOGIN / CANCEL
    entity      VARCHAR(40)  NOT NULL,
    entity_id   BIGINT,
    details     JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_school_time ON audit_log(school_id, created_at DESC);
