-- Two-step verification (MFA) for staff accounts: an authenticator app (TOTP) plus single-use recovery codes.
-- admins.mfa_secret = the TOTP secret, encrypted by the app (AES-256-GCM, Configuration Panel key); mfa_enabled_at = on;
-- mfa_last_step = the last accepted 30-second step (a code works once); mfa_changed_at = signs "trusted browser" cookies.
-- admin_mfa_recovery_codes keeps SHA-256 hashes only. The policy (required for all staff) is the setting
-- system/admin_mfa_required. Additive and safe to run twice; without it MFA is simply not offered.
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
CALL ac_add_col('admins', 'mfa_secret', 'VARCHAR(255) NULL');
CALL ac_add_col('admins', 'mfa_enabled_at', 'DATETIME NULL');
CALL ac_add_col('admins', 'mfa_last_step', 'BIGINT NULL');
CALL ac_add_col('admins', 'mfa_changed_at', 'DATETIME NULL');
DROP PROCEDURE ac_add_col;
CREATE TABLE IF NOT EXISTS admin_mfa_recovery_codes (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    admin_id    BIGINT NOT NULL,
    code_hash   CHAR(64) NOT NULL,
    used_at     DATETIME NULL,
    created_at  DATETIME NOT NULL,
    INDEX admin_mfa_recovery_admin_index (admin_id, used_at),
    CONSTRAINT admin_mfa_recovery_admin_foreign FOREIGN KEY (admin_id) REFERENCES admins (id) ON DELETE CASCADE
);
