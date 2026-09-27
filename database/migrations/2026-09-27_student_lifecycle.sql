-- =====================================================================
--  Student Creative Lifecycle - migration for an EXISTING SQL Server database
--  (fresh installs get these objects from database/schema.sql).
--  Run with:  sqlcmd -S localhost -d artistikcity -i database/migrations/2026-09-27_student_lifecycle.sql
--  Foreign keys use ON DELETE NO ACTION; every FK and status column has a non-clustered index.
-- =====================================================================
SET XACT_ABORT ON;

-- 1) allow the new "moderator" admin persona
DECLARE @ck NVARCHAR(256);
SELECT @ck = cc.name FROM sys.check_constraints cc
JOIN sys.columns col ON col.object_id = cc.parent_object_id AND col.column_id = cc.parent_column_id
WHERE cc.parent_object_id = OBJECT_ID('dbo.admins') AND col.name = 'admin_type';
IF @ck IS NOT NULL EXEC('ALTER TABLE dbo.admins DROP CONSTRAINT [' + @ck + ']');
ALTER TABLE dbo.admins ADD CONSTRAINT admins_admin_type_check CHECK (admin_type IN ('admin', 'teacher', 'moderator'));
GO

-- 2) lifecycle tables
IF OBJECT_ID('dbo.enrollments', 'U') IS NULL
BEGIN
CREATE TABLE enrollments (
    id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    course_id           BIGINT       NOT NULL,
    progress_percentage INT          NOT NULL DEFAULT 0 CHECK (progress_percentage BETWEEN 0 AND 100),
    status              NVARCHAR(20) NOT NULL DEFAULT 'Active' CHECK (status IN ('Active', 'Completed')),
    created_at          DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME2    NULL,
    CONSTRAINT enrollments_user_course_unique UNIQUE (user_id, course_id),
    CONSTRAINT enrollments_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION,
    CONSTRAINT enrollments_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE NO ACTION
);
CREATE NONCLUSTERED INDEX enrollments_user_id_index ON enrollments (user_id);
CREATE NONCLUSTERED INDEX enrollments_course_id_index ON enrollments (course_id);
CREATE NONCLUSTERED INDEX enrollments_status_index ON enrollments (status);
END;

IF OBJECT_ID('dbo.lesson_completions', 'U') IS NULL
BEGIN
CREATE TABLE lesson_completions (
    id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    enrollment_id BIGINT    NOT NULL,
    lesson_id     BIGINT    NOT NULL,
    completed_at  DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT lesson_completions_unique UNIQUE (enrollment_id, lesson_id),
    CONSTRAINT lesson_completions_enrollment_id_foreign FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE NO ACTION,
    CONSTRAINT lesson_completions_lesson_id_foreign FOREIGN KEY (lesson_id) REFERENCES course_module_lessons (id) ON DELETE NO ACTION
);
CREATE NONCLUSTERED INDEX lesson_completions_enrollment_id_index ON lesson_completions (enrollment_id);
CREATE NONCLUSTERED INDEX lesson_completions_lesson_id_index ON lesson_completions (lesson_id);
END;

IF OBJECT_ID('dbo.student_submissions', 'U') IS NULL
BEGIN
CREATE TABLE student_submissions (
    id                BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    course_id         BIGINT        NOT NULL,
    title             NVARCHAR(200) NOT NULL,
    description       NVARCHAR(MAX) NULL,
    file_url          NVARCHAR(500) NOT NULL,
    admin_status      NVARCHAR(20)  NOT NULL DEFAULT 'Pending Review' CHECK (admin_status IN ('Pending Review', 'Approved', 'Rejected')),
    reviewer_feedback NVARCHAR(MAX) NULL,
    reviewed_by       BIGINT        NULL,
    reviewed_at       DATETIME2     NULL,
    created_at        DATETIME2     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME2     NULL,
    CONSTRAINT student_submissions_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION,
    CONSTRAINT student_submissions_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE NO ACTION,
    CONSTRAINT student_submissions_reviewed_by_foreign FOREIGN KEY (reviewed_by) REFERENCES admins (id) ON DELETE NO ACTION
);
CREATE NONCLUSTERED INDEX student_submissions_user_id_index ON student_submissions (user_id);
CREATE NONCLUSTERED INDEX student_submissions_course_id_index ON student_submissions (course_id);
CREATE NONCLUSTERED INDEX student_submissions_admin_status_index ON student_submissions (admin_status);
END;

IF OBJECT_ID('dbo.portfolio_marketplace', 'U') IS NULL
BEGIN
CREATE TABLE portfolio_marketplace (
    id                 BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    submission_id      BIGINT         NOT NULL,
    user_id            BIGINT         NOT NULL,
    is_listed_for_sale BIT            NOT NULL DEFAULT 0,
    sale_price         DECIMAL(10, 2) NULL CHECK (sale_price IS NULL OR sale_price >= 0),
    currency           NVARCHAR(3)    NOT NULL DEFAULT 'INR',
    inventory_count    INT            NOT NULL DEFAULT 1 CHECK (inventory_count >= 0),
    created_at         DATETIME2      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME2      NULL,
    CONSTRAINT portfolio_marketplace_submission_unique UNIQUE (submission_id),
    CONSTRAINT portfolio_marketplace_submission_id_foreign FOREIGN KEY (submission_id) REFERENCES student_submissions (id) ON DELETE NO ACTION,
    CONSTRAINT portfolio_marketplace_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION
);
CREATE NONCLUSTERED INDEX portfolio_marketplace_submission_id_index ON portfolio_marketplace (submission_id);
CREATE NONCLUSTERED INDEX portfolio_marketplace_user_id_index ON portfolio_marketplace (user_id);
CREATE NONCLUSTERED INDEX portfolio_marketplace_listed_index ON portfolio_marketplace (is_listed_for_sale);
END;

GO
-- Catalogue view in the "Courses_Workshops" shape, built on the existing courses table.
CREATE OR ALTER VIEW courses_workshops AS
SELECT c.id, c.title, c.introduction AS description,
       (SELECT TOP 1 p.photo_name FROM course_photos p WHERE p.course_id = c.id ORDER BY p.id) AS thumbnail_url,
       LOWER(ct.course_type_slug) AS type,
       (SELECT TOP 1 cp.price_inr FROM course_prices cp WHERE cp.course_id = c.id ORDER BY cp.id) AS price,
       c.course_start_date AS schedule_date
FROM courses c JOIN course_types ct ON ct.id = c.course_type_id;

GO

-- 3) backfill enrollments from paid course orders
INSERT INTO enrollments (user_id, course_id, progress_percentage, status, created_at, updated_at)
SELECT DISTINCT o.user_id, o.course_id, 0, 'Active', SYSDATETIME(), SYSDATETIME()
FROM orders o
WHERE o.payment_status = 1
  AND NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.user_id = o.user_id AND e.course_id = o.course_id);
GO
