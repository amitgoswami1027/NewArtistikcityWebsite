-- ============================================================================================
--  ArtistikCity: clear all site data (WIPE-SITE-DATA.bat runs this after taking a full backup)
--
--  KEPT (what the site needs to run and what staff set up):
--    admins (staff and instructor logins), settings, faqs, mediums, genres, skills, course_types,
--    categories, tags (blog taxonomy), shop_price_options (print price list), admin_audit, schema_migrations
--  (courses_workshops is a view over courses, so it empties with them)
--
--  CLEARED: everything else - students and their logins, courses, workshops, free lessons, orders,
--  enrollments, coursework, certificates, paintings, carts, shop orders, coupons, commissions,
--  messages, wish lists, blog posts, testimonials, home gallery, contact messages, newsletter.
-- ============================================================================================
SET FOREIGN_KEY_CHECKS = 0;

-- students, sign-in and sessions
TRUNCATE TABLE webauthn_credentials;
TRUNCATE TABLE linked_accounts;
TRUNCATE TABLE auth_sessions;
TRUNCATE TABLE auth_refresh_tokens;
TRUNCATE TABLE auth_tokens;
TRUNCATE TABLE auth_otps;
TRUNCATE TABLE auth_events;
TRUNCATE TABLE user_addresses;
TRUNCATE TABLE wishlist_items;
TRUNCATE TABLE users;

-- courses, workshops and learning
TRUNCATE TABLE lesson_completions;
TRUNCATE TABLE course_module_lesson_tasks;
TRUNCATE TABLE course_module_lessons;
TRUNCATE TABLE course_modules;
TRUNCATE TABLE course_photos;
TRUNCATE TABLE course_prices;
TRUNCATE TABLE course_projects;
TRUNCATE TABLE course_reviews;
TRUNCATE TABLE course_videos;
TRUNCATE TABLE course_certificates;
TRUNCATE TABLE courses;
TRUNCATE TABLE free_course_videos;
TRUNCATE TABLE free_courses;
TRUNCATE TABLE enrollments;
TRUNCATE TABLE orders;
TRUNCATE TABLE payment_logs;
TRUNCATE TABLE certificates;
TRUNCATE TABLE student_submissions;
TRUNCATE TABLE student_artworks;
TRUNCATE TABLE student_tasks;
TRUNCATE TABLE student_task_photos;
TRUNCATE TABLE student_teacher_connects;

-- marketplace, prints and commissions
TRUNCATE TABLE artist_thread_messages;
TRUNCATE TABLE artist_threads;
TRUNCATE TABLE shop_order_items;
TRUNCATE TABLE shop_orders;
TRUNCATE TABLE shop_cart_items;
TRUNCATE TABLE shop_carts;
TRUNCATE TABLE shop_coupons;
TRUNCATE TABLE marketplace_orders;
TRUNCATE TABLE cart_reservations;
TRUNCATE TABLE cart_items;
TRUNCATE TABLE painting_images;
TRUNCATE TABLE paintings;
TRUNCATE TABLE portfolio_marketplace;
TRUNCATE TABLE commission_artwork_items;
TRUNCATE TABLE commission_orders;

-- site content and inboxes
TRUNCATE TABLE post_tag;
TRUNCATE TABLE category_post;
TRUNCATE TABLE posts;
TRUNCATE TABLE testimonials;
TRUNCATE TABLE home_artworks;
TRUNCATE TABLE contacts;
TRUNCATE TABLE newsletter_campaigns;
TRUNCATE TABLE newsletters;

SET FOREIGN_KEY_CHECKS = 1;
