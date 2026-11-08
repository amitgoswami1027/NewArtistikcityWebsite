-- Landing Page Management: give the landing tables the same text collation as the rest of the database.
--
-- Why: a database loaded from a dump keeps each table's own collation (e.g. utf8mb4_0900_ai_ci from a PC), while tables
-- created later by a migration get the server/database default (utf8mb4_unicode_ci on RDS). MySQL then refuses queries
-- that compare or UNION text from both kinds of table ("Illegal mix of collations"), e.g. the Landing Page Management
-- overview (campaigns from landing_pages / landing_coupons / orders) and its analytics (orders.coupon_code vs
-- landing_coupons.code).
--
-- What: takes the collation of the `orders` table as the reference and converts every landing_* table that differs to
-- it (CONVERT TO CHARACTER SET re-encodes the text columns; utf8mb4 -> utf8mb4, so no data changes). It also sets the
-- database default to the same collation, so tables created by future migrations match. Tables that already match are
-- left alone: on a database where everything already agrees (fresh installs, the local Windows setup) this does nothing.
-- Safe to run twice; no table is dropped and no row is changed.
DROP PROCEDURE IF EXISTS ac_align_landing_collation;
DELIMITER //
CREATE PROCEDURE ac_align_landing_collation()
BEGIN
    DECLARE ref_coll VARCHAR(64);
    DECLARE ref_cs VARCHAR(64);
    DECLARE t VARCHAR(64);
    DECLARE done INT DEFAULT 0;
    DECLARE cur CURSOR FOR
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name LIKE 'landing\_%'
          AND table_collation <> ref_coll;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    SELECT table_collation INTO ref_coll FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'orders';
    IF ref_coll IS NOT NULL THEN
        SELECT character_set_name INTO ref_cs FROM information_schema.collations WHERE collation_name = ref_coll;

        -- ALTER DATABASE can't be a prepared statement, so the usual utf8mb4 collations are spelled out
        CASE ref_coll
            WHEN 'utf8mb4_0900_ai_ci' THEN ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
            WHEN 'utf8mb4_unicode_ci' THEN ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
            WHEN 'utf8mb4_general_ci' THEN ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
            WHEN 'utf8mb4_unicode_520_ci' THEN ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_520_ci;
            WHEN 'utf8mb4_bin' THEN ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
            ELSE BEGIN END;
        END CASE;

        SET FOREIGN_KEY_CHECKS = 0;
        OPEN cur;
        read_loop: LOOP
            FETCH cur INTO t;
            IF done = 1 THEN LEAVE read_loop; END IF;
            SET @s := CONCAT('ALTER TABLE `', t, '` CONVERT TO CHARACTER SET ', ref_cs, ' COLLATE ', ref_coll);
            PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
        END LOOP;
        CLOSE cur;
        SET FOREIGN_KEY_CHECKS = 1;

        -- columns added to existing tables by the landing migrations take their table's collation, but make sure
        SET @c := (SELECT collation_name FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'utm_campaign');
        IF @c IS NOT NULL AND @c <> ref_coll THEN
            SET @s := CONCAT('ALTER TABLE orders CONVERT TO CHARACTER SET ', ref_cs, ' COLLATE ', ref_coll);
            PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
        END IF;
    END IF;
END //
DELIMITER ;

CALL ac_align_landing_collation();
DROP PROCEDURE IF EXISTS ac_align_landing_collation;
