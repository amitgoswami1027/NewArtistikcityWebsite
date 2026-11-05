-- =====================================================================
--  ArtistikCity - MySQL 8.x schema (InnoDB, utf8mb4)
--  Converted from the Laravel (MySQL) migrations and reconciled with the
--  columns the application code actually reads/writes (several columns
--  were used by the PHP code but missing from the original migrations).
--
--  Run with:  mysql -u artistik -p artistikcity < database/schema.sql
--  WARNING: drops and recreates every table (all data is lost).
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;
DROP VIEW IF EXISTS courses_workshops;
DROP TABLE IF EXISTS wishlist_items;
DROP TABLE IF EXISTS marketplace_orders;
DROP TABLE IF EXISTS cart_reservations;
DROP TABLE IF EXISTS painting_images;
DROP TABLE IF EXISTS paintings;
DROP TABLE IF EXISTS course_certificates;
DROP TABLE IF EXISTS portfolio_marketplace;
DROP TABLE IF EXISTS student_submissions;
DROP TABLE IF EXISTS lesson_completions;
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS commission_artwork_items;
DROP TABLE IF EXISTS commission_orders;
DROP TABLE IF EXISTS student_task_photos;
DROP TABLE IF EXISTS student_tasks;
DROP TABLE IF EXISTS student_teacher_connects;
DROP TABLE IF EXISTS certificates;
DROP TABLE IF EXISTS course_module_lesson_tasks;
DROP TABLE IF EXISTS course_module_lessons;
DROP TABLE IF EXISTS course_modules;
DROP TABLE IF EXISTS course_projects;
DROP TABLE IF EXISTS course_videos;
DROP TABLE IF EXISTS course_reviews;
DROP TABLE IF EXISTS course_photos;
DROP TABLE IF EXISTS course_prices;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS category_post;
DROP TABLE IF EXISTS post_tag;
DROP TABLE IF EXISTS posts;
DROP TABLE IF EXISTS tags;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS free_course_videos;
DROP TABLE IF EXISTS free_courses;
DROP TABLE IF EXISTS home_artworks;
DROP TABLE IF EXISTS student_artworks;
DROP TABLE IF EXISTS user_addresses;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS payment_logs;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS contacts;
DROP TABLE IF EXISTS newsletters;
DROP TABLE IF EXISTS settings;
DROP TABLE IF EXISTS testimonials;
DROP TABLE IF EXISTS mediums;
DROP TABLE IF EXISTS genres;
DROP TABLE IF EXISTS skills;
DROP TABLE IF EXISTS course_types;
DROP TABLE IF EXISTS password_resets;
DROP TABLE IF EXISTS auth_events;
DROP TABLE IF EXISTS webauthn_credentials;
DROP TABLE IF EXISTS auth_tokens;
DROP TABLE IF EXISTS auth_otps;
DROP TABLE IF EXISTS auth_refresh_tokens;
DROP TABLE IF EXISTS auth_sessions;
DROP TABLE IF EXISTS linked_accounts;
DROP TABLE IF EXISTS admins;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                  BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    slug                VARCHAR(255) NULL,
    -- sign-in identifiers: at least one of email / phone_number is set (phone-only accounts have no email)
    email               VARCHAR(255) NULL,
    email_verified_at   DATETIME NULL,
    phone_number        VARCHAR(20) NULL,           -- E.164, e.g. +919876543210
    phone_verified_at   DATETIME NULL,
    password_hash       VARCHAR(255) NULL,          -- bcrypt; NULL for passwordless accounts
    is_verified         TINYINT(1) NOT NULL DEFAULT 0,  -- email or phone ownership proven
    webauthn_user_handle VARCHAR(100) NULL,         -- random passkey user handle (base64url)
    last_login_at       DATETIME NULL,
    profile_title       VARCHAR(255) NULL,
    profile_description LONGTEXT NULL,
    profile_photo       VARCHAR(1000) NULL,
    location            VARCHAR(255) NULL,
    notifications       TINYINT NULL,
    language_preference VARCHAR(50) NULL,
    free_courses        TINYINT NOT NULL DEFAULT 0,
    free_course_type    VARCHAR(20) NOT NULL DEFAULT 'default'
                        CHECK (free_course_type IN ('color', 'black-white', 'default')),
    status              TINYINT NOT NULL DEFAULT 1,
    remember_token      VARCHAR(100) NULL,
    created_at          DATETIME NULL,
    updated_at          DATETIME NULL,
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_phone_number_unique UNIQUE (phone_number),
    CONSTRAINT users_webauthn_user_handle_unique UNIQUE (webauthn_user_handle)
);

-- ---------------------------------------------------------------------
-- Authentication (see src/main/java/com/artistikcity/auth)
-- ---------------------------------------------------------------------
-- One row per social identity, all mapped to a single users.id.
CREATE TABLE linked_accounts (
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
    profile_data      JSON NULL,                    -- the provider profile as received (no tokens)
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at     DATETIME NULL,
    CONSTRAINT linked_accounts_provider_unique UNIQUE (provider_name, provider_user_id),
    CONSTRAINT linked_accounts_user_provider_unique UNIQUE (user_id, provider_name),
    CONSTRAINT linked_accounts_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- A login on one device. The browser holds an opaque session token (rotated regularly) and a
-- single-use refresh token; only SHA-256 hashes are stored.
CREATE TABLE auth_sessions (
    id                        BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id                   BIGINT NOT NULL,
    session_token_hash        CHAR(64) NOT NULL,
    session_expires_at        DATETIME NOT NULL,   -- short, sliding (idle timeout)
    previous_token_hash       CHAR(64) NULL,       -- accepted briefly after a rotation (parallel requests)
    previous_valid_until      DATETIME NULL,
    rotated_at                DATETIME NOT NULL,
    auth_method               VARCHAR(20) NOT NULL CHECK (auth_method IN ('password', 'google', 'facebook', 'otp', 'magic_link', 'passkey', 'signup')),
    remember                  TINYINT(1) NOT NULL DEFAULT 0,
    ip_address                VARCHAR(45) NULL,
    user_agent                VARCHAR(500) NULL,
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at                DATETIME NOT NULL,   -- absolute lifetime of this login
    revoked_at                DATETIME NULL,
    revoke_reason             VARCHAR(50) NULL,
    CONSTRAINT auth_sessions_token_unique UNIQUE (session_token_hash),
    CONSTRAINT auth_sessions_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX auth_sessions_user_index ON auth_sessions (user_id, revoked_at);
CREATE INDEX auth_sessions_previous_index ON auth_sessions (previous_token_hash);

CREATE TABLE auth_refresh_tokens (
    id           BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    session_id   BIGINT NOT NULL,
    token_hash   CHAR(64) NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   DATETIME NOT NULL,
    used_at      DATETIME NULL,                    -- set when exchanged; presenting it again = reuse
    CONSTRAINT auth_refresh_tokens_hash_unique UNIQUE (token_hash),
    CONSTRAINT auth_refresh_tokens_session_foreign FOREIGN KEY (session_id) REFERENCES auth_sessions (id) ON DELETE CASCADE
);

-- SMS one-time passwords (HMAC-SHA256 of the code, never the code itself).
CREATE TABLE auth_otps (
    id            BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    phone_number  VARCHAR(20) NOT NULL,
    purpose       VARCHAR(20) NOT NULL DEFAULT 'login' CHECK (purpose IN ('login', 'verify_phone')),
    user_id       BIGINT NULL,                     -- set for verify_phone (adding a number to an account)
    code_hash     CHAR(64) NOT NULL,
    attempts      INT NOT NULL DEFAULT 0,
    expires_at    DATETIME NOT NULL,
    consumed_at   DATETIME NULL,
    ip_address    VARCHAR(45) NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX auth_otps_phone_index ON auth_otps (phone_number, created_at);
CREATE INDEX auth_otps_ip_index ON auth_otps (ip_address, created_at);

-- Single-use e-mailed tokens: magic links, e-mail verification, password reset (SHA-256 hashed).
CREATE TABLE auth_tokens (
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
    CONSTRAINT auth_tokens_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX auth_tokens_email_index ON auth_tokens (email, purpose, created_at);

-- Passkeys (WebAuthn credentials).
CREATE TABLE webauthn_credentials (
    id               BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    credential_id    VARCHAR(512) NOT NULL,        -- base64url
    public_key_cose  BLOB NOT NULL,
    algorithm        INT NOT NULL,                 -- COSE alg: -7 ES256, -257 RS256, -8 EdDSA
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

-- Security audit trail (sign-ins, failures, linking, revocations).
CREATE TABLE auth_events (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id     BIGINT NULL,
    event       VARCHAR(50) NOT NULL,
    method      VARCHAR(20) NULL,
    success     TINYINT(1) NOT NULL DEFAULT 1,
    ip_address  VARCHAR(45) NULL,
    user_agent  VARCHAR(500) NULL,
    detail      VARCHAR(500) NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX auth_events_user_index ON auth_events (user_id, created_at);
CREATE INDEX auth_events_event_index ON auth_events (event, created_at);

CREATE TABLE admins (
    id                  BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    slug                VARCHAR(255) NULL,
    email               VARCHAR(255) NOT NULL,
    password            VARCHAR(255) NOT NULL,
    admin_type          VARCHAR(20) NOT NULL CHECK (admin_type IN ('admin', 'teacher', 'moderator')),
    location            VARCHAR(255) NULL,
    profile_photo       VARCHAR(500) NULL,
    profile_details     LONGTEXT NULL,
    title               LONGTEXT NULL,
    designation         LONGTEXT NULL,
    profile_title       VARCHAR(255) NULL,
    profile_description LONGTEXT NULL,
    remember_token      VARCHAR(100) NULL,
    created_at          DATETIME NULL,
    updated_at          DATETIME NULL,
    CONSTRAINT admins_email_unique UNIQUE (email)
);

-- ---------------------------------------------------------------------
-- Course taxonomies
-- ---------------------------------------------------------------------
CREATE TABLE mediums (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    medium_slug VARCHAR(100) NOT NULL,
    photo       VARCHAR(500) NULL,
    status      TINYINT NOT NULL DEFAULT 1,
    created_at  DATETIME NULL,
    updated_at  DATETIME NULL
);

CREATE TABLE genres (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    genre_slug VARCHAR(100) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE course_types (
    id               BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    course_type_slug VARCHAR(100) NOT NULL,
    status           TINYINT NOT NULL DEFAULT 1,
    created_at       DATETIME NULL,
    updated_at       DATETIME NULL
);

CREATE TABLE skills (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    skill_slug VARCHAR(100) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

-- categories are shared by courses and blog posts (as in the original app)
CREATE TABLE categories (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(255) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

-- ---------------------------------------------------------------------
-- Courses & workshops  (course_type_id 1 = course, 2 = workshop)
-- ---------------------------------------------------------------------
CREATE TABLE courses (
    id                     BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    main_course_id         BIGINT NULL,
    admin_id               BIGINT NOT NULL,
    medium_id              BIGINT NULL,
    genre_id               VARCHAR(100) NULL,        -- comma separated genre ids
    course_type_id         BIGINT NULL,
    skill_id               BIGINT NULL,
    category_id            BIGINT NULL,
    title                  VARCHAR(255) NOT NULL,
    slug                   VARCHAR(255) NOT NULL,
    sub_title              VARCHAR(500) NULL,
    introduction           LONGTEXT NULL,
    introduction_details   LONGTEXT NULL,
    summary                LONGTEXT NULL,
    summary_details        LONGTEXT NULL,
    schedule               LONGTEXT NULL,
    schedule_details       LONGTEXT NULL,
    schedule_pdf           VARCHAR(500) NULL,
    age_group              VARCHAR(255) NULL,
    duration               VARCHAR(255) NULL,
    sessions               VARCHAR(255) NULL,
    mini_projects          VARCHAR(255) NULL,
    course_modules         VARCHAR(255) NULL,
    time_required          VARCHAR(255) NULL,
    includes               LONGTEXT NULL,
    course_includes        LONGTEXT NULL,         -- JSON
    course_highlights      LONGTEXT NULL,         -- JSON
    course_for             LONGTEXT NULL,         -- JSON
    course_not_for         LONGTEXT NULL,         -- JSON
    course_problems_solved LONGTEXT NULL,         -- JSON
    course_learn           LONGTEXT NULL,         -- JSON
    course_outcome         LONGTEXT NULL,
    course_deliverables    LONGTEXT NULL,         -- JSON
    course_prerequisites   LONGTEXT NULL,
    course_start_date      DATE NULL,
    course_based           VARCHAR(50) NULL,
    is_free_course         TINYINT NOT NULL DEFAULT 0,
    status                 TINYINT NOT NULL DEFAULT 1,
    created_at             DATETIME NULL,
    updated_at             DATETIME NULL,
    CONSTRAINT courses_admin_id_foreign FOREIGN KEY (admin_id) REFERENCES admins (id)
);
CREATE INDEX courses_slug_index ON courses (slug);

CREATE TABLE course_prices (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    course_id  BIGINT NOT NULL,
    price_inr  DOUBLE NOT NULL DEFAULT 0,
    price_usd  DOUBLE NOT NULL DEFAULT 0,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    CONSTRAINT course_prices_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id)
);

CREATE TABLE course_photos (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    course_id  BIGINT NOT NULL,
    photo_name VARCHAR(500) NOT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    CONSTRAINT course_photos_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id)
);

CREATE TABLE course_videos (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    course_id   BIGINT NOT NULL,
    video_title VARCHAR(255) NULL,
    video_url   VARCHAR(1000) NULL,
    status      TINYINT NOT NULL DEFAULT 0,
    created_at  DATETIME NULL,
    updated_at  DATETIME NULL,
    CONSTRAINT course_videos_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id)
);

CREATE TABLE course_reviews (
    id            BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    course_id     BIGINT NOT NULL,
    added_by_id   BIGINT NOT NULL,
    added_by_type VARCHAR(20) NOT NULL CHECK (added_by_type IN ('Teacher', 'Student')),
    rating        INT NULL,
    review        LONGTEXT NULL,
    status        TINYINT NOT NULL DEFAULT 0,
    created_at    DATETIME NULL,
    updated_at    DATETIME NULL
);

CREATE TABLE course_modules (
    id              BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    admin_id        BIGINT NULL,
    course_id       BIGINT NOT NULL,
    module_title    VARCHAR(255) NOT NULL,
    module_duration VARCHAR(255) NULL,
    status          VARCHAR(20) NOT NULL DEFAULT '0',
    created_at      DATETIME NULL,
    updated_at      DATETIME NULL,
    CONSTRAINT course_modules_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT course_modules_admin_id_foreign FOREIGN KEY (admin_id) REFERENCES admins (id)
);

CREATE TABLE course_module_lessons (
    id                 BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    admin_id           BIGINT NULL,
    course_id          BIGINT NOT NULL,
    module_id          BIGINT NOT NULL,
    lesson_title       VARCHAR(255) NOT NULL,
    lesson_description LONGTEXT NULL,
    lesson_pdf         VARCHAR(500) NULL,
    status             VARCHAR(20) NOT NULL DEFAULT '0',
    created_at         DATETIME NULL,
    updated_at         DATETIME NULL,
    CONSTRAINT course_module_lessons_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT course_module_lessons_module_id_foreign FOREIGN KEY (module_id) REFERENCES course_modules (id),
    CONSTRAINT course_module_lessons_admin_id_foreign FOREIGN KEY (admin_id) REFERENCES admins (id)
);

CREATE TABLE course_module_lesson_tasks (
    id               BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    admin_id         BIGINT NULL,
    course_id        BIGINT NOT NULL,
    module_id        BIGINT NOT NULL,
    lesson_id        BIGINT NOT NULL,
    task_title       VARCHAR(255) NOT NULL,
    task_description LONGTEXT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT '0',
    created_at       DATETIME NULL,
    updated_at       DATETIME NULL,
    CONSTRAINT course_module_lesson_tasks_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT course_module_lesson_tasks_module_id_foreign FOREIGN KEY (module_id) REFERENCES course_modules (id),
    CONSTRAINT course_module_lesson_tasks_lesson_id_foreign FOREIGN KEY (lesson_id) REFERENCES course_module_lessons (id),
    CONSTRAINT course_module_lesson_tasks_admin_id_foreign FOREIGN KEY (admin_id) REFERENCES admins (id)
);

CREATE TABLE course_projects (
    id                  BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    admin_id            BIGINT NULL,
    course_id           BIGINT NOT NULL,
    project_title       VARCHAR(255) NOT NULL,
    project_description LONGTEXT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT '0',
    created_at          DATETIME NULL,
    updated_at          DATETIME NULL,
    CONSTRAINT course_projects_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT course_projects_admin_id_foreign FOREIGN KEY (admin_id) REFERENCES admins (id)
);

-- ---------------------------------------------------------------------
-- Student <-> teacher task conversation
-- ---------------------------------------------------------------------
CREATE TABLE student_teacher_connects (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    task_id    BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    CONSTRAINT stc_task_id_foreign FOREIGN KEY (task_id) REFERENCES course_module_lesson_tasks (id),
    CONSTRAINT stc_student_id_foreign FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT stc_teacher_id_foreign FOREIGN KEY (teacher_id) REFERENCES admins (id)
);

CREATE TABLE student_tasks (
    id                         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    task_id                    BIGINT NOT NULL,
    student_teacher_connect_id BIGINT NOT NULL,
    from_type                  VARCHAR(20) NOT NULL CHECK (from_type IN ('Student', 'Teacher')),
    from_id                    BIGINT NOT NULL,
    to_type                    VARCHAR(20) NOT NULL CHECK (to_type IN ('Student', 'Teacher')),
    to_id                      BIGINT NOT NULL,
    reply                      LONGTEXT NULL,
    review_status              VARCHAR(20) NOT NULL
                               CHECK (review_status IN ('Under Review', 'Reviewed', 'Feedback', 'Completed')),
    review_date                DATETIME NULL,
    deleted_at                 DATETIME NULL,
    created_at                 DATETIME NULL,
    updated_at                 DATETIME NULL,
    CONSTRAINT student_tasks_task_id_foreign FOREIGN KEY (task_id) REFERENCES course_module_lesson_tasks (id),
    CONSTRAINT student_tasks_connect_id_foreign FOREIGN KEY (student_teacher_connect_id) REFERENCES student_teacher_connects (id)
);

CREATE TABLE student_task_photos (
    id              BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    task_id         BIGINT NOT NULL,
    student_task_id BIGINT NOT NULL,
    photo_name      VARCHAR(500) NOT NULL,
    created_at      DATETIME NULL,
    updated_at      DATETIME NULL,
    CONSTRAINT student_task_photos_task_id_foreign FOREIGN KEY (task_id) REFERENCES course_module_lesson_tasks (id)
);

-- ---------------------------------------------------------------------
-- Commerce
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    order_number      VARCHAR(100) NULL,
    user_id           BIGINT NOT NULL,
    course_id         BIGINT NOT NULL,
    session_id        VARCHAR(255) NOT NULL,
    billing_name      VARCHAR(255) NOT NULL,
    billing_email     VARCHAR(255) NULL,
    billing_address   VARCHAR(500) NULL,
    billing_city      VARCHAR(255) NULL,
    billing_state     VARCHAR(255) NULL,
    billing_zipcode   VARCHAR(50) NULL,
    billing_country   VARCHAR(255) NULL,
    price_type        VARCHAR(10) NULL,
    price             VARCHAR(50) NULL,
    payment_option    VARCHAR(50) NULL,
    payment_id        VARCHAR(255) NULL,
    gateway_ref       VARCHAR(255) NULL,           -- Razorpay / PayPal order id created for this order
    payment_status    TINYINT NOT NULL DEFAULT 0,  -- 0 = not paid, 1 = success, 2 = failed
    payment_details   LONGTEXT NULL,
    issue_certificate TINYINT NOT NULL DEFAULT 0,  -- 0 = not issued, 1 = issued
    certificate_name  VARCHAR(500) NULL,
    created_at        DATETIME NULL,
    updated_at        DATETIME NULL
);
CREATE INDEX orders_gateway_ref_index ON orders (gateway_ref);
CREATE INDEX orders_user_course_index ON orders (user_id, course_id);

CREATE TABLE payment_logs (
    id              BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    order_id        BIGINT NULL,
    course_id       BIGINT NULL,
    payment_option  VARCHAR(50) NULL,
    payment_id      VARCHAR(255) NULL,
    payment_status  TINYINT NOT NULL DEFAULT 0,
    payment_details LONGTEXT NULL,
    created_at      DATETIME NULL,
    updated_at      DATETIME NULL
);

CREATE TABLE cart_items (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    user_id    BIGINT NULL,
    order_id   BIGINT NULL,
    course_id  BIGINT NOT NULL,
    price_type VARCHAR(10) NULL,
    price      VARCHAR(50) NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

-- Things a student saved for later: a course/workshop or a marketplace original.
CREATE TABLE wishlist_items (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    item_type  VARCHAR(20) NOT NULL CHECK (item_type IN ('course', 'painting')),
    item_id    BIGINT      NOT NULL,
    created_at DATETIME    NOT NULL,
    CONSTRAINT wishlist_items_unique UNIQUE (user_id, item_type, item_id),
    CONSTRAINT wishlist_items_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_addresses (
    id           BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    address_name VARCHAR(255) NOT NULL,
    address      VARCHAR(255) NOT NULL,
    city         VARCHAR(255) NULL,
    state        VARCHAR(255) NULL,
    country      VARCHAR(255) NULL,
    zipcode      VARCHAR(255) NULL,
    `primary`    TINYINT NULL DEFAULT 0,
    status       TINYINT NULL DEFAULT 1,
    created_at   DATETIME NULL,
    updated_at   DATETIME NULL
);

CREATE TABLE certificates (
    id                 BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    course_id          BIGINT NOT NULL,
    order_id           BIGINT NOT NULL,
    admin_id           BIGINT NOT NULL,
    user_name          VARCHAR(255) NULL,
    course_title       VARCHAR(255) NULL,
    teacher_name       VARCHAR(255) NULL,
    certificate_number VARCHAR(255) NULL,
    certificate_name   VARCHAR(500) NULL,
    certificate_path   VARCHAR(1000) NULL,
    status             TINYINT NOT NULL DEFAULT 1,
    created_at         DATETIME NULL,
    updated_at         DATETIME NULL,
    CONSTRAINT certificates_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT certificates_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT certificates_order_id_foreign FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT certificates_admin_id_foreign FOREIGN KEY (admin_id) REFERENCES admins (id)
);

-- ---------------------------------------------------------------------
-- Free courses
-- ---------------------------------------------------------------------
CREATE TABLE free_courses (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    admin_id    BIGINT NOT NULL,
    title       VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL,
    description LONGTEXT NULL,
    status      TINYINT NULL,
    photo       VARCHAR(500) NULL,
    video_url   VARCHAR(1000) NULL,
    created_at  DATETIME NULL,
    updated_at  DATETIME NULL
);

CREATE TABLE free_course_videos (
    id             BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    free_course_id BIGINT NOT NULL,
    video_title    VARCHAR(255) NULL,
    video_url      VARCHAR(1000) NULL,
    status         TINYINT NOT NULL DEFAULT 0,
    created_at     DATETIME NULL,
    updated_at     DATETIME NULL
);

-- ---------------------------------------------------------------------
-- Site content
-- ---------------------------------------------------------------------
CREATE TABLE home_artworks (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    admin_id   BIGINT NOT NULL,
    photo_name VARCHAR(500) NULL,
    comments   LONGTEXT NULL,
    status     TINYINT NULL DEFAULT 0,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE student_artworks (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    photo_name VARCHAR(500) NOT NULL DEFAULT '',
    comments   LONGTEXT NULL,
    status     TINYINT NULL DEFAULT 1,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE testimonials (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NULL,
    title       VARCHAR(255) NULL,
    photo       VARCHAR(500) NULL,
    description LONGTEXT NULL,
    status      TINYINT NOT NULL DEFAULT 1,
    created_at  DATETIME NULL,
    updated_at  DATETIME NULL
);

CREATE TABLE newsletters (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE contacts (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name       VARCHAR(255) NULL,
    mobile_no  VARCHAR(255) NULL,
    email      VARCHAR(255) NULL,
    message    LONGTEXT NULL,
    status     TINYINT NULL DEFAULT 1,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE settings (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    meta_tag   VARCHAR(100) NULL,
    meta_key   VARCHAR(100) NULL,
    meta_value LONGTEXT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

-- ---------------------------------------------------------------------
-- Blog
-- ---------------------------------------------------------------------
CREATE TABLE posts (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    title      VARCHAR(256) NOT NULL,
    subtitle   VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    body       LONGTEXT NOT NULL,
    status     TINYINT NULL,
    posted_by  BIGINT NULL,
    image      VARCHAR(500) NULL,
    featured   TINYINT NULL,
    `like`     INT NULL,
    dislike    INT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE tags (
    id         BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(255) NOT NULL,
    status     TINYINT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE category_post (
    category_id BIGINT NOT NULL,
    post_id     BIGINT NOT NULL,
    created_at  DATETIME NULL,
    updated_at  DATETIME NULL,
    CONSTRAINT category_post_category_id_foreign FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT category_post_post_id_foreign FOREIGN KEY (post_id) REFERENCES posts (id)
);

CREATE TABLE post_tag (
    post_id    BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    CONSTRAINT post_tag_post_id_foreign FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT post_tag_tag_id_foreign FOREIGN KEY (tag_id) REFERENCES tags (id)
);

-- ---------------------------------------------------------------------
-- Artist Commission Portal (custom painting orders)
-- Kept separate from the course "orders" table above.
-- ---------------------------------------------------------------------
CREATE TABLE commission_orders (
    order_id               VARCHAR(50)   NOT NULL PRIMARY KEY,
    user_id                BIGINT         NULL,
    customer_name          VARCHAR(255)  NOT NULL,
    customer_email         VARCHAR(255)  NOT NULL,
    customer_phone         VARCHAR(50)   NULL,
    fulfillment_type       VARCHAR(50)   NOT NULL CHECK (fulfillment_type IN ('DIGITAL_ONLY', 'PHYSICAL_PRINT', 'ORIGINAL_PAINTING')),
    special_instructions   LONGTEXT  NULL,
    uploaded_photo_url     VARCHAR(2083) NOT NULL,
    shipping_address_line1 VARCHAR(255)  NULL,
    shipping_city          VARCHAR(100)  NULL,
    shipping_postal_code   VARCHAR(20)   NULL,
    shipping_country       VARCHAR(100)  NULL,
    payment_method         VARCHAR(50)   NOT NULL CHECK (payment_method IN ('CREDIT_CARD', 'RAZORPAY_UPI', 'PAYPAL')),
    payment_provider_ref   VARCHAR(255)  NULL,
    payment_transaction_id VARCHAR(255)  NULL,
    order_status           VARCHAR(50)   NOT NULL DEFAULT 'PAYMENT_PENDING' CHECK (order_status IN ('PAYMENT_PENDING', 'QUEUED', 'PROOF_SENT', 'IN_PRODUCTION', 'SHIPPED', 'COMPLETED')),
    currency               VARCHAR(3)    NOT NULL DEFAULT 'USD',
    total_price            DECIMAL(10, 2) NOT NULL,
    created_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME      NULL
);
CREATE INDEX commission_orders_provider_ref_index ON commission_orders (payment_provider_ref);

CREATE TABLE commission_artwork_items (
    item_id        VARCHAR(50)  NOT NULL PRIMARY KEY,
    order_id       VARCHAR(50)  NOT NULL,
    artwork_theme  VARCHAR(50)  NOT NULL CHECK (artwork_theme IN ('PORTRAIT', 'LANDSCAPE', 'WATERCOLOR', 'CHARCOAL')),
    width_inches   INT           NOT NULL,
    height_inches  INT           NOT NULL,
    frame_material VARCHAR(100) NOT NULL DEFAULT 'NONE',
    has_matting    TINYINT(1)           NOT NULL DEFAULT 0,
    CONSTRAINT commission_artwork_items_order_id_foreign FOREIGN KEY (order_id) REFERENCES commission_orders (order_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- Student Creative Lifecycle: learn -> submit -> exhibit & sell
-- (reuses the existing courses / users / admins tables)
-- ---------------------------------------------------------------------
CREATE TABLE enrollments (
    id                  BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    course_id           BIGINT       NOT NULL,
    progress_percentage INT          NOT NULL DEFAULT 0 CHECK (progress_percentage BETWEEN 0 AND 100),
    status              VARCHAR(20) NOT NULL DEFAULT 'Active' CHECK (status IN ('Active', 'Completed')),
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    NULL,
    CONSTRAINT enrollments_user_course_unique UNIQUE (user_id, course_id),
    CONSTRAINT enrollments_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION,
    CONSTRAINT enrollments_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE NO ACTION
);
CREATE INDEX enrollments_user_id_index ON enrollments (user_id);
CREATE INDEX enrollments_course_id_index ON enrollments (course_id);
CREATE INDEX enrollments_status_index ON enrollments (status);

CREATE TABLE lesson_completions (
    id            BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    enrollment_id BIGINT    NOT NULL,
    lesson_id     BIGINT    NOT NULL,
    completed_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT lesson_completions_unique UNIQUE (enrollment_id, lesson_id),
    CONSTRAINT lesson_completions_enrollment_id_foreign FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE NO ACTION,
    CONSTRAINT lesson_completions_lesson_id_foreign FOREIGN KEY (lesson_id) REFERENCES course_module_lessons (id) ON DELETE NO ACTION
);
CREATE INDEX lesson_completions_enrollment_id_index ON lesson_completions (enrollment_id);
CREATE INDEX lesson_completions_lesson_id_index ON lesson_completions (lesson_id);

CREATE TABLE student_submissions (
    id                BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    course_id         BIGINT        NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       LONGTEXT NULL,
    file_url          VARCHAR(500) NOT NULL,
    admin_status      VARCHAR(20)  NOT NULL DEFAULT 'Pending Review' CHECK (admin_status IN ('Pending Review', 'Approved', 'Rejected')),
    reviewer_feedback LONGTEXT NULL,
    reviewed_by       BIGINT        NULL,
    reviewed_at       DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NULL,
    CONSTRAINT student_submissions_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION,
    CONSTRAINT student_submissions_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE NO ACTION,
    CONSTRAINT student_submissions_reviewed_by_foreign FOREIGN KEY (reviewed_by) REFERENCES admins (id) ON DELETE NO ACTION
);
CREATE INDEX student_submissions_user_id_index ON student_submissions (user_id);
CREATE INDEX student_submissions_course_id_index ON student_submissions (course_id);
CREATE INDEX student_submissions_admin_status_index ON student_submissions (admin_status);

CREATE TABLE portfolio_marketplace (
    id                 BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    submission_id      BIGINT         NOT NULL,
    user_id            BIGINT         NOT NULL,
    is_listed_for_sale TINYINT(1)            NOT NULL DEFAULT 0,
    sale_price         DECIMAL(10, 2) NULL CHECK (sale_price IS NULL OR sale_price >= 0),
    currency           VARCHAR(3)    NOT NULL DEFAULT 'INR',
    inventory_count    INT            NOT NULL DEFAULT 1 CHECK (inventory_count >= 0),
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NULL,
    CONSTRAINT portfolio_marketplace_submission_unique UNIQUE (submission_id),
    CONSTRAINT portfolio_marketplace_submission_id_foreign FOREIGN KEY (submission_id) REFERENCES student_submissions (id) ON DELETE NO ACTION,
    CONSTRAINT portfolio_marketplace_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION
);
CREATE INDEX portfolio_marketplace_submission_id_index ON portfolio_marketplace (submission_id);
CREATE INDEX portfolio_marketplace_user_id_index ON portfolio_marketplace (user_id);
CREATE INDEX portfolio_marketplace_listed_index ON portfolio_marketplace (is_listed_for_sale);

-- Course completion certificates issued through the student studio (verifiable by number).
CREATE TABLE course_certificates (
    id             BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    enrollment_id  BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    course_id      BIGINT       NOT NULL,
    certificate_no VARCHAR(40) NOT NULL,
    issued_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT course_certificates_enrollment_unique UNIQUE (enrollment_id),
    CONSTRAINT course_certificates_number_unique UNIQUE (certificate_no),
    CONSTRAINT course_certificates_enrollment_id_foreign FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE NO ACTION,
    CONSTRAINT course_certificates_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION,
    CONSTRAINT course_certificates_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE NO ACTION
);
CREATE INDEX course_certificates_user_id_index ON course_certificates (user_id);
CREATE INDEX course_certificates_course_id_index ON course_certificates (course_id);

-- ---------------------------------------------------------------------
-- 1-of-1 Original Art Marketplace (/marketplace)
-- Every painting is unique: stock is a single state machine
--   DRAFT -> PENDING_REVIEW -> AVAILABLE <-> RESERVED -> SOLD   (ARCHIVED = withdrawn)
-- final_price is pinned to base_price/discount by a CHECK constraint, so no row can
-- carry a price that disagrees with its own pricing inputs (works on SQL Server and H2).
-- A painting can sit in at most one cart: cart_reservations.painting_id is UNIQUE.
-- ---------------------------------------------------------------------
CREATE TABLE paintings (
    id                  BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    slug                VARCHAR(191)  NOT NULL,
    title               VARCHAR(255)  NOT NULL,
    description         LONGTEXT  NOT NULL,
    artist_notes        LONGTEXT  NULL,
    medium              VARCHAR(100)  NOT NULL,
    surface             VARCHAR(100)  NOT NULL,
    subject             VARCHAR(100)  NULL,
    style_tags          VARCHAR(255)  NULL,
    height_inches       DECIMAL(6, 2)  NOT NULL CHECK (height_inches > 0),
    width_inches        DECIMAL(6, 2)  NOT NULL CHECK (width_inches > 0),
    depth_inches        DECIMAL(6, 2)  NOT NULL DEFAULT 0,
    weight_kg           DECIMAL(6, 2)  NULL,
    year_created        INT            NOT NULL,
    is_framed           TINYINT(1)            NOT NULL DEFAULT 0,
    frame_details       VARCHAR(255)  NULL,
    is_signed           TINYINT(1)            NOT NULL DEFAULT 1,
    has_certificate     TINYINT(1)            NOT NULL DEFAULT 1,
    base_price          DECIMAL(12, 2) NOT NULL CHECK (base_price > 0),
    discount_percentage DECIMAL(5, 2)  NOT NULL DEFAULT 0 CHECK (discount_percentage >= 0 AND discount_percentage <= 90),
    final_price         DECIMAL(12, 2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'INR',
    stock_status        VARCHAR(30)   NOT NULL DEFAULT 'DRAFT' CHECK (stock_status IN ('DRAFT', 'PENDING_REVIEW', 'AVAILABLE', 'RESERVED', 'SOLD', 'ARCHIVED')),
    version             INT            NOT NULL DEFAULT 0,
    source              VARCHAR(20)   NOT NULL DEFAULT 'STUDIO' CHECK (source IN ('STUDIO', 'STUDENT')),
    artist_name         VARCHAR(255)  NOT NULL,
    artist_admin_id     BIGINT         NULL,
    artist_user_id      BIGINT         NULL,
    submission_id       BIGINT         NULL,
    is_featured         TINYINT(1)            NOT NULL DEFAULT 0,
    review_notes        LONGTEXT  NULL,
    reviewed_by         BIGINT         NULL,
    reviewed_at         DATETIME      NULL,
    published_at        DATETIME      NULL,
    sold_at             DATETIME      NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT paintings_slug_unique UNIQUE (slug),
    CONSTRAINT paintings_final_price_check CHECK (final_price = ROUND(base_price * (100 - discount_percentage) / 100, 2)),
    CONSTRAINT paintings_artist_admin_id_foreign FOREIGN KEY (artist_admin_id) REFERENCES admins (id) ON DELETE NO ACTION,
    CONSTRAINT paintings_artist_user_id_foreign FOREIGN KEY (artist_user_id) REFERENCES users (id) ON DELETE NO ACTION,
    CONSTRAINT paintings_submission_id_foreign FOREIGN KEY (submission_id) REFERENCES student_submissions (id) ON DELETE NO ACTION,
    CONSTRAINT paintings_reviewed_by_foreign FOREIGN KEY (reviewed_by) REFERENCES admins (id) ON DELETE NO ACTION
);
CREATE INDEX IX_Paintings_Status ON paintings (stock_status);
CREATE INDEX paintings_artist_admin_id_index ON paintings (artist_admin_id);
CREATE INDEX paintings_artist_user_id_index ON paintings (artist_user_id);
CREATE INDEX paintings_submission_id_index ON paintings (submission_id);

CREATE TABLE painting_images (
    id          BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    painting_id BIGINT         NOT NULL,
    image_url   VARCHAR(2083) NOT NULL,
    is_primary  TINYINT(1)            NOT NULL DEFAULT 0,
    sort_order  INT            NOT NULL DEFAULT 0,
    alt_text    VARCHAR(255)  NULL,
    CONSTRAINT painting_images_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE CASCADE
);
CREATE INDEX painting_images_painting_id_index ON painting_images (painting_id);

CREATE TABLE cart_reservations (
    id                 BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    session_or_user_id VARCHAR(255) NOT NULL,
    painting_id        BIGINT        NOT NULL,
    user_id            BIGINT        NULL,
    reserved_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at         DATETIME     NOT NULL,
    CONSTRAINT cart_reservations_painting_unique UNIQUE (painting_id),
    CONSTRAINT cart_reservations_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE CASCADE
);
CREATE INDEX IX_CartReservations_Expiry ON cart_reservations (expires_at);
CREATE INDEX cart_reservations_holder_index ON cart_reservations (session_or_user_id);

CREATE TABLE marketplace_orders (
    order_id              VARCHAR(50)   NOT NULL PRIMARY KEY,
    painting_id           BIGINT         NOT NULL,
    user_id               BIGINT         NULL,
    holder_key            VARCHAR(255)  NOT NULL,
    buyer_name            VARCHAR(255)  NOT NULL,
    buyer_email           VARCHAR(255)  NOT NULL,
    buyer_phone           VARCHAR(50)   NULL,
    address_line1         VARCHAR(255)  NOT NULL,
    address_line2         VARCHAR(255)  NULL,
    city                  VARCHAR(100)  NOT NULL,
    state                 VARCHAR(100)  NULL,
    postal_code           VARCHAR(20)   NOT NULL,
    country               VARCHAR(100)  NOT NULL,
    gateway               VARCHAR(20)   NOT NULL CHECK (gateway IN ('RAZORPAY', 'PAYPAL', 'TEST')),
    currency              VARCHAR(3)    NOT NULL,
    amount                DECIMAL(12, 2) NOT NULL,
    price_inr             DECIMAL(12, 2) NOT NULL,
    provider_ref          VARCHAR(255)  NULL,
    transaction_id        VARCHAR(255)  NULL,
    status                VARCHAR(30)   NOT NULL DEFAULT 'PAYMENT_PENDING' CHECK (status IN ('PAYMENT_PENDING', 'PAID', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'EXPIRED', 'REFUND_REQUIRED', 'REFUNDED')),
    courier               VARCHAR(100)  NULL,
    tracking_number       VARCHAR(100)  NULL,
    staff_notes           LONGTEXT  NULL,
    paid_at               DATETIME      NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NULL,
    CONSTRAINT marketplace_orders_painting_id_foreign FOREIGN KEY (painting_id) REFERENCES paintings (id) ON DELETE NO ACTION,
    CONSTRAINT marketplace_orders_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION
);
CREATE INDEX marketplace_orders_painting_id_index ON marketplace_orders (painting_id);
CREATE INDEX marketplace_orders_status_index ON marketplace_orders (status);
CREATE INDEX marketplace_orders_provider_ref_index ON marketplace_orders (provider_ref);

-- Catalogue view in the "Courses_Workshops" shape, built on the existing courses table.
CREATE VIEW courses_workshops AS
SELECT c.id, c.title, c.introduction AS description,
       (SELECT p.photo_name FROM course_photos p WHERE p.course_id = c.id ORDER BY p.id LIMIT 1) AS thumbnail_url,
       LOWER(ct.course_type_slug) AS type,
       (SELECT cp.price_inr FROM course_prices cp WHERE cp.course_id = c.id ORDER BY cp.id LIMIT 1) AS price,
       c.course_start_date AS schedule_date
FROM courses c JOIN course_types ct ON ct.id = c.course_type_id;
