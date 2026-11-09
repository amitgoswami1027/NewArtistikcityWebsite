-- GO-LIVE-RESET step 1 (before schema.sql): copy the configuration that survives the reset into ac_keep_* tables,
-- which schema.sql does not touch. Kept: config_overrides (payment / SMS / Google keys of the Configuration Panel,
-- encrypted) and settings (site texts, SEO, contact, social, menu, cookie banner, page texts, landing defaults, shop and
-- offer rules), minus what only made sense for testing:
--   the site-access password (unless @ga_keep_site_lock = 1), simulated Razorpay switches, simulated sign-in codes, the showcase marker (so the
--   showcase landing pages are made again for the new courses), the shop's simulate mode, and values that point at
--   uploaded files (those files are archived by the reset), and the demo seed's placeholder phone / misspelt e-mail.
DROP TABLE IF EXISTS ac_keep_config_overrides;
DROP TABLE IF EXISTS ac_keep_settings;
CREATE TABLE ac_keep_config_overrides AS SELECT * FROM config_overrides;
-- one row per key: the newest (the site reads the settings in id order, so the newest already wins)
CREATE TABLE ac_keep_settings AS SELECT s.meta_tag, s.meta_key, s.meta_value FROM settings s
 WHERE s.id = (SELECT MAX(s2.id) FROM settings s2 WHERE s2.meta_tag = s.meta_tag AND s2.meta_key = s.meta_key)
   AND NOT (s.meta_tag = 'contact' AND s.meta_key = 'phone' AND s.meta_value = '9876 543 201')
   AND NOT (s.meta_tag = 'contact' AND s.meta_key = 'email' AND s.meta_value = 'info@artistickcity.com')
   AND NOT (meta_tag = 'system' AND meta_key LIKE 'site\_lock\_%' AND COALESCE(@ga_keep_site_lock, 0) = 0)
   AND NOT (meta_tag = 'landing_pages' AND meta_key IN ('simulate_razorpay', 'simulate_razorpay_all', 'showcase_v1'))
   AND NOT (meta_tag = 'shop' AND meta_key = 'simulate_payments')
   AND NOT (meta_tag = 'room_preview')
   AND COALESCE(meta_value, '') NOT LIKE '%/storage/uploads/%'
   AND COALESCE(meta_value, '') NOT LIKE '%/storage/private/%';
-- simulated sign-in codes (shown on screen, for testing without SMS / mail) are switched off for the live site
UPDATE ac_keep_settings SET meta_value = JSON_SET(meta_value, '$.simulatePhone', CAST('false' AS JSON), '$.simulateEmail', CAST('false' AS JSON))
 WHERE meta_tag = 'landing_pages' AND meta_key = 'login' AND JSON_VALID(meta_value);
SELECT CONCAT('kept ', (SELECT COUNT(*) FROM ac_keep_config_overrides), ' configuration values and ',
              (SELECT COUNT(*) FROM ac_keep_settings), ' site settings') AS step_1;
