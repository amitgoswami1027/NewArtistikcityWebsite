-- 2026-11-05: escrow ledger, artist payouts and payment disputes (Admin > Sales > Payouts & ledger).
-- ledger_entries is append-only: a sale is written once as gross (ESCROW), the platform commission (PLATFORM) and
-- the artist's share (ARTIST, payable after app.ledger.payout-after-days); refunds, payouts and dispute outcomes
-- are new rows, never edits. idem_key makes every posting idempotent. Balances are sums. Safe to run twice.

CREATE TABLE IF NOT EXISTS ledger_entries (
    id           BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    idem_key     VARCHAR(160)   NOT NULL,
    entry_type   VARCHAR(20)    NOT NULL,  -- SALE | COMMISSION | ARTIST_SHARE | REFUND | PAYOUT | DISPUTE_LOSS | ADJUSTMENT
    account      VARCHAR(10)    NOT NULL,  -- ESCROW | PLATFORM | ARTIST
    source       VARCHAR(20)    NOT NULL,  -- MARKETPLACE | SHOP | COURSE | PAYOUT | MANUAL
    source_ref   VARCHAR(60)    NOT NULL,
    payee_type   VARCHAR(6)     NULL,      -- USER (student artist) | ADMIN (studio artist); NULL for platform rows
    payee_id     BIGINT         NULL,
    payee_name   VARCHAR(255)   NULL,
    amount       DECIMAL(12, 2) NOT NULL,  -- signed, in INR
    available_at DATETIME       NULL,      -- ARTIST rows: when it may be paid out
    payout_id    BIGINT         NULL,
    memo         VARCHAR(300)   NULL,
    created_by   BIGINT         NULL,
    created_at   DATETIME       NOT NULL,
    UNIQUE INDEX ledger_entries_idem_unique (idem_key),
    INDEX ledger_entries_payee_index (payee_type, payee_id, account, available_at),
    INDEX ledger_entries_source_index (source, source_ref),
    INDEX ledger_entries_time_index (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payout_batches (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    period_end DATETIME       NOT NULL,
    status     VARCHAR(8)     NOT NULL DEFAULT 'DRAFT',  -- DRAFT | PAID | VOID
    total      DECIMAL(12, 2) NOT NULL DEFAULT 0,
    line_count INT            NOT NULL DEFAULT 0,
    reference  VARCHAR(200)   NULL,
    paid_by    BIGINT         NULL,
    paid_at    DATETIME       NULL,
    created_at DATETIME       NOT NULL,
    INDEX payout_batches_status_index (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payout_lines (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    batch_id   BIGINT         NOT NULL,
    payee_type VARCHAR(6)     NOT NULL,
    payee_id   BIGINT         NOT NULL,
    payee_name VARCHAR(255)   NULL,
    payee_email VARCHAR(255)  NULL,
    amount     DECIMAL(12, 2) NOT NULL,
    entries    INT            NOT NULL DEFAULT 0,
    UNIQUE INDEX payout_lines_unique (batch_id, payee_type, payee_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ledger_disputes (
    id                 BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    source             VARCHAR(20)    NOT NULL,
    source_ref         VARCHAR(60)    NOT NULL,
    payment_ref        VARCHAR(255)   NULL,
    gateway_dispute_id VARCHAR(255)   NULL,
    reason             VARCHAR(300)   NULL,
    amount             DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status             VARCHAR(6)     NOT NULL DEFAULT 'OPEN',  -- OPEN | WON | LOST
    opened_by          BIGINT         NULL,                     -- NULL = gateway webhook
    closed_by          BIGINT         NULL,
    note               VARCHAR(500)   NULL,
    created_at         DATETIME       NOT NULL,
    closed_at          DATETIME       NULL,
    UNIQUE INDEX ledger_disputes_gateway_unique (gateway_dispute_id),
    INDEX ledger_disputes_source_index (source, source_ref, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
