-- =====================================================================
--  Student certificates - migration for an EXISTING SQL Server database
--  Run after 2026-09-27_student_lifecycle.sql
-- =====================================================================
IF OBJECT_ID('dbo.course_certificates', 'U') IS NULL
BEGIN
CREATE TABLE course_certificates (
    id             BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    enrollment_id  BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    course_id      BIGINT       NOT NULL,
    certificate_no NVARCHAR(40) NOT NULL,
    issued_at      DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT course_certificates_enrollment_unique UNIQUE (enrollment_id),
    CONSTRAINT course_certificates_number_unique UNIQUE (certificate_no),
    CONSTRAINT course_certificates_enrollment_id_foreign FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE NO ACTION,
    CONSTRAINT course_certificates_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION,
    CONSTRAINT course_certificates_course_id_foreign FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE NO ACTION
);
CREATE NONCLUSTERED INDEX course_certificates_user_id_index ON course_certificates (user_id);
CREATE NONCLUSTERED INDEX course_certificates_course_id_index ON course_certificates (course_id);
END;
GO
