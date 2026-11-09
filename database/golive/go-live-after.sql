-- GO-LIVE-RESET last step (after schema.sql and golive-seed.sql): put the kept configuration back (a kept setting wins
-- over the starter value of the same key), drop the ac_keep_* tables and show what the database now holds.
INSERT INTO config_overrides SELECT * FROM ac_keep_config_overrides;
DELETE s FROM settings s JOIN ac_keep_settings k ON k.meta_tag = s.meta_tag AND k.meta_key = s.meta_key;
INSERT INTO settings (meta_tag, meta_key, meta_value, created_at, updated_at)
SELECT meta_tag, meta_key, meta_value, NOW(), NOW() FROM ac_keep_settings;
DROP TABLE ac_keep_config_overrides;
DROP TABLE ac_keep_settings;
SELECT (SELECT COUNT(*) FROM admins) AS staff, (SELECT GROUP_CONCAT(email) FROM admins) AS staff_login,
       (SELECT COUNT(*) FROM users) AS students, (SELECT GROUP_CONCAT(email) FROM users) AS student_login,
       (SELECT COUNT(*) FROM courses WHERE status = 1) AS live_courses, (SELECT COUNT(*) FROM course_module_lessons) AS lessons,
       (SELECT COUNT(*) FROM orders) AS orders, (SELECT COUNT(*) FROM paintings) AS paintings,
       (SELECT COUNT(*) FROM config_overrides) AS config_values, (SELECT COUNT(*) FROM settings) AS settings;
-- configuration kept (names only): payment, SMS and Google sign-in keys set in Admin > Configuration Panel
SELECT COALESCE(GROUP_CONCAT(config_key ORDER BY config_key SEPARATOR ', '), '(none: set the payment keys in Admin > System parameters > Configuration Panel)') AS configuration_kept FROM config_overrides;
-- which go-live request made this database (GO-LIVE-RESET / the pipeline run each request id once)
DELETE FROM settings WHERE meta_tag = 'system' AND meta_key = 'go_live_request';
INSERT INTO settings (meta_tag, meta_key, meta_value, created_at, updated_at)
SELECT 'system', 'go_live_request', @ga_request_id, NOW(), NOW() FROM DUAL WHERE COALESCE(@ga_request_id, '') <> '';
