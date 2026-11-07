-- Landing Page Management: site pages. A landing page can now also replace one of a short, fixed list of site pages
-- (How it works at /how-it-works, Commission step 1 at /commission/step-1) instead of selling a course.
-- kind = COURSE (as before, course_id set, address /course/{slug}) or SITE (course_id empty, site_route set).
-- Additive and safe to run twice: no table is recreated, no row is changed or deleted. Existing pages stay COURSE pages.
-- The showcase content itself (the three pages) is created by the application on its next start through the landing
-- module (LandingShowcase), never by this script.
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
DROP PROCEDURE IF EXISTS ac_add_index;
DELIMITER //
CREATE PROCEDURE ac_add_index(IN t VARCHAR(64), IN i VARCHAR(64), IN cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = t AND index_name = i) = 0 THEN
        SET @s := CONCAT('ALTER TABLE ', t, ' ADD INDEX ', i, ' (', cols, ')');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END //
DELIMITER ;

CALL ac_add_col('landing_pages', 'kind', 'VARCHAR(10) NOT NULL DEFAULT ''COURSE'' AFTER slug');
CALL ac_add_col('landing_pages', 'site_route', 'VARCHAR(40) NULL AFTER kind');
-- a site page has no course (the foreign key keeps checking the pages that do have one)
ALTER TABLE landing_pages MODIFY course_id BIGINT NULL;
CALL ac_add_index('landing_pages', 'landing_pages_site_index', 'site_route, status');

DROP PROCEDURE IF EXISTS ac_add_col;
DROP PROCEDURE IF EXISTS ac_add_index;
