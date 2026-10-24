-- =====================================================================
--  Enrollments: allow status 'Refunded' (set when an admin records a refund in Admin > Course orders,
--  so the course leaves the student's studio). Safe to run more than once.
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

-- drop the old CHECK (status IN ('Active', 'Completed')), whatever MySQL named it
CALL ac_exec_if((SELECT COUNT(*) FROM information_schema.check_constraints c JOIN information_schema.table_constraints t
        ON t.constraint_schema = c.constraint_schema AND t.constraint_name = c.constraint_name
    WHERE c.constraint_schema = DATABASE() AND t.table_name = 'enrollments' AND c.check_clause LIKE '%Completed%' AND c.check_clause NOT LIKE '%Refunded%'),
    (SELECT CONCAT('ALTER TABLE enrollments DROP CHECK `', c.constraint_name, '`') FROM information_schema.check_constraints c JOIN information_schema.table_constraints t
        ON t.constraint_schema = c.constraint_schema AND t.constraint_name = c.constraint_name
    WHERE c.constraint_schema = DATABASE() AND t.table_name = 'enrollments' AND c.check_clause LIKE '%Completed%' AND c.check_clause NOT LIKE '%Refunded%' LIMIT 1));
CALL ac_exec_if((SELECT COUNT(*) = 0 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'enrollments' AND constraint_name = 'enrollments_status_check'),
    'ALTER TABLE enrollments ADD CONSTRAINT enrollments_status_check CHECK (status IN (''Active'', ''Completed'', ''Refunded''))');

DROP PROCEDURE IF EXISTS ac_exec_if;
