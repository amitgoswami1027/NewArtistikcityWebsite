-- =====================================================================
--  Course checkout: keep the payment provider's reference (Razorpay order id / PayPal order id) on the
--  order, so a payment is only accepted for the order it was created for. Safe to run more than once.
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

CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'gateway_ref'),
    'ALTER TABLE orders ADD COLUMN gateway_ref VARCHAR(255) NULL AFTER payment_id');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'orders_gateway_ref_index'),
    'CREATE INDEX orders_gateway_ref_index ON orders (gateway_ref)');
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'orders_user_course_index'),
    'CREATE INDEX orders_user_course_index ON orders (user_id, course_id)');

DROP PROCEDURE IF EXISTS ac_exec_if;
