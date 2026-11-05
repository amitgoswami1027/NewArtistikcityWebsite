-- 2026-11-05: generated PDFs in private (S3) storage, and room for full S3 / CDN image URLs.
-- Safe to run twice.

-- Every generated student PDF (portfolio reports, course certificates): where it is stored (the S3 object key,
-- e.g. private/documents/portfolios/12/20261105-101500-ab12cd34ef56.pdf) and who may open it.
CREATE TABLE IF NOT EXISTS student_documents (
    id             BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    kind           VARCHAR(20)   NOT NULL,              -- PORTFOLIO | CERTIFICATE
    course_id      BIGINT        NULL,
    certificate_no VARCHAR(40)   NULL,
    storage_key    VARCHAR(1024) NOT NULL,
    file_name      VARCHAR(255)  NOT NULL,
    content_type   VARCHAR(100)  NOT NULL DEFAULT 'application/pdf',
    size_bytes     BIGINT        NOT NULL DEFAULT 0,
    options        VARCHAR(255)  NULL,
    created_at     DATETIME      NOT NULL,
    updated_at     DATETIME      NOT NULL,
    INDEX student_documents_user_kind_index (user_id, kind, created_at),
    UNIQUE INDEX student_documents_certificate_no_unique (certificate_no),
    CONSTRAINT student_documents_user_id_foreign FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- image addresses can now be full https:// URLs of the S3 bucket or CDN (S3_PUBLIC_BASE_URL)
ALTER TABLE shop_order_items MODIFY image_url VARCHAR(2083) NULL;
ALTER TABLE student_submissions MODIFY file_url VARCHAR(2083) NOT NULL;
ALTER TABLE certificates MODIFY certificate_path VARCHAR(1024) NULL;
