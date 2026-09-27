-- =====================================================================
--  ArtistikCity - 1-of-1 Original Art Marketplace (replaces /student-feedback)
--  Idempotent: safe to run more than once on an existing SQL Server database.
--    sqlcmd -S localhost -d artistikcity -i database/migrations/2026-09-27_art_marketplace.sql
--
--  Integrity rules enforced by the database itself:
--    * final_price can never disagree with base_price / discount_percentage (CHECK).
--    * a painting can only be in one cart at a time (UNIQUE cart_reservations.painting_id).
--    * stock_status is a closed set: DRAFT, PENDING_REVIEW, AVAILABLE, RESERVED, SOLD, ARCHIVED.
--  The application also bumps paintings.version on every state change and only
--  flips AVAILABLE -> RESERVED with "WHERE stock_status = 'AVAILABLE' AND version = @v"
--  (optimistic locking), inside a SERIALIZABLE transaction.
-- =====================================================================

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'paintings')
BEGIN
    CREATE TABLE paintings (
        id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        slug                NVARCHAR(191)  NOT NULL,
        title               NVARCHAR(255)  NOT NULL,
        description         NVARCHAR(MAX)  NOT NULL,
        artist_notes        NVARCHAR(MAX)  NULL,
        medium              NVARCHAR(100)  NOT NULL,
        surface             NVARCHAR(100)  NOT NULL,
        subject             NVARCHAR(100)  NULL,
        style_tags          NVARCHAR(255)  NULL,
        height_inches       DECIMAL(6, 2)  NOT NULL CHECK (height_inches > 0),
        width_inches        DECIMAL(6, 2)  NOT NULL CHECK (width_inches > 0),
        depth_inches        DECIMAL(6, 2)  NOT NULL DEFAULT 0,
        weight_kg           DECIMAL(6, 2)  NULL,
        year_created        INT            NOT NULL,
        is_framed           BIT            NOT NULL DEFAULT 0,
        frame_details       NVARCHAR(255)  NULL,
        is_signed           BIT            NOT NULL DEFAULT 1,
        has_certificate     BIT            NOT NULL DEFAULT 1,
        base_price          DECIMAL(12, 2) NOT NULL CHECK (base_price > 0),
        discount_percentage DECIMAL(5, 2)  NOT NULL DEFAULT 0 CHECK (discount_percentage >= 0 AND discount_percentage <= 90),
        final_price         DECIMAL(12, 2) NOT NULL,
        currency            NVARCHAR(3)    NOT NULL DEFAULT 'INR',
        stock_status        NVARCHAR(30)   NOT NULL DEFAULT 'DRAFT' CHECK (stock_status IN ('DRAFT', 'PENDING_REVIEW', 'AVAILABLE', 'RESERVED', 'SOLD', 'ARCHIVED')),
        version             INT            NOT NULL DEFAULT 0,
        source              NVARCHAR(20)   NOT NULL DEFAULT 'STUDIO' CHECK (source IN ('STUDIO', 'STUDENT')),
        artist_name         NVARCHAR(255)  NOT NULL,
        artist_admin_id     BIGINT         NULL,
        artist_user_id      BIGINT         NULL,
        submission_id       BIGINT         NULL,
        is_featured         BIT            NOT NULL DEFAULT 0,
        review_notes        NVARCHAR(MAX)  NULL,
        reviewed_by         BIGINT         NULL,
        reviewed_at         DATETIME2      NULL,
        published_at        DATETIME2      NULL,
        sold_at             DATETIME2      NULL,
        created_at          DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
        updated_at          DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT paintings_slug_unique UNIQUE (slug),
        CONSTRAINT paintings_final_price_check CHECK (final_price = ROUND(base_price * (100 - discount_percentage) / 100, 2)),
        CONSTRAINT paintings_artist_admin_id_foreign FOREIGN KEY (artist_admin_id) REFERENCES admins (id),
        CONSTRAINT paintings_artist_user_id_foreign FOREIGN KEY (artist_user_id) REFERENCES users (id),
        CONSTRAINT paintings_submission_id_foreign FOREIGN KEY (submission_id) REFERENCES student_submissions (id),
        CONSTRAINT paintings_reviewed_by_foreign FOREIGN KEY (reviewed_by) REFERENCES admins (id)
    );
    CREATE NONCLUSTERED INDEX IX_Paintings_Status ON paintings (stock_status);
    CREATE NONCLUSTERED INDEX paintings_artist_admin_id_index ON paintings (artist_admin_id);
    CREATE NONCLUSTERED INDEX paintings_artist_user_id_index ON paintings (artist_user_id);
    CREATE NONCLUSTERED INDEX paintings_submission_id_index ON paintings (submission_id);
END;
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'painting_images')
BEGIN
    CREATE TABLE painting_images (
        id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        painting_id BIGINT         NOT NULL,
        image_url   NVARCHAR(2083) NOT NULL,
        is_primary  BIT            NOT NULL DEFAULT 0,
        sort_order  INT            NOT NULL DEFAULT 0,
        alt_text    NVARCHAR(255)  NULL,
        CONSTRAINT painting_images_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE CASCADE
    );
    CREATE NONCLUSTERED INDEX painting_images_painting_id_index ON painting_images (painting_id);
END;
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'cart_reservations')
BEGIN
    CREATE TABLE cart_reservations (
        id                 BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        session_or_user_id NVARCHAR(255) NOT NULL,
        painting_id        BIGINT        NOT NULL,
        user_id            BIGINT        NULL,
        reserved_at        DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
        expires_at         DATETIME2     NOT NULL,
        CONSTRAINT cart_reservations_painting_unique UNIQUE (painting_id),   -- a painting can only live in one cart
        CONSTRAINT cart_reservations_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE CASCADE
    );
    CREATE NONCLUSTERED INDEX IX_CartReservations_Expiry ON cart_reservations (expires_at);
    CREATE NONCLUSTERED INDEX cart_reservations_holder_index ON cart_reservations (session_or_user_id);
END;
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'marketplace_orders')
BEGIN
    CREATE TABLE marketplace_orders (
        order_id        NVARCHAR(50)   NOT NULL PRIMARY KEY,
        painting_id     BIGINT         NOT NULL,
        user_id         BIGINT         NULL,
        holder_key      NVARCHAR(255)  NOT NULL,
        buyer_name      NVARCHAR(255)  NOT NULL,
        buyer_email     NVARCHAR(255)  NOT NULL,
        buyer_phone     NVARCHAR(50)   NULL,
        address_line1   NVARCHAR(255)  NOT NULL,
        address_line2   NVARCHAR(255)  NULL,
        city            NVARCHAR(100)  NOT NULL,
        state           NVARCHAR(100)  NULL,
        postal_code     NVARCHAR(20)   NOT NULL,
        country         NVARCHAR(100)  NOT NULL,
        gateway         NVARCHAR(20)   NOT NULL CHECK (gateway IN ('RAZORPAY', 'PAYPAL', 'TEST')),
        currency        NVARCHAR(3)    NOT NULL,
        amount          DECIMAL(12, 2) NOT NULL,
        price_inr       DECIMAL(12, 2) NOT NULL,
        provider_ref    NVARCHAR(255)  NULL,
        transaction_id  NVARCHAR(255)  NULL,
        status          NVARCHAR(30)   NOT NULL DEFAULT 'PAYMENT_PENDING' CHECK (status IN ('PAYMENT_PENDING', 'PAID', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'EXPIRED', 'REFUND_REQUIRED', 'REFUNDED')),
        courier         NVARCHAR(100)  NULL,
        tracking_number NVARCHAR(100)  NULL,
        staff_notes     NVARCHAR(MAX)  NULL,
        paid_at         DATETIME2      NULL,
        created_at      DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
        updated_at      DATETIME2      NULL,
        CONSTRAINT marketplace_orders_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id),
        CONSTRAINT marketplace_orders_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id)
    );
    CREATE NONCLUSTERED INDEX marketplace_orders_painting_id_index ON marketplace_orders (painting_id);
    CREATE NONCLUSTERED INDEX marketplace_orders_status_index ON marketplace_orders (status);
    CREATE NONCLUSTERED INDEX marketplace_orders_provider_ref_index ON marketplace_orders (provider_ref);
END;
GO

-- Carry existing student-shop listings (portfolio_marketplace) into the new marketplace
-- as student originals, once. Their stock becomes a single 1-of-1 piece.
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'portfolio_marketplace')
BEGIN
    INSERT INTO paintings (slug, title, description, medium, surface, height_inches, width_inches, year_created,
        base_price, discount_percentage, final_price, currency, stock_status, source, artist_name, artist_user_id, submission_id, published_at)
    SELECT CONCAT('student-', s.id, '-', LEFT(LOWER(REPLACE(s.title, ' ', '-')), 120)), s.title, COALESCE(s.description, s.title),
           'Mixed media', 'Paper', 12, 16, YEAR(s.created_at),
           m.sale_price, 0, m.sale_price, 'INR', 'PENDING_REVIEW', 'STUDENT', u.name, m.user_id, s.id, NULL
    FROM portfolio_marketplace m
    JOIN student_submissions s ON s.id = m.submission_id
    JOIN users u ON u.id = m.user_id
    WHERE m.is_listed_for_sale = 1 AND m.sale_price IS NOT NULL AND s.admin_status = 'Approved'
      AND NOT EXISTS (SELECT 1 FROM paintings p WHERE p.submission_id = s.id);

    INSERT INTO painting_images (painting_id, image_url, is_primary, sort_order, alt_text)
    SELECT p.id, s.file_url, 1, 0, p.title
    FROM paintings p JOIN student_submissions s ON s.id = p.submission_id
    WHERE NOT EXISTS (SELECT 1 FROM painting_images i WHERE i.painting_id = p.id);
END;
GO
