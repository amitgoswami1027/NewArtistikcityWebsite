-- =====================================================================
--  Artist Commission Portal - migration for an EXISTING SQL Server database
--  (fresh installs get these tables from database/schema.sql).
--  Run with:  sqlcmd -S localhost -d artistikcity -i database/migrations/2026-09-27_commission_portal.sql
--  Safe to run more than once.
-- =====================================================================
IF OBJECT_ID('dbo.commission_orders', 'U') IS NULL
BEGIN
    CREATE TABLE commission_orders (
        order_id               NVARCHAR(50)   NOT NULL PRIMARY KEY,
        user_id                BIGINT         NULL,
        customer_name          NVARCHAR(255)  NOT NULL,
        customer_email         NVARCHAR(255)  NOT NULL,
        customer_phone         NVARCHAR(50)   NULL,
        fulfillment_type       NVARCHAR(50)   NOT NULL CHECK (fulfillment_type IN ('DIGITAL_ONLY', 'PHYSICAL_PRINT', 'ORIGINAL_PAINTING')),
        special_instructions   NVARCHAR(MAX)  NULL,
        uploaded_photo_url     NVARCHAR(2083) NOT NULL,
        shipping_address_line1 NVARCHAR(255)  NULL,
        shipping_city          NVARCHAR(100)  NULL,
        shipping_postal_code   NVARCHAR(20)   NULL,
        shipping_country       NVARCHAR(100)  NULL,
        payment_method         NVARCHAR(50)   NOT NULL CHECK (payment_method IN ('CREDIT_CARD', 'RAZORPAY_UPI', 'PAYPAL')),
        payment_provider_ref   NVARCHAR(255)  NULL,
        payment_transaction_id NVARCHAR(255)  NULL,
        order_status           NVARCHAR(50)   NOT NULL DEFAULT 'PAYMENT_PENDING' CHECK (order_status IN ('PAYMENT_PENDING', 'QUEUED', 'PROOF_SENT', 'IN_PRODUCTION', 'SHIPPED', 'COMPLETED')),
        currency               NVARCHAR(3)    NOT NULL DEFAULT 'USD',
        total_price            DECIMAL(10, 2) NOT NULL,
        created_at             DATETIME2      NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at             DATETIME2      NULL
    );
    CREATE INDEX commission_orders_provider_ref_index ON commission_orders (payment_provider_ref);
END;

IF OBJECT_ID('dbo.commission_artwork_items', 'U') IS NULL
BEGIN
    CREATE TABLE commission_artwork_items (
        item_id        NVARCHAR(50)  NOT NULL PRIMARY KEY,
        order_id       NVARCHAR(50)  NOT NULL,
        artwork_theme  NVARCHAR(50)  NOT NULL CHECK (artwork_theme IN ('PORTRAIT', 'LANDSCAPE', 'WATERCOLOR', 'CHARCOAL')),
        width_inches   INT           NOT NULL,
        height_inches  INT           NOT NULL,
        frame_material NVARCHAR(100) NOT NULL DEFAULT 'NONE',
        has_matting    BIT           NOT NULL DEFAULT 0,
        CONSTRAINT commission_artwork_items_order_id_foreign FOREIGN KEY (order_id) REFERENCES commission_orders (order_id) ON DELETE CASCADE
    );
END;
