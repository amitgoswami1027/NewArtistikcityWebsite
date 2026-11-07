-- Cart, custom print sizes and "Make an offer" (shop/OfferService). Safe to run twice.
-- paintings.offers_enabled: NULL = the default (studio originals take offers, student listings don't until the student
-- switches it on); offer_min_pct: lowest offer as % of the price, NULL = the global setting (Admin > Marketplace > Offers).
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
CALL ac_add_col('paintings', 'offers_enabled', 'TINYINT(1) NULL');
CALL ac_add_col('paintings', 'offer_min_pct', 'INT NULL');
DROP PROCEDURE ac_add_col;

CREATE TABLE IF NOT EXISTS painting_offers (
    id             BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    painting_id    BIGINT         NOT NULL,
    user_id        BIGINT         NOT NULL,
    buyer_name     VARCHAR(190)   NOT NULL,
    buyer_email    VARCHAR(190)   NOT NULL,
    list_price_inr DECIMAL(12, 2) NOT NULL,
    amount_inr     DECIMAL(12, 2) NOT NULL,
    counter_inr    DECIMAL(12, 2) NULL,
    agreed_inr     DECIMAL(12, 2) NULL,
    status         VARCHAR(20)    NOT NULL CHECK (status IN ('OPEN', 'COUNTERED', 'ACCEPTED', 'DECLINED', 'WITHDRAWN', 'EXPIRED', 'CLOSED', 'PAID')),
    round_no       INT            NOT NULL DEFAULT 1,
    message        VARCHAR(500)   NULL,
    expires_at     DATETIME       NULL,
    pay_by         DATETIME       NULL,
    reminded_at    DATETIME       NULL,
    closed_reason  VARCHAR(255)   NULL,
    order_id       VARCHAR(40)    NULL,
    buyer_unread   TINYINT(1)     NOT NULL DEFAULT 0,
    seller_unread  TINYINT(1)     NOT NULL DEFAULT 1,
    created_at     DATETIME       NOT NULL,
    updated_at     DATETIME       NOT NULL,
    INDEX painting_offers_painting_status_index (painting_id, status),
    INDEX painting_offers_user_status_index (user_id, status),
    CONSTRAINT painting_offers_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE CASCADE,
    CONSTRAINT painting_offers_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS painting_offer_events (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    offer_id    BIGINT         NOT NULL,
    actor       VARCHAR(10)    NOT NULL CHECK (actor IN ('BUYER', 'SELLER', 'STAFF', 'SYSTEM')),
    actor_name  VARCHAR(190)   NULL,
    kind        VARCHAR(20)    NOT NULL,
    amount_inr  DECIMAL(12, 2) NULL,
    note        VARCHAR(500)   NULL,
    created_at  DATETIME       NOT NULL,
    INDEX painting_offer_events_offer_index (offer_id),
    CONSTRAINT painting_offer_events_offer_id_foreign FOREIGN KEY (offer_id) REFERENCES painting_offers (id) ON DELETE CASCADE
);
