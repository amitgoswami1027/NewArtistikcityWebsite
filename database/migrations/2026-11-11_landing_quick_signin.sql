-- Landing pages: quick sign-in inside the page's checkout. Six-digit sign-in codes sent by e-mail (auth_email_codes),
-- next to the existing SMS codes (auth_otps), Google one-tap sign-in and passkeys. Codes are stored as an HMAC only.
-- Additive and safe to run twice: a new table, nothing else is changed.
CREATE TABLE IF NOT EXISTS auth_email_codes (
    id            BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    purpose       VARCHAR(20) NOT NULL DEFAULT 'login',
    code_hash     CHAR(64) NOT NULL,
    attempts      INT NOT NULL DEFAULT 0,
    expires_at    DATETIME NOT NULL,
    consumed_at   DATETIME NULL,
    ip_address    VARCHAR(45) NULL,
    created_at    DATETIME NOT NULL,
    INDEX auth_email_codes_email_index (email, created_at),
    INDEX auth_email_codes_ip_index (ip_address, created_at)
);
