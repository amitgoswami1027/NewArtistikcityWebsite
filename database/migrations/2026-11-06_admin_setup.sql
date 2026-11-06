-- Admin > System parameters: values saved in the Configuration panel (they win over environment variables and the
-- defaults in config/artistikcity.yml). Secrets are AES-GCM encrypted by the app (value_enc); the key is not stored here.
-- Safe to run twice.
CREATE TABLE IF NOT EXISTS config_overrides (
    config_key  VARCHAR(190) NOT NULL PRIMARY KEY,
    value_text  TEXT         NULL,
    value_enc   TEXT         NULL,
    is_secret   TINYINT      NOT NULL DEFAULT 0,
    updated_by  BIGINT       NULL,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL
);

-- Studio Stories CMS: workflow (0 draft, 1 published, 2 archived, 3 in review, 4 scheduled), author, scheduled publishing,
-- views and read time.
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
CALL ac_add_col('posts', 'author_id', 'BIGINT NULL');
CALL ac_add_col('posts', 'publish_at', 'DATETIME NULL');
CALL ac_add_col('posts', 'published_at', 'DATETIME NULL');
CALL ac_add_col('posts', 'archived_at', 'DATETIME NULL');
CALL ac_add_col('posts', 'views', 'INT NOT NULL DEFAULT 0');
CALL ac_add_col('posts', 'read_minutes', 'INT NOT NULL DEFAULT 0');
DROP PROCEDURE ac_add_col;
UPDATE posts SET author_id = posted_by WHERE author_id IS NULL;
UPDATE posts SET published_at = created_at WHERE status = 1 AND published_at IS NULL;

-- one row per post and day: the dashboard's views over time
CREATE TABLE IF NOT EXISTS post_views (
    post_id  BIGINT NOT NULL,
    day      DATE   NOT NULL,
    views    INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, day)
);
DROP PROCEDURE IF EXISTS ac_add_index;
DELIMITER //
CREATE PROCEDURE ac_add_index(IN t VARCHAR(64), IN i VARCHAR(64), IN cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = t AND index_name = i) = 0 THEN
        SET @s := CONCAT('CREATE INDEX ', i, ' ON ', t, ' (', cols, ')');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END //
DELIMITER ;
CALL ac_add_index('post_views', 'post_views_day_index', 'day');
DROP PROCEDURE ac_add_index;
