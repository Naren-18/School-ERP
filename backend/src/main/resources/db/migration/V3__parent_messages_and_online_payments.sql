-- Parent messages on WhatsApp, and online fee payments (Razorpay) at the counter.

-- The parent agreed to receive WhatsApp messages on the primary contact's number.
-- Off by default: nobody is messaged without consent.
ALTER TABLE student ADD COLUMN whatsapp_consent BOOLEAN NOT NULL DEFAULT FALSE;

-- One row per WhatsApp message tried, sent or failed. Students skipped for no
-- consent or no mobile number are reported on screen, not stored.
CREATE TABLE notification_log (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id           BIGINT       NOT NULL REFERENCES school(id),
    type                VARCHAR(30)  NOT NULL,   -- ABSENCE / FEE_REMINDER / PAYMENT_RECEIVED / RESULTS_PUBLISHED
    student_id          BIGINT       NOT NULL REFERENCES student(id),
    phone               VARCHAR(15)  NOT NULL,
    ref_date            DATE,                    -- the day of an absence
    ref_id              BIGINT,                  -- the receipt or exam a message is about
    status              VARCHAR(10)  NOT NULL,
    error               VARCHAR(300),
    provider_message_id VARCHAR(100),
    sent_by             BIGINT       REFERENCES app_user(id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_status CHECK (status IN ('SENT','FAILED'))
);
CREATE INDEX idx_notification_log_school ON notification_log(school_id, created_at DESC);
CREATE INDEX idx_notification_log_ref ON notification_log(school_id, type, ref_date, student_id);

-- A payment started with the gateway. The dues and amounts are fixed here when
-- the order is created, so the browser cannot change what gets paid; the
-- receipt is created only after the gateway confirms the payment.
CREATE TABLE online_payment (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id    BIGINT        NOT NULL REFERENCES school(id),
    student_id   BIGINT        NOT NULL REFERENCES student(id),
    gateway      VARCHAR(20)   NOT NULL,
    order_id     VARCHAR(60)   NOT NULL UNIQUE,
    amount       NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    lines        JSONB         NOT NULL,   -- {"<dueId>": "500.00"}
    status       VARCHAR(10)   NOT NULL DEFAULT 'CREATED',
    payment_id   VARCHAR(60),
    receipt_id   BIGINT        REFERENCES fee_receipt(id),
    failure      VARCHAR(300),
    created_by   BIGINT        NOT NULL REFERENCES app_user(id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_online_payment_status CHECK (status IN ('CREATED','PAID','FAILED'))
);
CREATE INDEX idx_online_payment_school ON online_payment(school_id, created_at DESC);
