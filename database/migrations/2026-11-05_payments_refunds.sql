-- 2026-11-05: refunds queue (Admin > Sales > Refunds) and payment reconciliation.
-- Every payment that must be given back lands here once: a course paid twice, an original that sold to someone
-- else first (marketplace_orders REFUND_REQUIRED), shop lines that could not be supplied, a cancelled live course.
-- Nothing is refunded automatically: staff confirm each one, which then calls Razorpay / PayPal. Safe to run twice.

CREATE TABLE IF NOT EXISTS payment_refunds (
    id                BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    source            VARCHAR(20)    NOT NULL,                 -- COURSE | MARKETPLACE | SHOP
    source_ref        VARCHAR(50)    NOT NULL,                 -- orders.id / marketplace_orders.order_id / shop_orders.order_id
    payment_ref       VARCHAR(255)   NOT NULL,                 -- Razorpay payment id / PayPal capture id / test id
    gateway           VARCHAR(20)    NOT NULL,                 -- RAZORPAY | PAYPAL | TEST | MANUAL
    amount            DECIMAL(12, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    reason            VARCHAR(40)    NOT NULL,                 -- DUPLICATE_PAYMENT | SOLD_TO_OTHER | NOT_SUPPLIED | COURSE_CANCELLED | OTHER
    detail            VARCHAR(500)   NULL,
    buyer_email       VARCHAR(255)   NULL,
    user_id           BIGINT         NULL,
    status            VARCHAR(12)    NOT NULL DEFAULT 'PENDING', -- PENDING | PROCESSING | REFUNDED | FAILED | DISMISSED
    gateway_refund_id VARCHAR(255)   NULL,
    error             VARCHAR(500)   NULL,
    decided_by        BIGINT         NULL,                     -- admins.id
    note              VARCHAR(500)   NULL,
    created_at        DATETIME       NOT NULL,
    decided_at        DATETIME       NULL,
    UNIQUE INDEX payment_refunds_source_unique (source, source_ref, payment_ref),
    INDEX payment_refunds_status_index (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- one row per reconciliation pass that changed something, so staff can see late matches
CREATE TABLE IF NOT EXISTS payment_reconciliations (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    source      VARCHAR(20)   NOT NULL,
    source_ref  VARCHAR(50)   NOT NULL,
    gateway     VARCHAR(20)   NOT NULL,
    payment_ref VARCHAR(255)  NULL,
    outcome     VARCHAR(30)   NOT NULL,                        -- PAID | REFUND_REQUIRED | CAPTURED | AMOUNT_MISMATCH | ERROR
    minutes_late INT          NOT NULL DEFAULT 0,
    detail      VARCHAR(500)  NULL,
    created_at  DATETIME      NOT NULL,
    INDEX payment_reconciliations_time_index (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- orders.payment_status gains 4 = refund due (paid, but must be given back: e.g. the course was already paid)
ALTER TABLE orders MODIFY payment_status TINYINT NOT NULL DEFAULT 0;
