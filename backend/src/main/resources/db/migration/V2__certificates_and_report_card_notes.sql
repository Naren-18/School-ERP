-- Phase 7: certificate serial numbers, and report card extras
-- (co-scholastic grades and class teacher remarks).

-- Gap-free certificate serial numbers per school and type (BONAFIDE, TC).
-- Locked FOR UPDATE inside the issuing transaction, like receipt_counter.
CREATE TABLE certificate_counter (
    school_id   BIGINT      NOT NULL REFERENCES school(id),
    type        VARCHAR(30) NOT NULL,
    next_value  BIGINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (school_id, type)
);

-- Areas a school grades outside academics (Art, Sports, Discipline, ...).
CREATE TABLE co_scholastic_area (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id     BIGINT      NOT NULL REFERENCES school(id),
    name          VARCHAR(60) NOT NULL,
    display_order INT         NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_co_scholastic_area ON co_scholastic_area(school_id, lower(name));

-- Per student per exam: the class teacher's remark and co-scholastic grades.
CREATE TABLE report_card_note (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id      BIGINT       NOT NULL REFERENCES school(id),
    exam_id        BIGINT       NOT NULL REFERENCES exam(id),
    enrollment_id  BIGINT       NOT NULL REFERENCES enrollment(id),
    teacher_remark VARCHAR(300),
    co_scholastic  JSONB        NOT NULL DEFAULT '{}'::jsonb,  -- {"<areaId>": "A"}
    updated_by     BIGINT       REFERENCES app_user(id),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (exam_id, enrollment_id)
);
CREATE INDEX idx_report_card_note_school ON report_card_note(school_id, exam_id);

CREATE INDEX idx_issued_certificate_school ON issued_certificate(school_id, issued_on DESC);
