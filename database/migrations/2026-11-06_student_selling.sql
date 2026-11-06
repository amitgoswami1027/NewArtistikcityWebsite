-- Student artists sell from My studio (Sell your art, step 7): where their earnings are paid. The details are encrypted
-- by the app (details_enc; key CONFIG_ENCRYPTION_KEY, else OTP_SECRET); masked is a safe summary such as "UPI ••••@okhdfc".
-- Safe to run twice.
CREATE TABLE IF NOT EXISTS student_payout_details (
    user_id      BIGINT       NOT NULL PRIMARY KEY,
    method       VARCHAR(10)  NOT NULL,
    holder_name  VARCHAR(100) NOT NULL,
    details_enc  TEXT         NOT NULL,
    masked       VARCHAR(160) NOT NULL,
    created_at   DATETIME     NULL,
    updated_at   DATETIME     NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
