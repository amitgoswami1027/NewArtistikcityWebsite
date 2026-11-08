-- Landing pages: Login & verification. landing_checkout.verify_with = how a page's buyers prove who they are when
-- they press Pay (NULL = the default in Admin › Landing Page Management › Login & verification; PHONE, EMAIL, EITHER
-- or SIGNIN). The settings themselves live in settings (tag landing_pages, keys login and simulate_razorpay_all).
-- Additive and safe to run twice; the app keeps working without it (the page then follows the default).
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
CALL ac_add_col('landing_checkout', 'verify_with', 'VARCHAR(10) NULL');
DROP PROCEDURE ac_add_col;
