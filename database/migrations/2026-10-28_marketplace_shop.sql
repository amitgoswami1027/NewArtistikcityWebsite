-- =====================================================================
--  Marketplace shop: prints, digital downloads, a persistent cart, coupons and artist messages.
--  Safe to run more than once.
--   * paintings: which extra formats a listing sells (shop_formats), the canvas edition size and count,
--     the private high-resolution file for digital downloads, and optional artist contact links
--   * shop_price_options     : the global price list (canvas / poster sizes, digital licence, frames, shipping)
--   * shop_carts / shop_cart_items : one cart per signed-in collector ("U:<id>") or guest session ("S:<token>")
--   * shop_coupons           : artist coupon codes (percent or flat, optional artwork / artist limit)
--   * shop_orders / shop_order_items : multi-item marketplace orders (originals, prints, downloads)
--   * artist_threads / artist_thread_messages : "Meet the artist" questions and in-app chat
-- =====================================================================
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

SET @ac_new_formats = (SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'paintings' AND column_name = 'shop_formats');
CALL ac_exec_if(@ac_new_formats, 'ALTER TABLE paintings
    ADD COLUMN shop_formats     VARCHAR(60)  NULL AFTER is_featured,
    ADD COLUMN edition_size     INT          NOT NULL DEFAULT 50 AFTER shop_formats,
    ADD COLUMN editions_sold    INT          NOT NULL DEFAULT 0 AFTER edition_size,
    ADD COLUMN hires_path       VARCHAR(255) NULL AFTER editions_sold,
    ADD COLUMN hires_name       VARCHAR(255) NULL AFTER hires_path,
    ADD COLUMN artist_whatsapp  VARCHAR(30)  NULL AFTER hires_name,
    ADD COLUMN artist_instagram VARCHAR(120) NULL AFTER artist_whatsapp,
    ADD COLUMN artist_email     VARCHAR(190) NULL AFTER artist_instagram');
-- existing listings start out selling canvas and poster prints (digital needs a high-resolution file first)
CALL ac_exec_if(@ac_new_formats, 'UPDATE paintings SET shop_formats = ''CANVAS,POSTER''');

CREATE TABLE IF NOT EXISTS shop_price_options (
    id           BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    kind         VARCHAR(20)    NOT NULL CHECK (kind IN ('CANVAS', 'POSTER', 'DIGITAL', 'FRAME', 'SHIPPING')),
    code         VARCHAR(40)    NOT NULL,
    label        VARCHAR(120)   NOT NULL,
    long_side_in DECIMAL(6, 2)  NULL,
    price_inr    DECIMAL(10, 2) NOT NULL CHECK (price_inr >= 0),
    extra_inr    DECIMAL(10, 2) NOT NULL DEFAULT 0 CHECK (extra_inr >= 0),
    sort_order   INT            NOT NULL DEFAULT 0,
    is_active    TINYINT(1)     NOT NULL DEFAULT 1,
    updated_at   DATETIME       NULL,
    CONSTRAINT shop_price_options_kind_code_unique UNIQUE (kind, code)
);

INSERT IGNORE INTO shop_price_options (kind, code, label, long_side_in, price_inr, extra_inr, sort_order, is_active, updated_at) VALUES
('CANVAS', 'S', 'Small', 12, 2499, 600, 1, 1, NOW()),
('CANVAS', 'M', 'Medium', 24, 4999, 1100, 2, 1, NOW()),
('CANVAS', 'L', 'Large', 36, 8999, 1600, 3, 1, NOW()),
('CANVAS', 'XL', 'Statement', 48, 13999, 2200, 4, 1, NOW()),
('POSTER', 'S', 'Small', 12, 799, 0, 1, 1, NOW()),
('POSTER', 'M', 'Medium', 18, 1199, 0, 2, 1, NOW()),
('POSTER', 'L', 'Large', 24, 1699, 0, 3, 1, NOW()),
('POSTER', 'XL', 'Extra large', 36, 2699, 0, 4, 1, NOW()),
('DIGITAL', 'PERSONAL', 'High-resolution file, personal-use licence', NULL, 999, 0, 1, 1, NOW()),
('FRAME', 'BLACK_WOOD', 'Black wood frame', NULL, 22, 0, 1, 1, NOW()),
('FRAME', 'OAK', 'Natural oak frame', NULL, 28, 0, 2, 1, NOW()),
('FRAME', 'WHITE_FLOAT', 'White canvas float frame', NULL, 30, 0, 3, 1, NOW()),
('SHIPPING', 'PRINTS_INDIA', 'Prints shipping within India (per order)', NULL, 149, 0, 1, 1, NOW()),
('SHIPPING', 'PRINTS_INTL', 'Prints shipping worldwide (per order)', NULL, 1999, 0, 2, 1, NOW());

CREATE TABLE IF NOT EXISTS shop_carts (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    owner_key   VARCHAR(80)  NOT NULL,
    user_id     BIGINT       NULL,
    coupon_code VARCHAR(40)  NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    CONSTRAINT shop_carts_owner_unique UNIQUE (owner_key),
    CONSTRAINT shop_carts_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS shop_cart_items (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    cart_id     BIGINT      NOT NULL,
    painting_id BIGINT      NOT NULL,
    format      VARCHAR(20) NOT NULL CHECK (format IN ('ORIGINAL', 'CANVAS', 'POSTER', 'DIGITAL')),
    size_code   VARCHAR(20) NOT NULL DEFAULT '',
    finish      VARCHAR(20) NOT NULL DEFAULT '',
    frame       VARCHAR(20) NOT NULL DEFAULT 'NONE',
    quantity    INT         NOT NULL DEFAULT 1 CHECK (quantity BETWEEN 1 AND 10),
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    CONSTRAINT shop_cart_items_line_unique UNIQUE (cart_id, painting_id, format, size_code, finish, frame),
    CONSTRAINT shop_cart_items_cart_id_foreign FOREIGN KEY (cart_id) REFERENCES shop_carts (id) ON DELETE CASCADE,
    CONSTRAINT shop_cart_items_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS shop_coupons (
    id             BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    code           VARCHAR(40)    NOT NULL,
    description    VARCHAR(255)   NULL,
    discount_type  VARCHAR(10)    NOT NULL CHECK (discount_type IN ('PERCENT', 'FLAT')),
    discount_value DECIMAL(10, 2) NOT NULL CHECK (discount_value > 0),
    painting_id    BIGINT         NULL,
    artist_name    VARCHAR(255)   NULL,
    min_subtotal   DECIMAL(10, 2) NULL,
    max_uses       INT            NULL,
    used_count     INT            NOT NULL DEFAULT 0,
    starts_at      DATETIME       NULL,
    expires_at     DATETIME       NULL,
    is_active      TINYINT(1)     NOT NULL DEFAULT 1,
    created_at     DATETIME       NOT NULL,
    updated_at     DATETIME       NOT NULL,
    CONSTRAINT shop_coupons_code_unique UNIQUE (code),
    CONSTRAINT shop_coupons_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE CASCADE
);

INSERT IGNORE INTO shop_coupons (code, description, discount_type, discount_value, painting_id, artist_name, min_subtotal, max_uses, used_count,
    starts_at, expires_at, is_active, created_at, updated_at) VALUES
('WELCOME10', '10% off your first artwork', 'PERCENT', 10, NULL, NULL, NULL, NULL, 0, NULL, NULL, 1, NOW(), NOW());

CREATE TABLE IF NOT EXISTS shop_orders (
    order_id        VARCHAR(50)    NOT NULL PRIMARY KEY,
    user_id         BIGINT         NULL,
    owner_key       VARCHAR(80)    NOT NULL,
    buyer_name      VARCHAR(255)   NOT NULL,
    buyer_email     VARCHAR(255)   NOT NULL,
    buyer_phone     VARCHAR(50)    NULL,
    address_line1   VARCHAR(255)   NULL,
    address_line2   VARCHAR(255)   NULL,
    city            VARCHAR(100)   NULL,
    state           VARCHAR(100)   NULL,
    postal_code     VARCHAR(20)    NULL,
    country         VARCHAR(100)   NOT NULL,
    needs_shipping  TINYINT(1)     NOT NULL DEFAULT 1,
    gateway         VARCHAR(20)    NOT NULL CHECK (gateway IN ('RAZORPAY', 'PAYPAL', 'TEST')),
    currency        VARCHAR(3)     NOT NULL,
    amount          DECIMAL(12, 2) NOT NULL,
    subtotal_inr    DECIMAL(12, 2) NOT NULL,
    discount_inr    DECIMAL(12, 2) NOT NULL DEFAULT 0,
    shipping_inr    DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_inr       DECIMAL(12, 2) NOT NULL,
    refund_due_inr  DECIMAL(12, 2) NOT NULL DEFAULT 0,
    coupon_code     VARCHAR(40)    NULL,
    provider_ref    VARCHAR(255)   NULL,
    transaction_id  VARCHAR(255)   NULL,
    status          VARCHAR(30)    NOT NULL DEFAULT 'PAYMENT_PENDING' CHECK (status IN ('PAYMENT_PENDING', 'PAID', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'EXPIRED', 'REFUND_REQUIRED', 'REFUNDED')),
    courier         VARCHAR(100)   NULL,
    tracking_number VARCHAR(100)   NULL,
    staff_notes     LONGTEXT       NULL,
    paid_at         DATETIME       NULL,
    created_at      DATETIME       NOT NULL,
    updated_at      DATETIME       NULL,
    CONSTRAINT shop_orders_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION
);
SET @ac_new_idx = (SELECT COUNT(*) = 0 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'shop_orders' AND index_name = 'shop_orders_status_index');
CALL ac_exec_if(@ac_new_idx, 'CREATE INDEX shop_orders_status_index ON shop_orders (status)');
CALL ac_exec_if(@ac_new_idx, 'CREATE INDEX shop_orders_provider_ref_index ON shop_orders (provider_ref)');
CALL ac_exec_if(@ac_new_idx, 'CREATE INDEX shop_orders_owner_index ON shop_orders (owner_key)');

CREATE TABLE IF NOT EXISTS shop_order_items (
    id                  BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    order_id            VARCHAR(50)    NOT NULL,
    painting_id         BIGINT         NOT NULL,
    format              VARCHAR(20)    NOT NULL CHECK (format IN ('ORIGINAL', 'CANVAS', 'POSTER', 'DIGITAL')),
    size_code           VARCHAR(20)    NOT NULL DEFAULT '',
    finish              VARCHAR(20)    NOT NULL DEFAULT '',
    frame               VARCHAR(20)    NOT NULL DEFAULT 'NONE',
    quantity            INT            NOT NULL DEFAULT 1,
    title               VARCHAR(255)   NOT NULL,
    artist_name         VARCHAR(255)   NOT NULL,
    image_url           VARCHAR(500)   NULL,
    variant_label       VARCHAR(255)   NOT NULL,
    unit_price_inr      DECIMAL(12, 2) NOT NULL,
    line_total_inr      DECIMAL(12, 2) NOT NULL,
    discount_inr        DECIMAL(12, 2) NOT NULL DEFAULT 0,
    edition_no          INT            NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'OK', 'REFUND_REQUIRED', 'REFUNDED')),
    download_token      VARCHAR(64)    NULL,
    download_count      INT            NOT NULL DEFAULT 0,
    download_expires_at DATETIME       NULL,
    CONSTRAINT shop_order_items_download_unique UNIQUE (download_token),
    CONSTRAINT shop_order_items_order_id_foreign FOREIGN KEY (order_id) REFERENCES shop_orders (order_id) ON DELETE CASCADE,
    CONSTRAINT shop_order_items_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS artist_threads (
    id              BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    painting_id     BIGINT       NOT NULL,
    user_id         BIGINT       NULL,
    owner_key       VARCHAR(80)  NOT NULL,
    buyer_name      VARCHAR(255) NOT NULL,
    buyer_email     VARCHAR(255) NOT NULL,
    context         VARCHAR(255) NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    staff_unread    INT          NOT NULL DEFAULT 0,
    buyer_unread    INT          NOT NULL DEFAULT 0,
    last_message_at DATETIME     NOT NULL,
    created_at      DATETIME     NOT NULL,
    CONSTRAINT artist_threads_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE CASCADE,
    CONSTRAINT artist_threads_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);
SET @ac_new_tidx = (SELECT COUNT(*) = 0 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'artist_threads' AND index_name = 'artist_threads_owner_index');
CALL ac_exec_if(@ac_new_tidx, 'CREATE INDEX artist_threads_owner_index ON artist_threads (owner_key, painting_id)');

CREATE TABLE IF NOT EXISTS artist_thread_messages (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    thread_id  BIGINT      NOT NULL,
    sender     VARCHAR(10) NOT NULL CHECK (sender IN ('BUYER', 'ARTIST')),
    admin_id   BIGINT      NULL,
    body       TEXT        NOT NULL,
    created_at DATETIME    NOT NULL,
    CONSTRAINT artist_thread_messages_thread_id_foreign FOREIGN KEY (thread_id) REFERENCES artist_threads (id) ON DELETE CASCADE
);

DROP PROCEDURE IF EXISTS ac_exec_if;
