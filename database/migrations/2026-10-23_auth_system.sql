-- =====================================================================
--  ArtistikCity - unified authentication schema (MySQL 8)
--  Safe to run more than once on an existing database:
--    mysql -h 127.0.0.1 -u artistik -p artistikcity < database/migrations/2026-10-23_auth_system.sql
--  (setup-and-run.ps1 applies pending migrations automatically.)
--
--  * users: password -> password_hash (nullable), phone -> phone_number (E.164, unique),
--    email nullable (phone-only accounts), + is_verified, phone_verified_at, webauthn_user_handle, last_login_at
--  * users.provider / provider_id move to linked_accounts (one row per Google/Facebook identity)
--  * new: auth_sessions, auth_refresh_tokens, auth_otps, auth_tokens, webauthn_credentials, auth_events
--  * password_resets is replaced by auth_tokens (purpose = 'password_reset')
-- =====================================================================

-- helper: run a statement only when a condition on information_schema holds
DROP PROCEDURE IF EXISTS ac_exec_if;
DELIMITER //
CREATE PROCEDURE ac_exec_if(IN cond INT, IN stmt TEXT)
BEGIN
    IF cond > 0 THEN
        SET @ac_sql = stmt;
        PREPARE ac_st FROM @ac_sql;
        EXECUTE ac_st;
        DEALLOCATE PREPARE ac_st;
    END IF;
END //
DELIMITER ;

-- ---- users: renamed / relaxed columns ---------------------------------
CALL ac_exec_if((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'password'),
    'ALTER TABLE users RENAME COLUMN password TO password_hash');
ALTER TABLE users MODIFY password_hash VARCHAR(255) NULL;

CALL ac_exec_if((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'phone'),
    'ALTER TABLE users RENAME COLUMN phone TO phone_number');
ALTER TABLE users MODIFY email VARCHAR(255) NULL;
ALTER TABLE users MODIFY profile_photo VARCHAR(1000) NULL;

-- ---- users: new columns --------------------------------------------------
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'phone_verified_at'),
    'ALTER TABLE users ADD COLUMN phone_verified_at DATETIME NULL AFTER phone_number');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'is_verified'),
    'ALTER TABLE users ADD COLUMN is_verified TINYINT(1) NOT NULL DEFAULT 0 AFTER password_hash');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'webauthn_user_handle'),
    'ALTER TABLE users ADD COLUMN webauthn_user_handle VARCHAR(100) NULL AFTER is_verified');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'last_login_at'),
    'ALTER TABLE users ADD COLUMN last_login_at DATETIME NULL AFTER webauthn_user_handle');

-- existing phone numbers were typed into profile forms and never verified: keep them, but normalise
-- Indian 10-digit numbers to E.164 and drop duplicates so the unique index can be created
ALTER TABLE users MODIFY phone_number VARCHAR(50) NULL;
UPDATE users SET phone_number = NULLIF(REGEXP_REPLACE(phone_number, '[^0-9+]', ''), '') WHERE phone_number IS NOT NULL;
UPDATE users SET phone_number = CONCAT('+91', phone_number) WHERE phone_number REGEXP '^[6-9][0-9]{9}$';
UPDATE users SET phone_number = CONCAT('+', phone_number) WHERE phone_number REGEXP '^91[6-9][0-9]{9}$';
UPDATE users SET phone_number = NULL WHERE phone_number IS NOT NULL AND phone_number NOT REGEXP '^\\+[1-9][0-9]{7,14}$';
UPDATE users u JOIN (SELECT phone_number, MIN(id) AS keep_id FROM users WHERE phone_number IS NOT NULL GROUP BY phone_number HAVING COUNT(*) > 1) d
    ON u.phone_number = d.phone_number AND u.id <> d.keep_id
    SET u.phone_number = NULL;
ALTER TABLE users MODIFY phone_number VARCHAR(20) NULL;
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'users_phone_number_unique'),
    'ALTER TABLE users ADD CONSTRAINT users_phone_number_unique UNIQUE (phone_number)');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'users_webauthn_user_handle_unique'),
    'ALTER TABLE users ADD CONSTRAINT users_webauthn_user_handle_unique UNIQUE (webauthn_user_handle)');

UPDATE users SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL;
UPDATE users SET is_verified = 1 WHERE email_verified_at IS NOT NULL OR phone_verified_at IS NOT NULL;

-- ---- linked_accounts -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS linked_accounts (
    id                BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    provider_name     VARCHAR(20) NOT NULL CHECK (provider_name IN ('google', 'facebook')),
    provider_user_id  VARCHAR(191) NOT NULL,
    email             VARCHAR(255) NULL,
    email_verified    TINYINT(1) NOT NULL DEFAULT 0,
    display_name      VARCHAR(255) NULL,
    first_name        VARCHAR(100) NULL,
    last_name         VARCHAR(100) NULL,
    avatar_url        VARCHAR(1000) NULL,
    profile_data      JSON NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at     DATETIME NULL,
    CONSTRAINT linked_accounts_provider_unique UNIQUE (provider_name, provider_user_id),
    CONSTRAINT linked_accounts_user_provider_unique UNIQUE (user_id, provider_name),
    CONSTRAINT linked_accounts_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- move the old single provider column pair into linked_accounts, then drop it
CALL ac_exec_if((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'provider_id'),
    'INSERT IGNORE INTO linked_accounts (user_id, provider_name, provider_user_id, email, email_verified, display_name, created_at, updated_at)
     SELECT id, LOWER(provider), provider_id, email, 0, name, COALESCE(created_at, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP
     FROM users WHERE provider_id IS NOT NULL AND LOWER(provider) IN (''google'', ''facebook'')');
CALL ac_exec_if((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'provider_id'),
    'ALTER TABLE users DROP COLUMN provider_id');
CALL ac_exec_if((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'provider'),
    'ALTER TABLE users DROP COLUMN provider');
-- placeholder e-mails the old social login invented for accounts without one
UPDATE users SET email = NULL WHERE email LIKE '%@users.noreply';

-- ---- sessions ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_sessions (
    id                        BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id                   BIGINT NOT NULL,
    session_token_hash        CHAR(64) NOT NULL,
    session_expires_at        DATETIME NOT NULL,
    previous_token_hash       CHAR(64) NULL,
    previous_valid_until      DATETIME NULL,
    rotated_at                DATETIME NOT NULL,
    auth_method               VARCHAR(20) NOT NULL CHECK (auth_method IN ('password', 'google', 'facebook', 'otp', 'magic_link', 'passkey', 'signup')),
    remember                  TINYINT(1) NOT NULL DEFAULT 0,
    ip_address                VARCHAR(45) NULL,
    user_agent                VARCHAR(500) NULL,
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at                DATETIME NOT NULL,
    revoked_at                DATETIME NULL,
    revoke_reason             VARCHAR(50) NULL,
    CONSTRAINT auth_sessions_token_unique UNIQUE (session_token_hash),
    CONSTRAINT auth_sessions_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX auth_sessions_user_index (user_id, revoked_at),
    INDEX auth_sessions_previous_index (previous_token_hash)
);

CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id           BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    session_id   BIGINT NOT NULL,
    token_hash   CHAR(64) NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   DATETIME NOT NULL,
    used_at      DATETIME NULL,
    CONSTRAINT auth_refresh_tokens_hash_unique UNIQUE (token_hash),
    CONSTRAINT auth_refresh_tokens_session_foreign FOREIGN KEY (session_id) REFERENCES auth_sessions (id) ON DELETE CASCADE
);

-- ---- one-time codes and tokens ---------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_otps (
    id            BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    phone_number  VARCHAR(20) NOT NULL,
    purpose       VARCHAR(20) NOT NULL DEFAULT 'login' CHECK (purpose IN ('login', 'verify_phone')),
    user_id       BIGINT NULL,
    code_hash     CHAR(64) NOT NULL,
    attempts      INT NOT NULL DEFAULT 0,
    expires_at    DATETIME NOT NULL,
    consumed_at   DATETIME NULL,
    ip_address    VARCHAR(45) NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX auth_otps_phone_index (phone_number, created_at),
    INDEX auth_otps_ip_index (ip_address, created_at)
);

CREATE TABLE IF NOT EXISTS auth_tokens (
    id           BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    purpose      VARCHAR(30) NOT NULL CHECK (purpose IN ('magic_link', 'verify_email', 'password_reset')),
    user_id      BIGINT NULL,
    email        VARCHAR(255) NOT NULL,
    token_hash   CHAR(64) NOT NULL,
    expires_at   DATETIME NOT NULL,
    consumed_at  DATETIME NULL,
    ip_address   VARCHAR(45) NULL,
    meta         JSON NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT auth_tokens_hash_unique UNIQUE (token_hash),
    CONSTRAINT auth_tokens_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX auth_tokens_email_index (email, purpose, created_at)
);

CREATE TABLE IF NOT EXISTS webauthn_credentials (
    id               BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    credential_id    VARCHAR(512) NOT NULL,
    public_key_cose  BLOB NOT NULL,
    algorithm        INT NOT NULL,
    sign_count       BIGINT NOT NULL DEFAULT 0,
    transports       VARCHAR(100) NULL,
    aaguid           CHAR(36) NULL,
    backup_eligible  TINYINT(1) NOT NULL DEFAULT 0,
    backed_up        TINYINT(1) NOT NULL DEFAULT 0,
    name             VARCHAR(100) NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at     DATETIME NULL,
    CONSTRAINT webauthn_credentials_id_unique UNIQUE (credential_id),
    CONSTRAINT webauthn_credentials_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auth_events (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id     BIGINT NULL,
    event       VARCHAR(50) NOT NULL,
    method      VARCHAR(20) NULL,
    success     TINYINT(1) NOT NULL DEFAULT 1,
    ip_address  VARCHAR(45) NULL,
    user_agent  VARCHAR(500) NULL,
    detail      VARCHAR(500) NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX auth_events_user_index (user_id, created_at),
    INDEX auth_events_event_index (event, created_at)
);

-- password_resets is superseded by auth_tokens (open reset links simply expire)
DROP TABLE IF EXISTS password_resets;

DROP PROCEDURE IF EXISTS ac_exec_if;
