-- 2026-11-05: messages to the students of a course (Admin > course > Message students), and cancelling or moving a
-- live course. Each message is shown in the students' studio and, when chosen, e-mailed; one delivery row per
-- student records whether the e-mail went out and when they read it. Safe to run twice.

SET @has := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'courses' AND column_name = 'cancelled_at');
SET @sql := IF(@has = 0, 'ALTER TABLE courses ADD COLUMN cancelled_at DATETIME NULL, ADD COLUMN cancel_reason VARCHAR(500) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS course_messages (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    course_id  BIGINT       NOT NULL,
    admin_id   BIGINT       NULL,
    kind       VARCHAR(12)  NOT NULL DEFAULT 'MESSAGE',  -- MESSAGE | CANCELLED | RESCHEDULED
    subject    VARCHAR(200) NOT NULL,
    body       LONGTEXT     NOT NULL,                    -- plain text
    send_email TINYINT(1)   NOT NULL DEFAULT 1,
    recipients INT          NOT NULL DEFAULT 0,
    emailed    INT          NOT NULL DEFAULT 0,
    failed     INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL,
    INDEX course_messages_course_index (course_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_message_deliveries (
    id           BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    message_id   BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    email        VARCHAR(255) NULL,
    email_status VARCHAR(8)   NOT NULL DEFAULT 'SKIPPED', -- SENT | FAILED | SKIPPED (studio only)
    error        VARCHAR(300) NULL,
    read_at      DATETIME     NULL,
    created_at   DATETIME     NOT NULL,
    UNIQUE INDEX course_message_deliveries_unique (message_id, user_id),
    INDEX course_message_deliveries_user_index (user_id, read_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
