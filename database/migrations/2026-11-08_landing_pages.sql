-- Landing Page Management (package landing/): configurable course landing pages with their own pricing, payment
-- methods, checkout, coupons, SEO, versions and analytics. Safe to run twice.
-- Content, design, sections and SEO live in a versioned JSON config (draft_config; each publish is a row in
-- landing_page_versions). Money settings (pricing, payment methods, checkout, coupons) are normal tables, live as
-- soon as they are saved and audited, so the price shown and the price charged always come from one place.
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
-- course orders placed through a landing page keep the page, the coupon and the amounts charged
CALL ac_add_col('orders', 'landing_page_id', 'BIGINT NULL');
CALL ac_add_col('orders', 'coupon_code', 'VARCHAR(40) NULL');
CALL ac_add_col('orders', 'list_amount', 'DECIMAL(12, 2) NULL');
CALL ac_add_col('orders', 'discount_amount', 'DECIMAL(12, 2) NULL');
CALL ac_add_col('orders', 'tax_amount', 'DECIMAL(12, 2) NULL');
CALL ac_add_col('orders', 'payment_method', 'VARCHAR(30) NULL');
CALL ac_add_col('orders', 'payment_provider', 'VARCHAR(20) NULL');
CALL ac_add_col('orders', 'utm_source', 'VARCHAR(80) NULL');
CALL ac_add_col('orders', 'utm_medium', 'VARCHAR(80) NULL');
CALL ac_add_col('orders', 'utm_campaign', 'VARCHAR(120) NULL');
CALL ac_add_col('orders', 'landing_visitor', 'CHAR(32) NULL');
CALL ac_add_col('orders', 'billing_phone', 'VARCHAR(20) NULL');
-- landing capabilities granted to one admin on top of their persona (People & roles), comma separated
CALL ac_add_col('admins', 'extra_capabilities', 'VARCHAR(500) NULL');
DROP PROCEDURE ac_add_col;

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
CALL ac_add_index('orders', 'orders_landing_page_index', 'landing_page_id, payment_status');
DROP PROCEDURE ac_add_index;

CREATE TABLE IF NOT EXISTS landing_pages (
    id                   BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name                 VARCHAR(190) NOT NULL,
    slug                 VARCHAR(190) NOT NULL,
    course_id            BIGINT       NOT NULL,
    campaign             VARCHAR(120) NULL,
    description          VARCHAR(500) NULL,
    template_key         VARCHAR(40)  NOT NULL,
    status               VARCHAR(12)  NOT NULL CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'ARCHIVED')),
    draft_config         LONGTEXT     NOT NULL,
    draft_saved_at       DATETIME     NULL,
    draft_saved_by       BIGINT       NULL,
    published_version_id BIGINT       NULL,
    published_at         DATETIME     NULL,
    publish_at           DATETIME     NULL,
    archived_at          DATETIME     NULL,
    created_by           BIGINT       NULL,
    created_at           DATETIME     NOT NULL,
    updated_at           DATETIME     NOT NULL,
    CONSTRAINT landing_pages_slug_unique UNIQUE (slug),
    INDEX landing_pages_course_index (course_id, status),
    CONSTRAINT landing_pages_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id)
);

CREATE TABLE IF NOT EXISTS landing_page_versions (
    id              BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    landing_page_id BIGINT       NOT NULL,
    version_no      INT          NOT NULL,
    template_key    VARCHAR(40)  NOT NULL,
    config          LONGTEXT     NOT NULL,
    note            VARCHAR(255) NULL,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL,
    CONSTRAINT landing_page_versions_unique UNIQUE (landing_page_id, version_no),
    CONSTRAINT landing_page_versions_page_foreign FOREIGN KEY (landing_page_id) REFERENCES landing_pages (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS landing_pricing (
    landing_page_id  BIGINT         NOT NULL PRIMARY KEY,
    list_price       DECIMAL(12, 2) NULL,
    price            DECIMAL(12, 2) NULL,
    price_usd        DECIMAL(12, 2) NULL,
    promo_label      VARCHAR(80)    NULL,
    promo_price      DECIMAL(12, 2) NULL,
    promo_starts_at  DATETIME       NULL,
    promo_ends_at    DATETIME       NULL,
    early_price      DECIMAL(12, 2) NULL,
    early_ends_at    DATETIME       NULL,
    coupons_enabled  TINYINT(1)     NOT NULL DEFAULT 1,
    tax_mode         VARCHAR(10)    NOT NULL DEFAULT 'INCLUDED' CHECK (tax_mode IN ('INCLUDED', 'ADDED')),
    tax_rate         DECIMAL(5, 2)  NOT NULL DEFAULT 0,
    tax_label        VARCHAR(40)    NULL,
    access_duration  VARCHAR(120)   NULL,
    refund_policy    VARCHAR(1000)  NULL,
    inclusions       LONGTEXT       NULL,
    updated_by       BIGINT         NULL,
    updated_at       DATETIME       NOT NULL,
    CONSTRAINT landing_pricing_page_foreign FOREIGN KEY (landing_page_id) REFERENCES landing_pages (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS landing_payment_settings (
    landing_page_id  BIGINT      NOT NULL PRIMARY KEY,
    mode             VARCHAR(8)  NOT NULL DEFAULT 'GLOBAL' CHECK (mode IN ('GLOBAL', 'CUSTOM')),
    default_provider VARCHAR(20) NULL,
    updated_by       BIGINT      NULL,
    updated_at       DATETIME    NOT NULL,
    CONSTRAINT landing_payment_settings_page_foreign FOREIGN KEY (landing_page_id) REFERENCES landing_pages (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS landing_payment_providers (
    id              BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    landing_page_id BIGINT      NOT NULL,
    provider        VARCHAR(20) NOT NULL,
    enabled         TINYINT(1)  NOT NULL DEFAULT 1,
    priority        INT         NOT NULL DEFAULT 1,
    CONSTRAINT landing_payment_providers_unique UNIQUE (landing_page_id, provider),
    CONSTRAINT landing_payment_providers_page_foreign FOREIGN KEY (landing_page_id) REFERENCES landing_pages (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS landing_payment_methods (
    id              BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    landing_page_id BIGINT      NOT NULL,
    method          VARCHAR(30) NOT NULL,
    enabled         TINYINT(1)  NOT NULL DEFAULT 1,
    sort_order      INT         NOT NULL DEFAULT 0,
    CONSTRAINT landing_payment_methods_unique UNIQUE (landing_page_id, method),
    CONSTRAINT landing_payment_methods_page_foreign FOREIGN KEY (landing_page_id) REFERENCES landing_pages (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS landing_checkout (
    landing_page_id  BIGINT        NOT NULL PRIMARY KEY,
    require_phone    TINYINT(1)    NOT NULL DEFAULT 1,
    require_address  TINYINT(1)    NOT NULL DEFAULT 0,
    require_terms    TINYINT(1)    NOT NULL DEFAULT 1,
    require_privacy  TINYINT(1)    NOT NULL DEFAULT 0,
    show_coupon      TINYINT(1)    NOT NULL DEFAULT 1,
    show_summary     TINYINT(1)    NOT NULL DEFAULT 1,
    success_message  VARCHAR(500)  NULL,
    success_redirect VARCHAR(255)  NULL,
    failure_message  VARCHAR(500)  NULL,
    pending_message  VARCHAR(500)  NULL,
    updated_by       BIGINT        NULL,
    updated_at       DATETIME      NOT NULL,
    CONSTRAINT landing_checkout_page_foreign FOREIGN KEY (landing_page_id) REFERENCES landing_pages (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS landing_coupons (
    id              BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    code            VARCHAR(40)    NOT NULL,
    description     VARCHAR(255)   NULL,
    kind            VARCHAR(10)    NOT NULL CHECK (kind IN ('PERCENT', 'FLAT')),
    value           DECIMAL(12, 2) NOT NULL CHECK (value > 0),
    campaign        VARCHAR(120)   NULL,
    all_pages       TINYINT(1)     NOT NULL DEFAULT 0,
    min_amount      DECIMAL(12, 2) NULL,
    max_uses        INT            NULL,
    per_user_limit  INT            NULL,
    first_time_only TINYINT(1)     NOT NULL DEFAULT 0,
    starts_at       DATETIME       NULL,
    expires_at      DATETIME       NULL,
    is_active       TINYINT(1)     NOT NULL DEFAULT 1,
    used_count      INT            NOT NULL DEFAULT 0,
    created_by      BIGINT         NULL,
    created_at      DATETIME       NOT NULL,
    updated_at      DATETIME       NOT NULL,
    CONSTRAINT landing_coupons_code_unique UNIQUE (code)
);

-- which pages / courses a coupon applies to (none and all_pages = 0: nowhere)
CREATE TABLE IF NOT EXISTS landing_coupon_targets (
    coupon_id   BIGINT      NOT NULL,
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('PAGE', 'COURSE')),
    target_id   BIGINT      NOT NULL,
    PRIMARY KEY (coupon_id, target_type, target_id),
    CONSTRAINT landing_coupon_targets_coupon_foreign FOREIGN KEY (coupon_id) REFERENCES landing_coupons (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS landing_coupon_redemptions (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    coupon_id  BIGINT         NOT NULL,
    order_id   BIGINT         NOT NULL,
    user_id    BIGINT         NOT NULL,
    discount   DECIMAL(12, 2) NOT NULL,
    created_at DATETIME       NOT NULL,
    CONSTRAINT landing_coupon_redemptions_order_unique UNIQUE (order_id),
    INDEX landing_coupon_redemptions_user_index (coupon_id, user_id),
    CONSTRAINT landing_coupon_redemptions_coupon_foreign FOREIGN KEY (coupon_id) REFERENCES landing_coupons (id)
);

-- funnel events (no personal data: visitor is a random cookie id, hashed)
CREATE TABLE IF NOT EXISTS landing_events (
    id              BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    landing_page_id BIGINT       NOT NULL,
    event           VARCHAR(20)  NOT NULL,
    visitor         CHAR(32)     NOT NULL,
    method          VARCHAR(30)  NULL,
    provider        VARCHAR(20)  NULL,
    order_id        BIGINT       NULL,
    utm_source      VARCHAR(80)  NULL,
    utm_medium      VARCHAR(80)  NULL,
    utm_campaign    VARCHAR(120) NULL,
    device          VARCHAR(10)  NULL,
    created_at      DATETIME     NOT NULL,
    INDEX landing_events_page_index (landing_page_id, event, created_at),
    INDEX landing_events_time_index (created_at)
);

CREATE TABLE IF NOT EXISTS landing_media (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    path        VARCHAR(500)  NOT NULL,
    url         VARCHAR(1000) NOT NULL,
    alt         VARCHAR(255)  NULL,
    width       INT           NULL,
    height      INT           NULL,
    bytes       INT           NULL,
    uploaded_by BIGINT        NULL,
    created_at  DATETIME      NOT NULL
);
