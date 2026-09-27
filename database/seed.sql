-- =====================================================================
--  ArtistikCity - demo seed data (SQL Server)
--  Logins created here (password for all: "password"):
--    admin panel : admin@artistikcity.com      (admin)
--                  vaishali@artistikcity.com   (teacher / instructor)
--    student     : student@artistikcity.com
--  Images referenced below are shipped in the project's uploads/ folder.
-- =====================================================================

-- ---- admins (id 1 = admin, id 2 = instructor used on the home page) --
INSERT INTO admins (name, slug, email, password, admin_type, location, profile_photo, profile_title, designation, profile_description, created_at, updated_at) VALUES
('Site Admin', 'site-admin', 'admin@artistikcity.com', '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', 'admin', 'Pune, India', NULL, 'Administrator', 'Administrator', 'Platform administrator.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Vaishali J Goswami', 'vaishali-j-goswami', 'vaishali@artistikcity.com', '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', 'teacher', 'Pune, India', 'instructor.jpg', 'Artist, Illustrator & Art Educator', 'Founder & Lead Instructor',
 'Vaishali is a professional artist with more than 15 years of experience teaching drawing, watercolour and acrylic painting to learners of every age. Her classes focus on building strong fundamentals and helping every student discover a personal style.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- users -------------------------------------------------------------
INSERT INTO users (name, slug, email, email_verified_at, password, phone, profile_title, profile_description, profile_photo, location, notifications, language_preference, free_courses, free_course_type, status, created_at, updated_at) VALUES
('Aarav Sharma', 'aarav-sharma', 'student@artistikcity.com', CURRENT_TIMESTAMP, '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', '9876543210', 'Aspiring watercolour artist', 'I love painting landscapes and learning new techniques.', 'student.jpg', 'Mumbai, India', 1, 'English', 1, 'color', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Diya Patel', 'diya-patel', 'diya@example.com', CURRENT_TIMESTAMP, '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', NULL, 'Hobbyist', NULL, NULL, 'Delhi, India', 1, 'English', 1, 'default', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- taxonomies --------------------------------------------------------
INSERT INTO mediums (name, medium_slug, photo, status, created_at, updated_at) VALUES
('Watercolour', 'watercolour', 'medium.jpg', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Acrylic', 'acrylic', 'medium.jpg', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Pencil Sketch', 'pencil-sketch', 'medium.jpg', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Oil Painting', 'oil-painting', 'medium.jpg', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Charcoal', 'charcoal', 'medium.jpg', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Digital Art', 'digital-art', 'medium.jpg', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO genres (name, genre_slug, status, created_at, updated_at) VALUES
('Landscape', 'landscape', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Portrait', 'portrait', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Still Life', 'still-life', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Abstract', 'abstract', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_types (name, course_type_slug, status, created_at, updated_at) VALUES
('Course', 'course', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Workshop', 'workshop', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO skills (name, skill_slug, status, created_at, updated_at) VALUES
('Beginner', 'beginner', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Intermediate', 'intermediate', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Advanced', 'advanced', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO categories (name, slug, status, created_at, updated_at) VALUES
('Art Tips', 'art-tips', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Techniques', 'techniques', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Inspiration', 'inspiration', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- courses (1-3 = courses, 4 = workshop) ------------------------------
INSERT INTO courses (admin_id, medium_id, genre_id, course_type_id, skill_id, category_id, title, slug, sub_title,
    introduction, introduction_details, summary, summary_details, schedule, schedule_details, age_group, duration, sessions,
    mini_projects, course_modules, time_required, course_includes, course_highlights, course_for, course_not_for,
    course_problems_solved, course_learn, course_outcome, course_deliverables, course_prerequisites, course_start_date,
    course_based, is_free_course, status, created_at, updated_at) VALUES
(2, 1, '1', 1, 1, 1, 'Watercolour Landscapes for Beginners', 'watercolour-landscapes-for-beginners', 'Learn washes, layering and colour mixing to paint glowing landscapes.',
 'A step-by-step introduction to watercolour painting.', '<p>Over four weeks you will learn how to control water and pigment, plan a painting and finish three complete landscapes.</p>',
 'Fundamentals of watercolour, from materials to finished paintings.', '<ul><li>Materials and set-up</li><li>Flat, graded and wet-in-wet washes</li><li>Skies, trees and water</li></ul>',
 'Live sessions twice a week.', '<p>Week 1: Materials &amp; washes<br>Week 2: Colour mixing<br>Week 3: Skies &amp; trees<br>Week 4: Final landscape</p>',
 '12+ years', '4', '8', '3', '4', '2', '{"certificate":"yes","study_material_access":"yes"}', '["Live classes","Personal feedback"]', '["Beginners"]', '["Professional artists"]', '["Muddy colours"]', '["Washes","Layering"]',
 'Three finished watercolour landscapes.', '["Certificate","Study material"]', 'No prior experience needed.', DATEADD(day, 30, CAST(CURRENT_TIMESTAMP AS DATE)), 'instructor_based', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, '2,4', 1, 2, 2, 'Acrylic Portrait Painting', 'acrylic-portrait-painting', 'Capture likeness, skin tones and light with fast-drying acrylics.',
 'Paint expressive portraits with acrylic colours.', '<p>Understand facial proportions, value studies and blending techniques for acrylics.</p>',
 'Proportions, values and skin tones.', '<ul><li>Facial proportions</li><li>Value mapping</li><li>Skin tone palettes</li></ul>',
 'Weekend live sessions.', '<p>Six weekend sessions with homework reviews.</p>',
 '15+ years', '6', '12', '4', '5', '3', '{"certificate":"yes","study_material_access":"yes"}', '["Portrait fundamentals"]', '["Intermediate learners"]', '["Absolute beginners"]', '["Flat looking faces"]', '["Proportions","Blending"]',
 'Two finished acrylic portraits.', '["Certificate"]', 'Basic drawing skills.', DATEADD(day, 45, CAST(CURRENT_TIMESTAMP AS DATE)), 'instructor_based', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 3, '3', 1, 1, 1, 'Pencil Sketching Essentials', 'pencil-sketching-essentials', 'Build confident drawing skills with graphite pencils.',
 'Drawing fundamentals for everyone.', '<p>Lines, shapes, shading and perspective explained simply.</p>',
 'Everything you need to start sketching.', '<ul><li>Line control</li><li>Shading</li><li>Perspective</li></ul>',
 'Self paced with weekly live Q&amp;A.', '<p>Four modules, one live Q&amp;A each week.</p>',
 '8+ years', '4', '6', '4', '4', '1', '{"certificate":"yes","study_material_access":"no"}', '["Self paced"]', '["Kids and adults"]', '["Experts"]', '["Stiff lines"]', '["Shading","Perspective"]',
 'A sketchbook of finished studies.', '["Certificate"]', 'None.', DATEADD(day, 60, CAST(CURRENT_TIMESTAMP AS DATE)), 'instructor_based', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, '1,3', 2, 1, 3, 'Weekend Watercolour Florals Workshop', 'weekend-watercolour-florals-workshop', 'A relaxing two-day workshop painting loose florals.',
 'Loose florals in a weekend.', '<p>Paint roses, peonies and leaves with a loose, joyful style.</p>',
 'Two days of guided painting.', '<ul><li>Brush strokes</li><li>Colour harmony</li></ul>',
 'Saturday and Sunday, 3 hours each.', '<p>Day 1: Strokes &amp; leaves<br>Day 2: Full floral composition</p>',
 '10+ years', '2', '2', '1', '2', '3', '{"certificate":"no","study_material_access":"yes"}', '["Live workshop"]', '["Everyone"]', '["-"]', '["Stiff florals"]', '["Loose brushwork"]',
 'One framed floral painting.', '["Study material"]', 'None.', DATEADD(day, 20, CAST(CURRENT_TIMESTAMP AS DATE)), 'instructor_based', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_prices (course_id, price_inr, price_usd, created_at, updated_at) VALUES
(1, 4999, 69, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 7499, 99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 2999, 39, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 1499, 25, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_photos (course_id, photo_name, created_at, updated_at) VALUES
(1, 'course.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'course.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'course.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'course.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- modules / lessons / tasks / projects for course 1 ------------------
INSERT INTO course_modules (admin_id, course_id, module_title, module_duration, status, created_at, updated_at) VALUES
(2, 1, 'Getting Started with Watercolour', '1 week', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, 'Painting Skies and Trees', '2 weeks', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_module_lessons (admin_id, course_id, module_id, lesson_title, lesson_description, lesson_pdf, status, created_at, updated_at) VALUES
(2, 1, 1, 'Materials and Set-up', '<p>Choosing paper, brushes and paints. Setting up your workspace.</p>', NULL, '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, 1, 'Basic Washes', '<p>Flat, graded and wet-in-wet washes.</p>', NULL, '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, 2, 'Sunset Skies', '<p>Blending warm colours for dramatic skies.</p>', NULL, '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_module_lesson_tasks (admin_id, course_id, module_id, lesson_id, task_title, task_description, status, created_at, updated_at) VALUES
(2, 1, 1, 2, 'Paint three wash swatches', 'Paint a flat, a graded and a wet-in-wet wash and upload a photo.', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_projects (admin_id, course_id, project_title, project_description, status, created_at, updated_at) VALUES
(2, 1, 'Final Landscape', 'Paint a complete landscape using everything you learned.', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- a paid order for the demo student ----------------------------------
INSERT INTO orders (order_number, user_id, course_id, session_id, billing_name, billing_email, billing_address, billing_city, billing_state, billing_zipcode, billing_country, price_type, price, payment_option, payment_id, payment_status, issue_certificate, created_at, updated_at) VALUES
('ORD00001', 1, 1, 'seed', 'Aarav Sharma', 'student@artistikcity.com', '12 MG Road', 'Mumbai', 'Maharashtra', '400001', 'India', 'inr', '4999', 'RazorPay', 'pay_demo_001', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO user_addresses (user_id, address_name, address, city, state, country, zipcode, [primary], status, created_at, updated_at) VALUES
(1, 'Aarav Sharma', '12 MG Road', 'Mumbai', 'Maharashtra', 'India', '400001', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- free courses ------------------------------------------------------
INSERT INTO free_courses (admin_id, title, slug, description, status, photo, video_url, created_at, updated_at) VALUES
(2, 'Introduction to Colour Theory', 'introduction-to-colour-theory', 'Primary, secondary and complementary colours explained with simple exercises.', 1, 'free.jpg', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO free_course_videos (free_course_id, video_title, video_url, status, created_at, updated_at) VALUES
(1, 'The Colour Wheel', 'https://www.youtube.com/embed/Qj1FK8n7WgY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- site content --------------------------------------------------------
INSERT INTO testimonials (name, slug, title, photo, description, status, created_at, updated_at) VALUES
('Priya Nair', 'priya-nair', 'Watercolour student', 'photo.jpg', 'The classes are structured beautifully and the feedback on every assignment helped me improve quickly.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Rohan Mehta', 'rohan-mehta', 'Portrait workshop', 'photo.jpg', 'I finally understand proportions. My portraits look like the people I am painting!', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO home_artworks (admin_id, photo_name, comments, status, created_at, updated_at) VALUES
(1, 'artwork.jpg', 'Student landscape', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 'artwork.jpg', 'Student portrait', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 'artwork.jpg', 'Student still life', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 'artwork.jpg', 'Student florals', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO student_artworks (user_id, photo_name, comments, status, created_at, updated_at) VALUES
(1, 'artwork.jpg', 'My first watercolour landscape', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO settings (meta_tag, meta_key, meta_value, created_at, updated_at) VALUES
('home', 'course_text_1', 'Live online classes', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('home', 'course_text_2', 'Personal feedback', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('home', 'course_text_3', 'Certificate of completion', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('home', 'workshop_text_1', 'Weekend sessions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('home', 'workshop_text_2', 'All materials list provided', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('home', 'workshop_text_3', 'Small batches', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- blog ------------------------------------------------------------------
INSERT INTO posts (title, subtitle, slug, body, status, posted_by, image, featured, [like], dislike, created_at, updated_at) VALUES
('5 Watercolour Mistakes Beginners Make', 'And how to avoid them', '5-watercolour-mistakes-beginners-make', '<p>Watercolour rewards planning. Here are the five most common mistakes we see in beginner classes and simple ways to fix them.</p>', 1, 2, 'blog-1.jpg', 1, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Choosing Your First Set of Brushes', 'A practical buying guide', 'choosing-your-first-set-of-brushes', '<p>You do not need dozens of brushes. Three good brushes will take you a long way.</p>', 1, 2, 'blog-2.jpg', 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Finding Inspiration Every Day', 'Keep a sketchbook habit', 'finding-inspiration-every-day', '<p>A daily five-minute sketch builds observation skills faster than anything else.</p>', 1, 2, 'blog-3.jpg', 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tags (name, slug, status, created_at, updated_at) VALUES
('watercolour', 'watercolour', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('beginners', 'beginners', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO category_post (category_id, post_id, created_at, updated_at) VALUES
(1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO post_tag (post_id, tag_id, created_at, updated_at) VALUES
(1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- Student Creative Lifecycle demo data ------------------------------
INSERT INTO admins (name, slug, email, password, admin_type, location, profile_photo, profile_title, designation, profile_description, created_at, updated_at) VALUES
('Studio Moderator', 'studio-moderator', 'moderator@artistikcity.com', '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', 'moderator', 'Pune, India', NULL, 'Gallery Moderator', 'Moderator', 'Reviews student submissions, gallery and testimonials.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO enrollments (user_id, course_id, progress_percentage, status, created_at, updated_at) VALUES
(1, 1, 0, 'Active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
