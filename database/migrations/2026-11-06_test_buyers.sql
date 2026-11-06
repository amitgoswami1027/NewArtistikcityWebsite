-- Test-buyer accounts and test orders (simulated payments on the live site, for chosen accounts only).
-- users.test_buyer = 1: a super admin ticked "Test buyer" in Admin > People; that account sees "Simulate payment".
-- is_test = 1 on an order: paid by simulation; kept out of revenue, the ledger, payouts and the reconciler.
-- Safe to run twice.
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
CALL ac_add_col('users', 'test_buyer', 'TINYINT NOT NULL DEFAULT 0');
CALL ac_add_col('orders', 'is_test', 'TINYINT NOT NULL DEFAULT 0');
CALL ac_add_col('marketplace_orders', 'is_test', 'TINYINT NOT NULL DEFAULT 0');
CALL ac_add_col('shop_orders', 'is_test', 'TINYINT NOT NULL DEFAULT 0');
CALL ac_add_col('commission_orders', 'is_test', 'TINYINT NOT NULL DEFAULT 0');
DROP PROCEDURE ac_add_col;
-- orders simulated before this column existed
UPDATE marketplace_orders SET is_test = 1 WHERE gateway = 'TEST';
UPDATE shop_orders SET is_test = 1 WHERE gateway = 'TEST';
UPDATE orders SET is_test = 1 WHERE payment_id LIKE 'test\_%';
UPDATE commission_orders SET is_test = 1 WHERE payment_transaction_id LIKE 'TEST-%';
