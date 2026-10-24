-- =====================================================================
--  Admin console overhaul. Safe to run more than once.
--   * course_modules / course_module_lessons.sort_order : curriculum order set in the admin
--   * posts.meta_title / meta_description               : blog SEO fields (posts.subtitle widened to 500)
--   * newsletters.unsubscribed_at, source               : subscriber state; newsletter_campaigns : e-mail blasts
--   * faqs                                               : the public FAQ page, managed in the admin
--   * contacts.read_at                                   : contact inbox state (status 1 = new, 2 = read, 3 = archived)
--   * admin_audit                                        : who changed what (orders, deletes, sends)
--   * settings rows for SEO and contact details
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

CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'course_modules' AND column_name = 'sort_order'),
    'ALTER TABLE course_modules ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER module_duration');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'course_module_lessons' AND column_name = 'sort_order'),
    'ALTER TABLE course_module_lessons ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER lesson_pdf');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'posts' AND column_name = 'meta_title'),
    'ALTER TABLE posts ADD COLUMN meta_title VARCHAR(255) NULL AFTER image, ADD COLUMN meta_description VARCHAR(500) NULL AFTER meta_title');
CALL ac_exec_if((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'posts' AND column_name = 'subtitle' AND character_maximum_length < 500),
    'ALTER TABLE posts MODIFY subtitle VARCHAR(500) NOT NULL DEFAULT ''''');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'newsletters' AND column_name = 'unsubscribed_at'),
    'ALTER TABLE newsletters ADD COLUMN source VARCHAR(50) NULL AFTER email, ADD COLUMN unsubscribed_at DATETIME NULL AFTER source');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'contacts' AND column_name = 'read_at'),
    'ALTER TABLE contacts ADD COLUMN read_at DATETIME NULL AFTER status');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'newsletters' AND index_name = 'newsletters_email_index'),
    'CREATE INDEX newsletters_email_index ON newsletters (email)');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'orders_created_index'),
    'CREATE INDEX orders_created_index ON orders (created_at)');

-- existing curriculum keeps its current order
UPDATE course_modules SET sort_order = id WHERE sort_order = 0;
UPDATE course_module_lessons SET sort_order = id WHERE sort_order = 0;

CREATE TABLE IF NOT EXISTS newsletter_campaigns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject VARCHAR(255) NOT NULL,
    preheader VARCHAR(255) NULL,
    body LONGTEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    recipients INT NOT NULL DEFAULT 0,
    sent_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    sent_at DATETIME NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE IF NOT EXISTS faqs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(100) NULL,
    question VARCHAR(500) NOT NULL,
    answer LONGTEXT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE IF NOT EXISTS admin_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NULL,
    entity VARCHAR(50) NOT NULL,
    entity_id BIGINT NULL,
    action VARCHAR(50) NOT NULL,
    details VARCHAR(1000) NULL,
    created_at DATETIME NULL,
    INDEX admin_audit_entity_index (entity, entity_id)
);

-- starter FAQ (edit or replace in Admin > FAQ); only when the table is empty
INSERT INTO faqs (category, question, answer, sort_order, status, created_at, updated_at)
SELECT * FROM (
    SELECT 'Classes' AS category, 'How do the live classes work?' AS question,
        '<p>Each course runs as a series of live online sessions with your instructor. After you enrol, the schedule, lessons and tasks appear under <strong>My studio &gt; My courses</strong>.</p>' AS answer, 1 AS sort_order, 1 AS status, NOW() AS created_at, NOW() AS updated_at
    UNION ALL SELECT 'Classes', 'What do I need to start?',
        '<p>Every course page lists the materials you need. A phone, tablet or computer with a good internet connection is enough to join the sessions.</p>', 2, 1, NOW(), NOW()
    UNION ALL SELECT 'Payments', 'Which payment methods can I use?',
        '<p>In India you can pay with UPI, cards and NetBanking. From other countries you can pay with PayPal.</p>', 3, 1, NOW(), NOW()
    UNION ALL SELECT 'Certificates', 'Will I get a certificate?',
        '<p>Yes. When you complete the lessons and your final work is approved, a verifiable certificate is added to <strong>My studio &gt; Certificates &amp; reports</strong>.</p>', 4, 1, NOW(), NOW()
    UNION ALL SELECT 'Marketplace', 'Can I sell my artwork on ArtistikCity?',
        '<p>Approved student work can be listed as a 1-of-1 original in the marketplace from your portfolio.</p>', 5, 1, NOW(), NOW()
) seed
WHERE NOT EXISTS (SELECT 1 FROM faqs);

-- SEO, contact and social settings (contact values are the ones the contact page showed before; correct them in Admin > Site settings)
INSERT INTO settings (meta_tag, meta_key, meta_value, created_at, updated_at)
SELECT s.meta_tag, s.meta_key, s.meta_value, NOW(), NOW() FROM (
    SELECT 'seo' AS meta_tag, 'site_title' AS meta_key, 'ArtistikCity - Live online art classes' AS meta_value
    UNION ALL SELECT 'seo', 'meta_description', 'Learn to draw and paint online with live classes, personal feedback and certificates. Courses and workshops in watercolour, acrylic, sketching and more.'
    UNION ALL SELECT 'seo', 'meta_keywords', 'online art classes, painting course, drawing course, watercolour, acrylic'
    UNION ALL SELECT 'seo', 'og_image', ''
    UNION ALL SELECT 'contact', 'email', 'info@artistickcity.com'
    UNION ALL SELECT 'contact', 'phone', '9876 543 201'
    UNION ALL SELECT 'contact', 'address', '505, Agate, MyHomes Jewel,\nMadinguda, Hyderabad - 540017\nTelanganma, India.'
    UNION ALL SELECT 'social', 'facebook', 'https://www.facebook.com/StudioVaishaliArts/'
    UNION ALL SELECT 'social', 'instagram', 'https://www.instagram.com/artistikcity/'
    UNION ALL SELECT 'social', 'youtube', 'https://www.youtube.com/@artistikcityYT'
    UNION ALL SELECT 'social', 'linkedin', 'https://in.linkedin.com/in/vaishali-jetly-goswami-4b5874233'
) s
WHERE NOT EXISTS (SELECT 1 FROM settings x WHERE x.meta_tag = s.meta_tag AND x.meta_key = s.meta_key);

DROP PROCEDURE IF EXISTS ac_exec_if;
