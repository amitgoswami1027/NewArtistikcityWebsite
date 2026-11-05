-- 2026-11-05: Security audit log (Admin > Setup > Security log).
-- One append-only stream of every important action: CREATE / UPDATE / DELETE by staff, AUTH events (sign-ins, failures,
-- revoked sessions), EXPORT of data and DENIED requests (401/403 on the admin console). Each row carries the SHA-256 of
-- the previous row (prev_hash) and of itself (row_hash), so a changed or removed row breaks the chain and
-- "Verify integrity" finds it. The app never updates or deletes these rows. Safe to run twice.

CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    occurred_at   DATETIME(3)   NOT NULL,
    actor_type    VARCHAR(20)   NOT NULL,             -- admin | student | system | anonymous
    actor_id      BIGINT        NULL,
    actor_email   VARCHAR(255)  NULL,
    actor_role    VARCHAR(30)   NULL,                 -- admin | teacher | moderator | student
    action_type   VARCHAR(10)   NOT NULL,             -- CREATE | UPDATE | DELETE | AUTH | EXPORT | DENIED
    action        VARCHAR(120)  NOT NULL,             -- what happened, e.g. "course updated", "admin_login"
    resource_type VARCHAR(50)   NULL,
    resource_id   VARCHAR(64)   NULL,
    summary       VARCHAR(1000) NULL,
    before_json   LONGTEXT      NULL,                 -- text, not JSON: MySQL would reorder keys and break the hash
    after_json    LONGTEXT      NULL,
    ip            VARCHAR(45)   NULL,
    country       VARCHAR(8)    NULL,                 -- CloudFront-Viewer-Country
    city          VARCHAR(100)  NULL,                 -- CloudFront-Viewer-City
    user_agent    VARCHAR(500)  NULL,
    http_method   VARCHAR(10)   NULL,
    path          VARCHAR(500)  NULL,
    outcome       VARCHAR(10)   NOT NULL,             -- OK | FAIL | DENIED
    prev_hash     CHAR(64)      NOT NULL,
    row_hash      CHAR(64)      NOT NULL,
    INDEX audit_logs_time_index (occurred_at),
    INDEX audit_logs_actor_index (actor_id, occurred_at),
    INDEX audit_logs_type_index (action_type, occurred_at),
    INDEX audit_logs_resource_index (resource_type, resource_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- the end of the chain: locked (SELECT ... FOR UPDATE) while a row is appended, so rows join the chain one at a time
CREATE TABLE IF NOT EXISTS audit_chain_head (
    id         INT          NOT NULL PRIMARY KEY,
    last_id    BIGINT       NOT NULL,
    last_hash  CHAR(64)     NOT NULL,
    updated_at DATETIME(3)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT IGNORE INTO audit_chain_head (id, last_id, last_hash, updated_at)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000', '2026-11-06 00:00:00.000');
