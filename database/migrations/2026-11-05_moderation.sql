-- 2026-11-05: account restrictions and content reports (Admin > Community > Reports & bans).
--   account_restrictions  BAN (no sign-in; every session ended), POSTING (no task replies, submissions or messages to
--                         artists), SELLING (no new work for the marketplace). until NULL = until lifted by staff.
--   content_reports       the Report button (reason code + note), one per reporter and item; staff hide the item or
--                         dismiss the report, one at a time or in bulk.
-- Safe to run twice.

CREATE TABLE IF NOT EXISTS account_restrictions (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    kind       VARCHAR(10)  NOT NULL,                 -- BAN | POSTING | SELLING
    reason     VARCHAR(500) NOT NULL,
    until      DATETIME     NULL,
    created_by BIGINT       NULL,
    created_at DATETIME     NOT NULL,
    lifted_at  DATETIME     NULL,
    lifted_by  BIGINT       NULL,
    INDEX account_restrictions_user_index (user_id, kind, lifted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS content_reports (
    id               BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    target_type      VARCHAR(12)  NOT NULL,           -- PAINTING | ARTWORK | REVIEW | SUBMISSION | USER
    target_id        BIGINT       NOT NULL,
    reason_code      VARCHAR(20)  NOT NULL,           -- SPAM | OFFENSIVE | COPYRIGHT | MISLEADING | PERSONAL_INFO | OTHER
    detail           VARCHAR(1000) NULL,
    reporter_user_id BIGINT       NULL,
    reporter_ip      VARCHAR(45)  NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'OPEN',  -- OPEN | ACTIONED | DISMISSED
    action           VARCHAR(10)  NULL,               -- HIDDEN | NONE
    decided_by       BIGINT       NULL,
    decided_at       DATETIME     NULL,
    note             VARCHAR(500) NULL,
    created_at       DATETIME     NOT NULL,
    UNIQUE INDEX content_reports_once_unique (target_type, target_id, reporter_user_id),
    INDEX content_reports_status_index (status, target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
