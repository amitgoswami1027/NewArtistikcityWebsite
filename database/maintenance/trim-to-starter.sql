-- Trim the site down to a small starter set: keeps the records listed below and deletes everything else of the same
-- kind, together with whatever belongs to the deleted records (lessons, orders, enrollments, photos, carts, ...).
--
-- TAKE A BACKUP FIRST (on AWS: an RDS snapshot, see docs/AWS-DEPLOY.md "Trim the data"). This cannot be undone.
-- Staff logins (except the listed ones), settings, FAQs, categories, tags, mediums/genres/skills and prices are kept.
-- Files in storage / S3 that belonged to deleted records are not removed (they are only unused).
--
-- Change the lists to keep other records (comma separated ids, no spaces):
SET @keep_users     = '1,2';        -- students: Aarav Sharma (student@artistikcity.com), Diya Patel (diya@example.com)
SET @keep_courses   = '1,4';        -- Watercolour Landscapes for Beginners (course), Weekend Watercolour Florals Workshop
SET @keep_paintings = '1,2';        -- Scarlet Macaw Study, The Old Mill at Dawn
SET @keep_posts     = '1,2';        -- 5 Watercolour Mistakes Beginners Make, Choosing Your First Set of Brushes
SET @keep_admins    = '1,2,3';      -- admin@, vaishali@, moderator@ (the demo teachers 11-15 go)
SET @keep_each      = 2;            -- testimonials, newsletter subscribers, contact messages, commission requests: newest N

SET @old_fk = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. the main records
DELETE FROM users     WHERE NOT FIND_IN_SET(id, @keep_users);
DELETE FROM admins    WHERE NOT FIND_IN_SET(id, @keep_admins);
DELETE FROM courses   WHERE NOT FIND_IN_SET(id, @keep_courses);
DELETE FROM paintings WHERE NOT FIND_IN_SET(id, @keep_paintings);
DELETE FROM posts     WHERE NOT FIND_IN_SET(id, @keep_posts);

-- 2. orders and requests of anyone or anything that is gone (guest orders included)
DELETE FROM orders             WHERE user_id IS NULL OR NOT FIND_IN_SET(user_id, @keep_users) OR NOT FIND_IN_SET(course_id, @keep_courses);
DELETE FROM payment_logs       WHERE user_id IS NULL OR NOT FIND_IN_SET(user_id, @keep_users);
DELETE FROM shop_orders        WHERE user_id IS NULL OR NOT FIND_IN_SET(user_id, @keep_users);
DELETE FROM marketplace_orders WHERE user_id IS NULL OR NOT FIND_IN_SET(user_id, @keep_users) OR NOT FIND_IN_SET(painting_id, @keep_paintings);
DELETE FROM commission_orders  WHERE user_id IS NOT NULL AND NOT FIND_IN_SET(user_id, @keep_users);
DELETE FROM wishlist_items     WHERE (item_type = 'course' AND NOT FIND_IN_SET(item_id, @keep_courses))
                                  OR (item_type = 'painting' AND NOT FIND_IN_SET(item_id, @keep_paintings));

-- 3. keep only the newest few of the small lists

DROP PROCEDURE IF EXISTS ac_keep_newest;
DELIMITER //
CREATE PROCEDURE ac_keep_newest(IN tbl VARCHAR(64), IN pk VARCHAR(64), IN n INT)
BEGIN
    SET @s = CONCAT('DELETE FROM `', tbl, '` WHERE `', pk, '` NOT IN (SELECT `', pk, '` FROM (SELECT `', pk, '` FROM `', tbl,
                    '` ORDER BY `', pk, '` DESC LIMIT ', n, ') k)');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
END //
DELIMITER ;
CALL ac_keep_newest('testimonials', 'id', @keep_each);
CALL ac_keep_newest('newsletters', 'id', @keep_each);
CALL ac_keep_newest('contacts', 'id', @keep_each);
CALL ac_keep_newest('commission_orders', 'order_id', @keep_each);
DROP PROCEDURE ac_keep_newest;

-- 4. everything that pointed at a deleted row: rows that can't exist without it are deleted, optional links are cleared.
--    Follows every foreign key in the schema plus the id columns that have no foreign key, until nothing changes.
DROP TEMPORARY TABLE IF EXISTS ac_links;
CREATE TEMPORARY TABLE ac_links (tbl VARCHAR(64), col VARCHAR(64), parent VARCHAR(64), pcol VARCHAR(64));
INSERT INTO ac_links
SELECT k.TABLE_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE k
WHERE k.TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL;
INSERT INTO ac_links VALUES
    ('auth_events', 'user_id', 'users', 'id'), ('auth_otps', 'user_id', 'users', 'id'),
    ('cart_items', 'user_id', 'users', 'id'), ('cart_items', 'course_id', 'courses', 'id'),
    ('cart_reservations', 'user_id', 'users', 'id'), ('course_reviews', 'course_id', 'courses', 'id'),
    ('payment_logs', 'course_id', 'courses', 'id'), ('student_artworks', 'user_id', 'users', 'id'),
    ('student_documents', 'course_id', 'courses', 'id'), ('user_addresses', 'user_id', 'users', 'id'),
    ('artist_thread_messages', 'admin_id', 'admins', 'id'), ('free_courses', 'admin_id', 'admins', 'id'),
    ('home_artworks', 'admin_id', 'admins', 'id'), ('admin_audit', 'admin_id', 'admins', 'id');

DROP PROCEDURE IF EXISTS ac_clean_orphans;
DELIMITER //
CREATE PROCEDURE ac_clean_orphans()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE changed BIGINT DEFAULT 1;
    DECLARE rounds INT DEFAULT 0;
    DECLARE t, c, p, pc VARCHAR(64);
    DECLARE nullable VARCHAR(3);
    DECLARE cur CURSOR FOR
        SELECT l.tbl, l.col, l.parent, l.pcol, col.IS_NULLABLE
        FROM ac_links l
        JOIN information_schema.COLUMNS col
          ON col.TABLE_SCHEMA = DATABASE() AND col.TABLE_NAME = l.tbl AND col.COLUMN_NAME = l.col
        JOIN information_schema.TABLES tb
          ON tb.TABLE_SCHEMA = DATABASE() AND tb.TABLE_NAME = l.tbl AND tb.TABLE_TYPE = 'BASE TABLE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
    WHILE changed > 0 AND rounds < 20 DO
        SET changed = 0, rounds = rounds + 1, done = 0;
        OPEN cur;
        each_link: LOOP
            FETCH cur INTO t, c, p, pc, nullable;
            IF done THEN LEAVE each_link; END IF;
            IF nullable = 'YES' AND c NOT IN ('user_id', 'course_id', 'enrollment_id', 'order_id', 'painting_id', 'post_id',
                                             'lesson_id', 'module_id', 'task_id', 'cart_id', 'thread_id', 'session_id') THEN
                -- an optional link (reviewed_by, submission_id, artist_user_id, ...): clear it, keep the row
                SET @s = CONCAT('UPDATE `', t, '` x SET x.`', c, '` = NULL WHERE x.`', c, '` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `',
                                p, '` y WHERE y.`', pc, '` = x.`', c, '`)');
            ELSE
                SET @s = CONCAT('DELETE x FROM `', t, '` x WHERE x.`', c, '` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `',
                                p, '` y WHERE y.`', pc, '` = x.`', c, '`)');
            END IF;
            PREPARE st FROM @s; EXECUTE st; SET changed = changed + ROW_COUNT(); DEALLOCATE PREPARE st;
        END LOOP;
        CLOSE cur;
    END WHILE;
END //
DELIMITER ;
CALL ac_clean_orphans();
DROP PROCEDURE ac_clean_orphans;
DROP TEMPORARY TABLE ac_links;

SET FOREIGN_KEY_CHECKS = @old_fk;

-- what is left
SELECT 'students' AS what, COUNT(*) AS how_many FROM users
UNION ALL SELECT 'staff', COUNT(*) FROM admins
UNION ALL SELECT 'courses + workshops', COUNT(*) FROM courses
UNION ALL SELECT 'course lessons', COUNT(*) FROM course_module_lessons
UNION ALL SELECT 'course orders', COUNT(*) FROM orders
UNION ALL SELECT 'marketplace paintings', COUNT(*) FROM paintings
UNION ALL SELECT 'blog posts', COUNT(*) FROM posts
UNION ALL SELECT 'testimonials', COUNT(*) FROM testimonials
UNION ALL SELECT 'newsletter subscribers', COUNT(*) FROM newsletters
UNION ALL SELECT 'commission requests', COUNT(*) FROM commission_orders;
