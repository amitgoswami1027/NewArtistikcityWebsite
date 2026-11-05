-- 2026-11-05: seat caps for live courses and workshops, a waitlist, and priority access links.
--   courses.seat_limit       NULL = unlimited (recorded courses); a number = at most that many students
--   seat_holds               a seat kept for 15 minutes while a student pays (HELD), then PAID / EXPIRED / RELEASED
--   course_waitlist          students waiting for a seat; the first one is invited when a seat frees up
--   access_grants            priority access links (a seat kept for the holder until it expires): waitlist invitations
--                            and links staff send by hand. Only the SHA-256 of the token is stored.
-- Safe to run twice.

SET @has := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'courses' AND column_name = 'seat_limit');
SET @sql := IF(@has = 0, 'ALTER TABLE courses ADD COLUMN seat_limit INT NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS seat_holds (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    course_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    order_id   BIGINT      NULL,
    grant_id   BIGINT      NULL,
    status     VARCHAR(10) NOT NULL DEFAULT 'HELD',     -- HELD | PAID | EXPIRED | RELEASED
    expires_at DATETIME    NOT NULL,
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    UNIQUE INDEX seat_holds_course_user_unique (course_id, user_id),
    INDEX seat_holds_status_index (course_id, status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_waitlist (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    course_id  BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    email      VARCHAR(255) NOT NULL,
    status     VARCHAR(10)  NOT NULL DEFAULT 'WAITING', -- WAITING | INVITED | JOINED | EXPIRED | LEFT
    grant_id   BIGINT       NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    UNIQUE INDEX course_waitlist_course_user_unique (course_id, user_id),
    INDEX course_waitlist_status_index (course_id, status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS access_grants (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    course_id  BIGINT       NOT NULL,
    user_id    BIGINT       NULL,
    email      VARCHAR(255) NOT NULL,
    kind       VARCHAR(10)  NOT NULL,                   -- WAITLIST | PRIORITY
    token_hash CHAR(64)     NOT NULL,
    holds_seat TINYINT(1)   NOT NULL DEFAULT 1,
    expires_at DATETIME     NOT NULL,
    used_at    DATETIME     NULL,
    revoked_at DATETIME     NULL,
    created_by BIGINT       NULL,                       -- admins.id (NULL = the waitlist)
    note       VARCHAR(255) NULL,
    created_at DATETIME     NOT NULL,
    UNIQUE INDEX access_grants_token_unique (token_hash),
    INDEX access_grants_course_index (course_id, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
