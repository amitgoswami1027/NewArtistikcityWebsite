-- Read-only: how many records of each kind the database holds (changes nothing).
SELECT 'students (users)' AS what, COUNT(*) AS how_many FROM users
UNION ALL SELECT 'staff (admins)', COUNT(*) FROM admins
UNION ALL SELECT 'courses + workshops', COUNT(*) FROM courses
UNION ALL SELECT 'course lessons', COUNT(*) FROM course_module_lessons
UNION ALL SELECT 'course orders', COUNT(*) FROM orders
UNION ALL SELECT 'enrollments', COUNT(*) FROM enrollments
UNION ALL SELECT 'marketplace paintings', COUNT(*) FROM paintings
UNION ALL SELECT 'marketplace orders', COUNT(*) FROM marketplace_orders
UNION ALL SELECT 'shop orders', COUNT(*) FROM shop_orders
UNION ALL SELECT 'blog posts', COUNT(*) FROM posts
UNION ALL SELECT 'commission requests', COUNT(*) FROM commission_orders
UNION ALL SELECT 'student submissions', COUNT(*) FROM student_submissions
UNION ALL SELECT 'certificates', COUNT(*) FROM course_certificates
UNION ALL SELECT 'generated PDFs', COUNT(*) FROM student_documents
UNION ALL SELECT 'testimonials', COUNT(*) FROM testimonials
UNION ALL SELECT 'newsletter subscribers', COUNT(*) FROM newsletters
UNION ALL SELECT 'contact messages', COUNT(*) FROM contacts;
SELECT id, name, email FROM users ORDER BY id;
SELECT id, title FROM courses ORDER BY id;
SELECT id, title, status FROM paintings ORDER BY id;
SELECT id, title, status FROM posts ORDER BY id;
