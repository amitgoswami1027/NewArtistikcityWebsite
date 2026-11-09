-- A/B tests for landing pages: the live version (A) against a variant (B, a saved version of the draft) with a traffic
-- split. Visitors are assigned by their anonymous visitor hash (sticky), events record the test and variant, and
-- promoting the winner publishes it as the one live version. Additive and safe to run twice; without it the A/B card
-- says this file is missing and pages work as before.
DROP PROCEDURE IF EXISTS ac_add_col;
DELIMITER //
CREATE PROCEDURE ac_add_col(IN t VARCHAR(64), IN c VARCHAR(64), IN def VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) = 0 THEN
        SET @s := CONCAT('ALTER TABLE ', t, ' ADD COLUMN ', c, ' ', def);
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END //
DELIMITER ;
CALL ac_add_col('landing_events', 'ab_test_id', 'BIGINT NULL');
CALL ac_add_col('landing_events', 'ab_variant', 'CHAR(1) NULL');
DROP PROCEDURE ac_add_col;
CREATE TABLE IF NOT EXISTS landing_ab_tests (
    id                 BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    landing_page_id    BIGINT       NOT NULL,
    name               VARCHAR(120) NOT NULL,
    status             VARCHAR(10)  NOT NULL,
    goal               VARCHAR(12)  NOT NULL,
    split_b            INT          NOT NULL,
    control_version_id BIGINT       NOT NULL,
    variant_version_id BIGINT       NOT NULL,
    winner             CHAR(1)      NULL,
    started_by         BIGINT       NULL,
    started_at         DATETIME     NOT NULL,
    ended_by           BIGINT       NULL,
    ended_at           DATETIME     NULL,
    created_at         DATETIME     NOT NULL,
    INDEX landing_ab_tests_page_index (landing_page_id, status),
    CONSTRAINT landing_ab_tests_page_foreign FOREIGN KEY (landing_page_id) REFERENCES landing_pages (id) ON DELETE CASCADE
);
SET @ix := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'landing_events' AND index_name = 'landing_events_ab_index');
SET @s := IF(@ix = 0, 'CREATE INDEX landing_events_ab_index ON landing_events (ab_test_id, ab_variant, event)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
