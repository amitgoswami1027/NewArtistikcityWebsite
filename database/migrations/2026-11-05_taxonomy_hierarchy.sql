-- 2026-11-05: Tag & hierarchy manager (Admin > Catalog). Mediums, genres, skill levels and age groups can be nested
-- (parent_id, same table: e.g. Painting > Watercolour, Landscape > Seascape) and archived (archived_at): an archived
-- item disappears from the course filters and the editor's picklists but stays on the courses that use it, and can
-- be restored. Safe to run twice.

DROP PROCEDURE IF EXISTS ac_add_tax_cols;
DELIMITER //
CREATE PROCEDURE ac_add_tax_cols(IN t VARCHAR(64))
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = t AND column_name = 'parent_id') = 0 THEN
        SET @s := CONCAT('ALTER TABLE ', t, ' ADD COLUMN parent_id BIGINT NULL, ADD COLUMN archived_at DATETIME NULL, ADD INDEX ', t, '_parent_index (parent_id)');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END //
DELIMITER ;
CALL ac_add_tax_cols('mediums');
CALL ac_add_tax_cols('genres');
CALL ac_add_tax_cols('skills');
CALL ac_add_tax_cols('age_groups');
DROP PROCEDURE ac_add_tax_cols;
