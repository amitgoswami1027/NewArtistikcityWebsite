-- 2026-11-05: age groups become an admin-managed list (Admin > Catalog > Age groups). Safe to run twice.
-- A course picks one group; courses.age_group keeps the group's name (so every page that shows it is unchanged).

CREATE TABLE IF NOT EXISTS age_groups (
    id             BIGINT AUTO_INCREMENT NOT NULL PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    age_group_slug VARCHAR(100) NOT NULL,
    sort_order     INT NOT NULL DEFAULT 0,
    status         TINYINT NOT NULL DEFAULT 1,
    created_at     DATETIME NULL,
    updated_at     DATETIME NULL,
    UNIQUE INDEX age_groups_slug_unique (age_group_slug)
);

-- same character set and collation as courses.age_group (databases differ: utf8mb4_0900_ai_ci or utf8mb4_unicode_ci),
-- otherwise comparing the two fails with "Illegal mix of collations"
SELECT CONCAT('ALTER TABLE age_groups CONVERT TO CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME) INTO @ac_sql
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'age_group';
PREPARE ac_stmt FROM @ac_sql;
EXECUTE ac_stmt;
DEALLOCATE PREPARE ac_stmt;

-- the three groups the site menus used so far (only when the list is still empty)
INSERT INTO age_groups (name, age_group_slug, sort_order, status, created_at, updated_at)
SELECT * FROM (
    SELECT 'Sub-junior - 8 to 12 yrs' AS name, 'sub-junior' AS slug, 1 AS so, 1 AS st, '2026-11-05 00:00:00' AS c, '2026-11-05 00:00:00' AS u
    UNION ALL SELECT 'Junior - 12 to 15 yrs', 'junior', 2, 1, '2026-11-05 00:00:00', '2026-11-05 00:00:00'
    UNION ALL SELECT 'Adult - 16 yrs and above', 'adult', 3, 1, '2026-11-05 00:00:00', '2026-11-05 00:00:00'
) d
WHERE NOT EXISTS (SELECT 1 FROM age_groups);

-- courses typed their age by hand ("12+ years", "8-12", "Adults"): put each in the matching group by its first number.
-- Anything that doesn't fit stays as it was; pick the group in the course editor.
UPDATE courses c
JOIN (SELECT id,
             LOWER(age_group) AS a,
             CAST(REGEXP_SUBSTR(age_group, '[0-9]+') AS UNSIGNED) AS n
      FROM courses
      WHERE age_group IS NOT NULL AND age_group <> ''
        AND age_group NOT IN (SELECT name FROM age_groups)) x ON x.id = c.id
SET c.age_group = CASE
        WHEN x.a LIKE '%adult%' OR x.n >= 16 THEN (SELECT name FROM age_groups WHERE age_group_slug = 'adult')
        WHEN x.a LIKE '%kid%' OR x.a LIKE '%child%' OR (x.n BETWEEN 1 AND 11) THEN (SELECT name FROM age_groups WHERE age_group_slug = 'sub-junior')
        WHEN x.n BETWEEN 12 AND 15 THEN (SELECT name FROM age_groups WHERE age_group_slug = 'junior')
        ELSE c.age_group
    END;
