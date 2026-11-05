/* =====================================================================================================================
   ArtistikCity - course catalogue, filter taxonomy and security audit log
   SQL Server (2019+ / Azure SQL) reference DDL.

   The running site uses MySQL 8.4 (database/schema.sql + database/migrations/*). This file is the same model written
   for SQL Server, for a brownfield deployment that standardises on it. Names, columns and rules match the MySQL
   tables so the Java code (plain JDBC) needs only dialect changes: TOP/OFFSET-FETCH instead of LIMIT, DATETIME2(3),
   NVARCHAR, and a SEQUENCE-free IDENTITY.

   Contents
     1. Filter taxonomy: mediums, genres, skills, age_groups (nested with parent_id, archivable), course_types
     2. courses + course_genres (the MySQL table keeps genre ids as a CSV column; here it is a proper join table)
     3. audit_logs (append-only, hash-chained) + audit_chain_head, with the indices the Security log filters use
   ===================================================================================================================== */

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/* ---------------------------------------------------------------------------------------------------------------------
   1. Filter taxonomy (Admin > Catalog = Tag & Hierarchy manager)
   One table per filter category. parent_id nests items inside the same list (Painting > Watercolour,
   Landscape > Seascape). archived_at hides an item from the learner filters and the editor picklists without
   touching courses that already use it (restorable; deleting is only allowed when unused).
   --------------------------------------------------------------------------------------------------------------------- */
CREATE TABLE dbo.mediums (
    id           BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_mediums PRIMARY KEY,
    name         NVARCHAR(100)  NOT NULL,
    medium_slug  VARCHAR(120)   NOT NULL,
    photo        NVARCHAR(255)  NULL,
    parent_id    BIGINT         NULL CONSTRAINT fk_mediums_parent REFERENCES dbo.mediums (id),
    status       BIT            NOT NULL CONSTRAINT df_mediums_status DEFAULT (1),
    archived_at  DATETIME2(0)   NULL,
    created_at   DATETIME2(0)   NOT NULL CONSTRAINT df_mediums_created DEFAULT (SYSUTCDATETIME()),
    updated_at   DATETIME2(0)   NULL,
    CONSTRAINT uq_mediums_slug UNIQUE (medium_slug),
    CONSTRAINT ck_mediums_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_mediums_parent ON dbo.mediums (parent_id) WHERE parent_id IS NOT NULL;
/* the learner filter reads only live, unarchived items: a filtered index keeps that read tiny */
CREATE INDEX ix_mediums_live ON dbo.mediums (name) INCLUDE (medium_slug, parent_id) WHERE status = 1 AND archived_at IS NULL;
GO

CREATE TABLE dbo.genres (
    id           BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_genres PRIMARY KEY,
    name         NVARCHAR(100)  NOT NULL,
    genre_slug   VARCHAR(120)   NOT NULL,
    parent_id    BIGINT         NULL CONSTRAINT fk_genres_parent REFERENCES dbo.genres (id),
    status       BIT            NOT NULL CONSTRAINT df_genres_status DEFAULT (1),
    archived_at  DATETIME2(0)   NULL,
    created_at   DATETIME2(0)   NOT NULL CONSTRAINT df_genres_created DEFAULT (SYSUTCDATETIME()),
    updated_at   DATETIME2(0)   NULL,
    CONSTRAINT uq_genres_slug UNIQUE (genre_slug),
    CONSTRAINT ck_genres_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_genres_parent ON dbo.genres (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX ix_genres_live ON dbo.genres (name) INCLUDE (genre_slug, parent_id) WHERE status = 1 AND archived_at IS NULL;
GO

CREATE TABLE dbo.skills (
    id           BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_skills PRIMARY KEY,
    name         NVARCHAR(100)  NOT NULL,
    skill_slug   VARCHAR(120)   NOT NULL,
    parent_id    BIGINT         NULL CONSTRAINT fk_skills_parent REFERENCES dbo.skills (id),
    status       BIT            NOT NULL CONSTRAINT df_skills_status DEFAULT (1),
    archived_at  DATETIME2(0)   NULL,
    created_at   DATETIME2(0)   NOT NULL CONSTRAINT df_skills_created DEFAULT (SYSUTCDATETIME()),
    updated_at   DATETIME2(0)   NULL,
    CONSTRAINT uq_skills_slug UNIQUE (skill_slug)
);
GO

CREATE TABLE dbo.age_groups (
    id              BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_age_groups PRIMARY KEY,
    name            NVARCHAR(100)  NOT NULL,          -- courses store this name (renaming a group renames it on courses)
    age_group_slug  VARCHAR(120)   NOT NULL,
    sort_order      INT            NOT NULL CONSTRAINT df_age_groups_sort DEFAULT (0),
    parent_id       BIGINT         NULL CONSTRAINT fk_age_groups_parent REFERENCES dbo.age_groups (id),
    status          BIT            NOT NULL CONSTRAINT df_age_groups_status DEFAULT (1),
    archived_at     DATETIME2(0)   NULL,
    created_at      DATETIME2(0)   NOT NULL CONSTRAINT df_age_groups_created DEFAULT (SYSUTCDATETIME()),
    updated_at      DATETIME2(0)   NULL,
    CONSTRAINT uq_age_groups_name UNIQUE (name),
    CONSTRAINT uq_age_groups_slug UNIQUE (age_group_slug)
);
GO

CREATE TABLE dbo.course_types (
    id                BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_course_types PRIMARY KEY,
    name              NVARCHAR(100) NOT NULL,         -- 1 = Course, 2 = Workshop (the site's menus depend on both)
    course_type_slug  VARCHAR(120)  NOT NULL CONSTRAINT uq_course_types_slug UNIQUE,
    status            BIT           NOT NULL CONSTRAINT df_course_types_status DEFAULT (1)
);
GO

/* ---------------------------------------------------------------------------------------------------------------------
   2. Courses
   --------------------------------------------------------------------------------------------------------------------- */
CREATE TABLE dbo.courses (
    id                 BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_courses PRIMARY KEY,
    title              NVARCHAR(255)  NOT NULL,
    slug               VARCHAR(255)   NOT NULL CONSTRAINT uq_courses_slug UNIQUE,
    sub_title          NVARCHAR(500)  NULL,
    introduction       NVARCHAR(MAX)  NULL,
    course_type_id     BIGINT         NOT NULL CONSTRAINT fk_courses_type REFERENCES dbo.course_types (id),
    medium_id          BIGINT         NULL CONSTRAINT fk_courses_medium REFERENCES dbo.mediums (id),
    skill_id           BIGINT         NULL CONSTRAINT fk_courses_skill REFERENCES dbo.skills (id),
    age_group          NVARCHAR(100)  NULL,           -- name of an age_groups row (exact match drives the Age filter)
    admin_id           BIGINT         NULL,           -- the instructor (admins.id); instructors only edit their own
    course_start_date  DATE           NULL,
    seat_limit         INT            NULL CONSTRAINT ck_courses_seat_limit CHECK (seat_limit IS NULL OR seat_limit > 0),
    status             BIT            NOT NULL CONSTRAINT df_courses_status DEFAULT (0),
    cancelled_at       DATETIME2(0)   NULL,
    cancel_reason      NVARCHAR(500)  NULL,
    intro_video_url    NVARCHAR(1000) NULL,
    created_at         DATETIME2(0)   NOT NULL CONSTRAINT df_courses_created DEFAULT (SYSUTCDATETIME()),
    updated_at         DATETIME2(0)   NULL
);
/* the catalogue query: live courses from today on, newest first, covering the card fields */
CREATE INDEX ix_courses_catalogue ON dbo.courses (status, course_start_date)
    INCLUDE (title, slug, course_type_id, medium_id, skill_id, age_group, seat_limit);
CREATE INDEX ix_courses_medium ON dbo.courses (medium_id) INCLUDE (status);
CREATE INDEX ix_courses_instructor ON dbo.courses (admin_id);
GO

/* fast tagging: one row per course and genre (the editor's TagPicker writes this list) */
CREATE TABLE dbo.course_genres (
    course_id  BIGINT NOT NULL CONSTRAINT fk_course_genres_course REFERENCES dbo.courses (id) ON DELETE CASCADE,
    genre_id   BIGINT NOT NULL CONSTRAINT fk_course_genres_genre REFERENCES dbo.genres (id),
    CONSTRAINT pk_course_genres PRIMARY KEY (course_id, genre_id)
);
CREATE INDEX ix_course_genres_genre ON dbo.course_genres (genre_id, course_id);   -- "courses in Landscape" + usage counts
GO

/* ---------------------------------------------------------------------------------------------------------------------
   3. Security audit log (Admin > Setup > Security log)
   Append-only and tamper-evident: each row stores the SHA-256 of the previous row (prev_hash) and of its own fields
   (row_hash), computed by the application (security/AuditLog.java). Rows are appended one at a time under an
   UPDLOCK on audit_chain_head so the chain never forks. The app login has INSERT/SELECT only on this table, and a
   trigger refuses UPDATE and DELETE for everyone else, so a change can only come from a DBA - and still breaks the
   chain, which "Verify integrity" finds.
   --------------------------------------------------------------------------------------------------------------------- */
CREATE TABLE dbo.audit_logs (
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    occurred_at    DATETIME2(3)   NOT NULL,           -- the "timestamp" column of the brief (UTC, millisecond precision)
    actor_type     VARCHAR(20)    NOT NULL,           -- admin | student | system | anonymous
    actor_id       BIGINT         NULL,               -- the "user_id" of the brief
    actor_email    NVARCHAR(255)  NULL,
    actor_role     VARCHAR(30)    NULL,               -- admin | teacher | moderator | student | system
    action_type    VARCHAR(10)    NOT NULL,
    action         NVARCHAR(120)  NOT NULL,           -- e.g. 'course updated', 'admin_login', 'session_revoked'
    resource_type  VARCHAR(50)    NULL,               -- e.g. 'course'
    resource_id    VARCHAR(64)    NULL,               -- e.g. '110'  ->  "Course #110"
    summary        NVARCHAR(1000) NULL,
    before_json    NVARCHAR(MAX)  NULL,               -- snapshot before an UPDATE (secrets masked); text so the hash is stable
    after_json     NVARCHAR(MAX)  NULL,
    ip             VARCHAR(45)    NULL,               -- IPv4 or IPv6
    country        VARCHAR(8)     NULL,               -- CloudFront-Viewer-Country
    city           NVARCHAR(100)  NULL,               -- CloudFront-Viewer-City
    user_agent     NVARCHAR(500)  NULL,
    http_method    VARCHAR(10)    NULL,
    path           NVARCHAR(500)  NULL,
    outcome        VARCHAR(10)    NOT NULL,           -- OK | FAIL | DENIED
    prev_hash      CHAR(64)       NOT NULL,
    row_hash       CHAR(64)       NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY CLUSTERED (id),   -- ever-increasing key: appends never split pages
    CONSTRAINT ck_audit_logs_type CHECK (action_type IN ('CREATE', 'UPDATE', 'DELETE', 'AUTH', 'EXPORT', 'DENIED')),
    CONSTRAINT ck_audit_logs_outcome CHECK (outcome IN ('OK', 'FAIL', 'DENIED')),
    CONSTRAINT ck_audit_logs_json CHECK ((before_json IS NULL OR ISJSON(before_json) = 1) AND (after_json IS NULL OR ISJSON(after_json) = 1))
);
/* the Security log's filters, newest first: time window; one user's activity; one action type; one record's history */
CREATE INDEX ix_audit_logs_time        ON dbo.audit_logs (occurred_at DESC) INCLUDE (actor_id, action_type, resource_type, outcome);
CREATE INDEX ix_audit_logs_user_time   ON dbo.audit_logs (actor_id, occurred_at DESC) INCLUDE (action_type, outcome) WHERE actor_id IS NOT NULL;
CREATE INDEX ix_audit_logs_type_time   ON dbo.audit_logs (action_type, occurred_at DESC) INCLUDE (actor_id, resource_type);
CREATE INDEX ix_audit_logs_resource    ON dbo.audit_logs (resource_type, resource_id, occurred_at DESC);
/* the revoke rule ("5 refusals in 10 minutes"): a narrow filtered index over DENIED rows only */
CREATE INDEX ix_audit_logs_denied      ON dbo.audit_logs (actor_type, actor_id, occurred_at) WHERE action_type = 'DENIED';
GO

CREATE TABLE dbo.audit_chain_head (
    id          INT          NOT NULL CONSTRAINT pk_audit_chain_head PRIMARY KEY,
    last_id     BIGINT       NOT NULL,
    last_hash   CHAR(64)     NOT NULL,
    updated_at  DATETIME2(3) NOT NULL
);
INSERT INTO dbo.audit_chain_head (id, last_id, last_hash, updated_at)
VALUES (1, 0, REPLICATE('0', 64), SYSUTCDATETIME());
GO

CREATE TRIGGER dbo.tr_audit_logs_append_only ON dbo.audit_logs
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 51000, 'audit_logs is append-only: rows cannot be changed or deleted.', 1;
END;
GO

/* Appending one entry (what AuditLog.write does, in T-SQL):

   BEGIN TRAN;
     DECLARE @prev CHAR(64) = (SELECT last_hash FROM dbo.audit_chain_head WITH (UPDLOCK, HOLDLOCK) WHERE id = 1);
     -- the app computes @hash = SHA-256(@prev + fields) and inserts the row with prev_hash = @prev, row_hash = @hash
     INSERT INTO dbo.audit_logs (...) OUTPUT inserted.id INTO @ids VALUES (...);
     UPDATE dbo.audit_chain_head SET last_id = (SELECT id FROM @ids), last_hash = @hash, updated_at = SYSUTCDATETIME() WHERE id = 1;
   COMMIT;

   Security log page, 50 rows of one user's UPDATE actions in the last 24 hours (uses ix_audit_logs_user_time):

   SELECT id, occurred_at, actor_id, actor_email, actor_role, action_type, action, resource_type, resource_id, ip, country, city
   FROM dbo.audit_logs
   WHERE actor_id = @user AND action_type = 'UPDATE' AND occurred_at >= DATEADD(HOUR, -24, SYSUTCDATETIME())
   ORDER BY occurred_at DESC, id DESC
   OFFSET 0 ROWS FETCH NEXT 50 ROWS ONLY;

   Keeping the table small: partition by month on occurred_at, or move rows older than the retention period (e.g. 400
   days) to cold storage with SWITCH PARTITION - never with DELETE, which the trigger refuses. */
