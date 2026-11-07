-- =====================================================================
--  ArtistikCity - demo seed data (MySQL)
--  Logins created here (password for all: "password"):
--    admin panel : admin@artistikcity.com      (admin)
--                  vaishali@artistikcity.com   (teacher / instructor)
--    student     : student@artistikcity.com
--  The last section tops every entity up to 4-5 varied records (buyers, a certified student seller,
--  course orders in every payment state, print orders in every fulfilment state, carts, commissions,
--  reviews, wishlists, enquiries, newsletter sign-ups). See "Wider demo records" below.
--  Images referenced below are shipped in the project's uploads/ folder.
-- =====================================================================

-- ---- admins (id 1 = admin, id 2 = instructor used on the home page) --
INSERT INTO admins (name, slug, email, password, admin_type, location, profile_photo, profile_title, designation, profile_description, created_at, updated_at) VALUES
('Site Admin', 'site-admin', 'admin@artistikcity.com', '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', 'admin', 'Pune, India', NULL, 'Administrator', 'Administrator', 'Platform administrator.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Vaishali J Goswami', 'vaishali-j-goswami', 'vaishali@artistikcity.com', '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', 'teacher', 'Pune, India', 'instructor.jpg', 'Artist, Illustrator & Art Educator', 'Founder & Lead Instructor',
 'Vaishali is a professional artist with more than 15 years of experience teaching drawing, watercolour and acrylic painting to learners of every age. Her classes focus on building strong fundamentals and helping every student discover a personal style.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- users -------------------------------------------------------------
INSERT INTO users (name, slug, email, email_verified_at, password_hash, phone_number, profile_title, profile_description, profile_photo, location, notifications, language_preference, free_courses, free_course_type, status, is_verified, phone_verified_at, created_at, updated_at) VALUES
('Aarav Sharma', 'aarav-sharma', 'student@artistikcity.com', CURRENT_TIMESTAMP, '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', '+919876543210', 'Aspiring watercolour artist', 'I love painting landscapes and learning new techniques.', 'student.jpg', 'Mumbai, India', 1, 'English', 1, 'color', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Diya Patel', 'diya-patel', 'diya@example.com', CURRENT_TIMESTAMP, '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', NULL, 'Hobbyist', NULL, NULL, 'Delhi, India', 1, 'English', 1, 'default', 1, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

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
 'Three finished watercolour landscapes.', '["Certificate","Study material"]', 'No prior experience needed.', DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY), 'instructor_based', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, '2,4', 1, 2, 2, 'Acrylic Portrait Painting', 'acrylic-portrait-painting', 'Capture likeness, skin tones and light with fast-drying acrylics.',
 'Paint expressive portraits with acrylic colours.', '<p>Understand facial proportions, value studies and blending techniques for acrylics.</p>',
 'Proportions, values and skin tones.', '<ul><li>Facial proportions</li><li>Value mapping</li><li>Skin tone palettes</li></ul>',
 'Weekend live sessions.', '<p>Six weekend sessions with homework reviews.</p>',
 '15+ years', '6', '12', '4', '5', '3', '{"certificate":"yes","study_material_access":"yes"}', '["Portrait fundamentals"]', '["Intermediate learners"]', '["Absolute beginners"]', '["Flat looking faces"]', '["Proportions","Blending"]',
 'Two finished acrylic portraits.', '["Certificate"]', 'Basic drawing skills.', DATE_ADD(CURRENT_DATE, INTERVAL 45 DAY), 'instructor_based', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 3, '3', 1, 1, 1, 'Pencil Sketching Essentials', 'pencil-sketching-essentials', 'Build confident drawing skills with graphite pencils.',
 'Drawing fundamentals for everyone.', '<p>Lines, shapes, shading and perspective explained simply.</p>',
 'Everything you need to start sketching.', '<ul><li>Line control</li><li>Shading</li><li>Perspective</li></ul>',
 'Self paced with weekly live Q&amp;A.', '<p>Four modules, one live Q&amp;A each week.</p>',
 '8+ years', '4', '6', '4', '4', '1', '{"certificate":"yes","study_material_access":"no"}', '["Self paced"]', '["Kids and adults"]', '["Experts"]', '["Stiff lines"]', '["Shading","Perspective"]',
 'A sketchbook of finished studies.', '["Certificate"]', 'None.', DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY), 'instructor_based', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, '1,3', 2, 1, 3, 'Weekend Watercolour Florals Workshop', 'weekend-watercolour-florals-workshop', 'A relaxing two-day workshop painting loose florals.',
 'Loose florals in a weekend.', '<p>Paint roses, peonies and leaves with a loose, joyful style.</p>',
 'Two days of guided painting.', '<ul><li>Brush strokes</li><li>Colour harmony</li></ul>',
 'Saturday and Sunday, 3 hours each.', '<p>Day 1: Strokes &amp; leaves<br>Day 2: Full floral composition</p>',
 '10+ years', '2', '2', '1', '2', '3', '{"certificate":"no","study_material_access":"yes"}', '["Live workshop"]', '["Everyone"]', '["-"]', '["Stiff florals"]', '["Loose brushwork"]',
 'One framed floral painting.', '["Study material"]', 'None.', DATE_ADD(CURRENT_DATE, INTERVAL 20 DAY), 'instructor_based', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

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

INSERT INTO user_addresses (user_id, address_name, address, city, state, country, zipcode, `primary`, status, created_at, updated_at) VALUES
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
('home', 'workshop_text_3', 'Small batches', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('seo', 'site_title', 'ArtistikCity - Live online art classes', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('seo', 'meta_description', 'Learn to draw and paint online with live classes, personal feedback and certificates. Courses and workshops in watercolour, acrylic, sketching and more.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('seo', 'meta_keywords', 'online art classes, painting course, drawing course, watercolour, acrylic', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('seo', 'og_image', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('contact', 'email', 'info@artistickcity.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('contact', 'phone', '9876 543 201', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('contact', 'address', '505, Agate, MyHomes Jewel,\nMadinguda, Hyderabad - 540017\nTelangana, India.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('social', 'facebook', 'https://www.facebook.com/StudioVaishaliArts/', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('social', 'instagram', 'https://www.instagram.com/artistikcity/', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('social', 'youtube', 'https://www.youtube.com/@artistikcityYT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('social', 'linkedin', 'https://in.linkedin.com/in/vaishali-jetly-goswami-4b5874233', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- FAQ (Admin > FAQ) ----------------------------------------------------
INSERT INTO faqs (category, question, answer, sort_order, status, created_at, updated_at) VALUES
('Classes', 'How do the live classes work?', '<p>Each course runs as a series of live online sessions with your instructor. After you enrol, the schedule, lessons and tasks appear under <strong>My studio &gt; My courses</strong>.</p>', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Classes', 'What do I need to start?', '<p>Every course page lists the materials you need. A phone, tablet or computer with a good internet connection is enough to join the sessions.</p>', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Payments', 'Which payment methods can I use?', '<p>In India you can pay with UPI, cards and NetBanking. From other countries you can pay with PayPal.</p>', 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Certificates', 'Will I get a certificate?', '<p>Yes. When you complete the lessons and your final work is approved, a verifiable certificate is added to <strong>My studio &gt; Certificates &amp; reports</strong>.</p>', 4, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Marketplace', 'Can I sell my artwork on ArtistikCity?', '<p>Approved student work can be listed as a 1-of-1 original in the marketplace from your portfolio.</p>', 5, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- blog ------------------------------------------------------------------
INSERT INTO posts (title, subtitle, slug, body, status, posted_by, image, featured, `like`, dislike, created_at, updated_at) VALUES
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

-- demo progress for the student: course 1 completed with one approved and one pending artwork
INSERT INTO lesson_completions (enrollment_id, lesson_id, completed_at) VALUES
(1, 1, CURRENT_TIMESTAMP), (1, 2, CURRENT_TIMESTAMP), (1, 3, CURRENT_TIMESTAMP);
UPDATE enrollments SET progress_percentage = 100, status = 'Completed', updated_at = CURRENT_TIMESTAMP WHERE id = 1;
INSERT INTO student_submissions (user_id, course_id, title, description, file_url, admin_status, reviewer_feedback, reviewed_by, reviewed_at, created_at, updated_at) VALUES
(1, 1, 'Monsoon Hills', 'Wet-in-wet sky with layered hills, painted on 300gsm cold-press paper.', '/storage/uploads/artworks/1/artwork.jpg', 'Approved',
 'Beautiful control of the washes and a clear sense of depth. Next time, leave a little more white paper for the highlights on the water.', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'Evening Lake Study', 'Quick study of reflections at sunset.', '/storage/uploads/home-artworks/3/artwork.jpg', 'Pending Review', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO portfolio_marketplace (submission_id, user_id, is_listed_for_sale, sale_price, currency, inventory_count, created_at, updated_at) VALUES
(1, 1, 1, 2500.00, 'INR', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- 1-of-1 Original Art Marketplace demo data -------------------------
-- final_price must equal ROUND(base_price * (100 - discount_percentage) / 100, 2)
INSERT INTO paintings (slug, title, description, artist_notes, medium, surface, subject, style_tags, height_inches, width_inches, depth_inches, weight_kg, year_created,
    is_framed, frame_details, is_signed, has_certificate, base_price, discount_percentage, final_price, currency, stock_status, version, source, artist_name,
    artist_admin_id, artist_user_id, submission_id, is_featured, published_at, sold_at, created_at, updated_at) VALUES
('scarlet-macaw-study', 'Scarlet Macaw Study', 'A close, feather-by-feather study of a macaw at rest, built up in soft graphite layers from 2H to 8B.',
 'I sketched this bird at a friend''s aviary over three mornings. What held me was the way the light slid across each feather, so I let the background stay quiet and spent the time on texture.',
 'Graphite', 'Archival 300gsm acid-free paper', 'Wildlife', 'Realism, Study', 16, 12, 0.10, 0.60, 2025, 1, 'Black oak float frame with museum glass', 1, 1, 18000.00, 15.00, 15300.00, 'INR', 'AVAILABLE', 0, 'STUDIO', 'Vaishali J Goswami',
 2, NULL, NULL, 1, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('the-old-mill-at-dawn', 'The Old Mill at Dawn', 'A forest mill beside a rushing stream, with moss-covered rocks and early light filtering through the pines.',
 'This painting began as a memory of a trek in the hills. I kept the water loose and fast and slowed down on the rocks, so your eye rests there before it follows the stream out of the frame.',
 'Acrylic', 'Gallery-wrapped cotton canvas', 'Landscape', 'Realism, Nature', 30, 30, 1.50, 3.20, 2024, 0, NULL, 1, 1, 65000.00, 10.00, 58500.00, 'INR', 'AVAILABLE', 0, 'STUDIO', 'Vaishali J Goswami',
 2, NULL, NULL, 1, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('lines-of-a-lifetime', 'Lines of a Lifetime', 'A charcoal portrait of an elder, every wrinkle mapped with compressed and vine charcoal.',
 'Faces like this one tell a whole story without a word. I worked from dark to light, lifting highlights with a kneaded eraser to keep the skin soft next to the deep shadows.',
 'Charcoal', 'Heavyweight cartridge paper', 'Portrait', 'Realism, Portrait', 28, 18, 0.10, 0.90, 2024, 0, NULL, 1, 1, 42000.00, 0.00, 42000.00, 'INR', 'AVAILABLE', 0, 'STUDIO', 'Vaishali J Goswami',
 2, NULL, NULL, 0, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('curls-in-charcoal', 'Curls in Charcoal', 'A portrait study focused on movement in the hair and a calm, direct gaze.',
 'I wanted the curls to feel like they were still moving, so I drew them fast and fixed the face slowly.',
 'Charcoal', 'Toned drawing paper', 'Portrait', 'Expressive, Portrait', 14, 20, 0.10, 0.50, 2023, 0, NULL, 1, 1, 24000.00, 20.00, 19200.00, 'INR', 'SOLD', 2, 'STUDIO', 'Vaishali J Goswami',
 2, NULL, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('flameback-woodpecker', 'Flameback Woodpecker', 'A golden-backed woodpecker clinging to rough bark, painted in transparent watercolour glazes.',
 'The bark took longer than the bird. I used salt and dry-brush to find the texture, then kept the bird crisp so it pops off the trunk.',
 'Watercolour', 'Cold-press 100% cotton paper', 'Wildlife', 'Realism, Nature', 20, 17.5, 0.10, 0.70, 2025, 1, 'White wood frame with mount', 1, 1, 36000.00, 0.00, 36000.00, 'INR', 'RESERVED', 1, 'STUDIO', 'Vaishali J Goswami',
 2, NULL, NULL, 1, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('copper-kettle-still-life', 'Copper Kettle Still Life', 'An antique kettle on its warmer, drawn with careful attention to reflections on metal.',
 'Reflective objects are my favourite puzzle. Everything you see on the kettle is the room around it, bent and squeezed.',
 'Graphite', 'Smooth bristol board', 'Still life', 'Realism, Study', 12, 16, 0.10, 0.40, 2024, 0, NULL, 1, 1, 9500.00, 5.00, 9025.00, 'INR', 'AVAILABLE', 0, 'STUDIO', 'Vaishali J Goswami',
 2, NULL, NULL, 0, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('monsoon-hills', 'Monsoon Hills', 'Wet-in-wet sky with layered hills, painted on 300gsm cold-press paper.',
 'Painted during my first ArtistikCity watercolour course. I let the sky bleed into the hills to catch that heavy monsoon feeling.',
 'Watercolour', 'Cold-press 300gsm paper', 'Landscape', 'Student original', 11, 15, 0.05, 0.30, 2026, 0, NULL, 1, 1, 2500.00, 0.00, 2500.00, 'INR', 'AVAILABLE', 0, 'STUDENT', 'Aarav Sharma',
 NULL, 1, 1, 0, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('glass-marble-light', 'Glass Marble, Morning Light', 'A single glass marble and its long shadow, drawn to study refraction.',
 'A small object, a big lesson in light. Submitted for review before it goes live.',
 'Graphite', 'Smooth bristol board', 'Still life', 'Minimal, Study', 10, 14, 0.10, 0.30, 2026, 0, NULL, 1, 1, 7000.00, 0.00, 7000.00, 'INR', 'PENDING_REVIEW', 0, 'STUDIO', 'Vaishali J Goswami',
 2, NULL, NULL, 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('rose-in-monochrome', 'Rose in Monochrome', 'A single rose rendered in graphite with deep tonal contrast.',
 NULL, 'Graphite', 'Archival drawing paper', 'Botanical', 'Realism', 16, 12, 0.10, 0.40, 2026, 0, NULL, 1, 1, 8000.00, 0.00, 8000.00, 'INR', 'DRAFT', 0, 'STUDIO', 'Vaishali J Goswami',
 2, NULL, NULL, 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('glass-jars-still-life', 'Glass Jars on the Sill', 'Two jars and a coin on a windowsill, a study of transparency and texture.',
 'Drawn on a slow afternoon; the coin in the jar was a last-minute idea that ended up being the focal point.',
 'Graphite', 'Textured drawing paper', 'Still life', 'Realism, Study', 12, 16, 0.10, 0.40, 2025, 0, NULL, 1, 1, 12000.00, 0.00, 12000.00, 'INR', 'SOLD', 2, 'STUDIO', 'Vaishali J Goswami',
 2, NULL, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO painting_images (painting_id, image_url, is_primary, sort_order, alt_text) VALUES
(1, '/assets/images/portfolio-2.jpg', 1, 0, 'Scarlet Macaw Study, graphite drawing'),
(2, '/assets/images/course1.jpg', 1, 0, 'The Old Mill at Dawn, acrylic landscape'),
(2, '/assets/images/register-banner.jpg', 0, 1, 'The Old Mill at Dawn, detail'),
(3, '/assets/images/course2.jpg', 1, 0, 'Lines of a Lifetime, charcoal portrait'),
(4, '/assets/images/course3.jpg', 1, 0, 'Curls in Charcoal, portrait'),
(5, '/assets/images/login-banner.jpg', 1, 0, 'Flameback Woodpecker, watercolour'),
(6, '/assets/images/portfolio-5.jpg', 1, 0, 'Copper Kettle Still Life, graphite'),
(7, '/storage/uploads/artworks/1/artwork.jpg', 1, 0, 'Monsoon Hills, student watercolour'),
(8, '/assets/images/portfolio-8.jpg', 1, 0, 'Glass Marble, Morning Light'),
(9, '/assets/images/portfolio-7.jpg', 1, 0, 'Rose in Monochrome'),
(10, '/assets/images/portfolio-3.jpg', 1, 0, 'Glass Jars on the Sill');

-- every demo original also sells canvas and poster prints; the first one has artist contact links for the "Meet the artist" hub
UPDATE paintings SET shop_formats = 'CANVAS,POSTER';
UPDATE paintings SET artist_whatsapp = '+919876543201', artist_instagram = 'artistikcity' WHERE id = 1;

-- an artist coupon code for the purchase flow
INSERT INTO shop_coupons (code, description, discount_type, discount_value, painting_id, artist_name, min_subtotal, max_uses, used_count,
    starts_at, expires_at, is_active, created_at, updated_at) VALUES
('WELCOME10', '10% off your first artwork', 'PERCENT', 10, NULL, NULL, NULL, NULL, 0, NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('VAISHALI500', 'Rs 500 off Vaishali''s originals and prints', 'FLAT', 500, NULL, 'Vaishali J Goswami', 3000, 100, 0, NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- a live 15-minute hold by another collector, so the "on hold" state is visible after start-up
INSERT INTO cart_reservations (session_or_user_id, painting_id, user_id, reserved_at, expires_at) VALUES
('S:demo-collector', 5, NULL, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 15 MINUTE));

INSERT INTO marketplace_orders (order_id, painting_id, user_id, holder_key, buyer_name, buyer_email, buyer_phone, address_line1, address_line2, city, state, postal_code, country,
    gateway, currency, amount, price_inr, provider_ref, transaction_id, status, courier, tracking_number, paid_at, created_at, updated_at) VALUES
('AC-M-DEMO01', 4, NULL, 'S:demo', 'Meera Iyer', 'meera@example.com', '9000000001', '12 Lavelle Road', NULL, 'Bengaluru', 'Karnataka', '560001', 'India',
 'RAZORPAY', 'INR', 19200.00, 19200.00, 'order_demo01', 'pay_demo01', 'SHIPPED', 'Blue Dart', 'BD123456789IN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AC-M-DEMO02', 10, NULL, 'S:demo', 'James Carter', 'james@example.com', NULL, '221 Baker Street', NULL, 'London', NULL, 'NW1 6XE', 'United Kingdom',
 'PAYPAL', 'USD', 142.86, 12000.00, 'PAYPAL-DEMO02', 'CAPTURE-DEMO02', 'PAID', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);


-- =====================================================================
--  Wider demo records: every entity has 4-5 varied rows, each one a different real-world case.
--  Users 1-5: 1 Aarav (certified student, also sells), 2 Diya (print buyer, commission client), 3 Ishita
--  (signed up, email NOT verified, unpaid order), 4 Farhan (collector; a refunded workshop),
--  5 Tanvi (certified student artist with a live listing). Farhan is also half-way through the course 5 masterclass. Password for every login: "password".
-- =====================================================================

-- ---- staff: a second teacher and a second moderator --------------------------------------------
INSERT INTO admins (name, slug, email, password, admin_type, location, profile_photo, profile_title, designation, profile_description, created_at, updated_at) VALUES
('Rajat Malhotra', 'rajat-malhotra', 'rajat@artistikcity.com', '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', 'teacher', 'New Delhi, India', 'avatar.png', 'Portrait Artist & Charcoal Specialist', 'Portrait Instructor',
 'Rajat has drawn more than 600 commissioned portraits in charcoal and graphite. He teaches the measuring, value and edge control that make a likeness hold up.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Pooja Shetty', 'pooja-shetty', 'pooja@artistikcity.com', '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', 'moderator', 'Mangaluru, India', NULL, 'Community Moderator', 'Moderator', 'Looks after reviews, reports and the student gallery.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- three more learners / buyers (ids 3-5) --------------------------------------------------
INSERT INTO users (name, slug, email, email_verified_at, password_hash, phone_number, profile_title, profile_description, profile_photo, location, notifications, language_preference, free_courses, free_course_type, status, is_verified, phone_verified_at, last_login_at, created_at, updated_at) VALUES
('Ishita Bose', 'ishita-bose', 'ishita.bose@example.com', NULL, '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', NULL, NULL, NULL, 'avatar.png', 'Kolkata, India', 1, 'English', 0, 'default', 1, 0, NULL, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
('Farhan Siddiqui', 'farhan-siddiqui', 'farhan.siddiqui@example.com', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 40 DAY), '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', '+919812345670', 'Art collector', 'I collect original Indian wildlife and portrait work for my home and office.', 'avatar.png', 'Lucknow, India', 0, 'English', 0, 'default', 1, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 40 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 40 DAY), CURRENT_TIMESTAMP),
('Tanvi Kulkarni', 'tanvi-kulkarni', 'tanvi.kulkarni@example.com', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 90 DAY), '$2y$10$8uheEMmigBJbrXk8eKxWW.BEfBIWUeZtLGRdZ7a.ms.WP8GuMa31e', '+919822001144', 'Watercolour student, Pune', 'Architecture student who paints the old city on weekends. Certified in Watercolour Landscapes.', 'avatar.png', 'Pune, India', 1, 'Marathi', 1, 'color', 1, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 90 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 90 DAY), CURRENT_TIMESTAMP);

INSERT INTO user_addresses (user_id, address_name, address, city, state, country, zipcode, `primary`, status, created_at, updated_at) VALUES
(1, 'Aarav Sharma (office)', '4th floor, Kalpataru Square, Andheri East', 'Mumbai', 'Maharashtra', 'India', '400069', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'Diya Patel', 'B-14, Vasant Vihar', 'New Delhi', 'Delhi', 'India', '110057', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'Farhan Siddiqui', '22 Hazratganj, Near GPO', 'Lucknow', 'Uttar Pradesh', 'India', '226001', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 'Tanvi Kulkarni', 'Flat 7, Shaniwar Peth', 'Pune', 'Maharashtra', 'India', '411030', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- a fifth course (advanced, second teacher) and its curriculum -------------------------------
INSERT INTO courses (admin_id, medium_id, genre_id, course_type_id, skill_id, category_id, title, slug, sub_title,
    introduction, introduction_details, summary, summary_details, schedule, schedule_details, age_group, duration, sessions,
    mini_projects, course_modules, time_required, course_includes, course_highlights, course_for, course_not_for,
    course_problems_solved, course_learn, course_outcome, course_deliverables, course_prerequisites, course_start_date,
    course_based, is_free_course, status, created_at, updated_at) VALUES
(4, 5, '2', 1, 3, 2, 'Charcoal Portrait Masterclass', 'charcoal-portrait-masterclass', 'Measure, map values and control edges for portraits that hold a likeness.',
 'An advanced portrait course in compressed and vine charcoal.', '<p>Five weeks of live demonstrations and one-to-one critiques. You will draw from photo references and finish two large portraits.</p>',
 'Likeness, values and edges in charcoal.', '<ul><li>Measuring and block-in</li><li>Five-value mapping</li><li>Lost and found edges</li><li>Hair and skin texture</li></ul>',
 'Live sessions every Wednesday and Saturday evening (IST).', '<p>Week 1: Block-in<br>Week 2: Values<br>Week 3: Features<br>Week 4: Hair &amp; texture<br>Week 5: Final portrait</p>',
 '16+ years', '5', '10', '4', '5', '3', '{"certificate":"yes","study_material_access":"yes"}', '["Live critiques","Reference pack"]', '["Intermediate and advanced artists"]', '["Absolute beginners"]', '["Faces that do not look like the sitter"]', '["Measuring","Value control","Edges"]',
 'Two finished A3 charcoal portraits.', '["Certificate","Reference pack"]', 'Comfortable with basic drawing and shading.', DATE_ADD(CURRENT_DATE, INTERVAL 75 DAY), 'instructor_based', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_prices (course_id, price_inr, price_usd, created_at, updated_at) VALUES
(5, 9999, 129, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO course_photos (course_id, photo_name, created_at, updated_at) VALUES
(5, 'course.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- modules 3-5: course 5 gets two modules with one lesson each; course 2's first module has no lessons yet
-- (course 3 is left without enrolments on purpose: the system tests put a one-seat cap on it)
INSERT INTO course_modules (admin_id, course_id, module_title, module_duration, status, created_at, updated_at) VALUES
(4, 5, 'Measuring and Block-in', '1 week', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 5, 'Values and Edges', '2 weeks', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, 'Facial Proportions', '2 weeks', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_module_lessons (admin_id, course_id, module_id, lesson_title, lesson_description, lesson_pdf, status, created_at, updated_at) VALUES
(4, 5, 3, 'Measuring with a Plumb Line', '<p>Use your pencil as a plumb line and a ruler to place the eyes, nose and mouth before any shading.</p>', NULL, '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 5, 4, 'Five-Value Mapping', '<p>Squint, group the face into five values and block them in with vine charcoal; keep edges soft until the end.</p>', NULL, '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_module_lesson_tasks (admin_id, course_id, module_id, lesson_id, task_title, task_description, status, created_at, updated_at) VALUES
(2, 1, 2, 3, 'Sunset sky in three layers', 'Paint a graded sunset sky in three glazes and upload a photo taken in daylight.', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 5, 3, 4, 'Block-in from a reference', 'Block in a portrait from the reference pack using only straight lines and upload a photo.', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 5, 4, 5, 'Five-value study', 'Draw a 10-minute five-value study of the same face.', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_projects (admin_id, course_id, project_title, project_description, status, created_at, updated_at) VALUES
(2, 2, 'Self-portrait in Acrylic', 'Paint a 12 x 16 inch self-portrait from a mirror or photo, focusing on warm and cool skin tones.', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 3, 'Sketchbook of Everyday Objects', 'Ten pages of shaded studies of objects from your home.', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 5, 'Final Charcoal Portrait', 'An A3 portrait of a family member or friend from your own reference photo.', '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- course orders in every payment state (paid INR, paid USD, unpaid, refunded) --------------
INSERT INTO orders (order_number, user_id, course_id, session_id, billing_name, billing_email, billing_address, billing_city, billing_state, billing_zipcode, billing_country, price_type, price, payment_option, payment_id, payment_status, issue_certificate, created_at, updated_at) VALUES
('ORD00002', 4, 5, 'seed', 'Farhan Siddiqui', 'farhan.siddiqui@example.com', '22 Hazratganj, Near GPO', 'Lucknow', 'Uttar Pradesh', '226001', 'India', 'inr', '9999', 'RazorPay', 'pay_seed_0002', 1, 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 20 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 20 DAY)),
('ORD00003', 5, 1, 'seed', 'Tanvi Kulkarni', 'tanvi.kulkarni@example.com', 'Flat 7, Shaniwar Peth', 'Pune', 'Maharashtra', '411030', 'India', 'inr', '4999', 'RazorPay', 'pay_seed_0003', 1, 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 85 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 85 DAY)),
('ORD00004', 3, 2, 'seed', 'Ishita Bose', 'ishita.bose@example.com', '9 Lake Place', 'Kolkata', 'West Bengal', '700029', 'India', 'inr', '7499', 'RazorPay', NULL, 0, 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)),
('ORD00005', 4, 4, 'seed', 'Farhan Siddiqui', 'farhan.siddiqui@example.com', '22 Hazratganj, Near GPO', 'Lucknow', 'Uttar Pradesh', '226001', 'India', 'inr', '1499', 'RazorPay', 'pay_seed_0005', 3, 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 28 DAY));

INSERT INTO enrollments (user_id, course_id, progress_percentage, status, created_at, updated_at) VALUES
(4, 5, 50, 'Active', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 20 DAY), CURRENT_TIMESTAMP),
(5, 1, 100, 'Completed', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 85 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)),
(4, 4, 0, 'Refunded', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 28 DAY));

INSERT INTO lesson_completions (enrollment_id, lesson_id, completed_at) VALUES
(2, 4, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 DAY)),
(3, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 80 DAY)), (3, 2, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 70 DAY)), (3, 3, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 45 DAY));

-- submissions 3-5: Tanvi has one listed and one approved-but-unlisted piece; Farhan has a rejected study
INSERT INTO student_submissions (user_id, course_id, title, description, file_url, admin_status, reviewer_feedback, reviewed_by, reviewed_at, created_at, updated_at) VALUES
(5, 1, 'Shaniwar Wada at Dusk', 'The fort gate in warm evening light, wet-in-wet sky with dry-brush stonework.', '/storage/uploads/artworks/5/artwork.jpg', 'Approved',
 'Lovely restraint in the sky and a convincing sense of scale. The stonework is the strongest passage.', 2, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 35 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)),
(5, 1, 'Lotus Pond, Morning', 'Lotus leaves and reflections painted in three glazes.', '/storage/uploads/artworks/4/artwork.jpg', 'Approved',
 'Clean glazes and a calm composition. Ready for your portfolio.', 2, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 12 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 14 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 12 DAY)),
(4, 5, 'Portrait of My Father', 'Charcoal block-in and value study from a family photo.', '/storage/uploads/artworks/3/artwork.jpg', 'Rejected',
 'The eyes sit too high: measure the halfway line of the head again, then resubmit. The values are already strong.', 2, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY));

INSERT INTO portfolio_marketplace (submission_id, user_id, is_listed_for_sale, sale_price, currency, inventory_count, created_at, updated_at) VALUES
(3, 5, 1, 4200.00, 'INR', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 28 DAY), CURRENT_TIMESTAMP);

-- painting 11: Tanvi's student original, approved and live
INSERT INTO paintings (slug, title, description, artist_notes, medium, surface, subject, style_tags, height_inches, width_inches, depth_inches, weight_kg, year_created,
    is_framed, frame_details, is_signed, has_certificate, base_price, discount_percentage, final_price, currency, stock_status, version, source, artist_name,
    artist_admin_id, artist_user_id, submission_id, is_featured, shop_formats, reviewed_by, reviewed_at, published_at, sold_at, created_at, updated_at) VALUES
('shaniwar-wada-at-dusk', 'Shaniwar Wada at Dusk', 'The fort gate in warm evening light, wet-in-wet sky with dry-brush stonework.',
 'I sketched the gate on three evenings from the same bench. The stone changes colour every ten minutes at sunset, so I painted the sky first and the stone last.',
 'Watercolour', 'Cold-press 300gsm paper', 'Cityscape', 'Student original', 12, 16, 0.05, 0.30, 2026, 0, NULL, 1, 1, 4200.00, 0.00, 4200.00, 'INR', 'AVAILABLE', 0, 'STUDENT', 'Tanvi Kulkarni',
 NULL, 5, 3, 0, NULL, 3, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 27 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 27 DAY), NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 28 DAY), CURRENT_TIMESTAMP);
INSERT INTO painting_images (painting_id, image_url, is_primary, sort_order, alt_text) VALUES
(11, '/storage/uploads/artworks/5/artwork.jpg', 1, 0, 'Shaniwar Wada at Dusk, student watercolour');

INSERT INTO student_artworks (user_id, photo_name, comments, status, created_at, updated_at) VALUES
(5, 'artwork.jpg', 'Practice wash for the Shaniwar Wada sky', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'artwork.jpg', 'Charcoal block-in warm-up', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 'artwork.jpg', 'Lotus leaves colour study', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- course reviews (one waiting for moderation) and wishlists ------------------------------
INSERT INTO course_reviews (course_id, added_by_id, added_by_type, rating, review, status, created_at, updated_at) VALUES
(1, 1, 'Student', 5, 'The wash exercises in week one changed how I paint skies. Vaishali''s feedback on every task was specific and kind.', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 15 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 15 DAY)),
(1, 5, 'Student', 5, 'Structured, calm and practical. I sold my first painting after this course.', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 25 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 25 DAY)),
(5, 4, 'Student', 4, 'The measuring drills alone were worth it. I would love one more live critique session.', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 DAY)),
(4, 4, 'Student', 3, 'Nice workshop, but the timing clashed with my work so I asked for a refund.', 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 27 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 27 DAY));

INSERT INTO wishlist_items (user_id, item_type, item_id, created_at) VALUES
(2, 'painting', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 DAY)),
(2, 'course', 2, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 DAY)),
(4, 'painting', 2, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY)),
(4, 'painting', 11, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
(3, 'course', 5, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY));

-- course cart (one guest session, three signed-in learners)
INSERT INTO cart_items (session_id, user_id, order_id, course_id, price_type, price, created_at, updated_at) VALUES
('seed-cart-ishita', 3, NULL, 2, 'inr', '7499', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)),
('seed-cart-diya', 2, NULL, 5, 'usd', '129', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('seed-cart-farhan', 4, NULL, 1, 'inr', '4999', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('seed-cart-guest', NULL, NULL, 4, 'inr', '1499', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---- art shop: three open carts (5 items) and five print orders, one per fulfilment state -----
INSERT INTO shop_carts (owner_key, user_id, coupon_code, created_at, updated_at) VALUES
('U:2', 2, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), CURRENT_TIMESTAMP),
('U:4', 4, 'WELCOME10', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 HOUR), CURRENT_TIMESTAMP),
('S:seed-guest-cart', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO shop_cart_items (cart_id, painting_id, format, size_code, finish, frame, quantity, created_at, updated_at) VALUES
(1, 2, 'CANVAS', 'M', 'STRETCHED', 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 'POSTER', 'M', 'MATTE', 'NONE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 6, 'POSTER', 'L', 'GLOSSY', 'BLACK_WOOD', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 3, 'CANVAS', 'S', 'ROLLED', 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 5, 'POSTER', 'S', 'MATTE', 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO shop_orders (order_id, user_id, owner_key, buyer_name, buyer_email, buyer_phone, address_line1, address_line2, city, state, postal_code, country, needs_shipping,
    gateway, currency, amount, subtotal_inr, discount_inr, shipping_inr, total_inr, coupon_code, provider_ref, transaction_id, status, courier, tracking_number, paid_at, created_at, updated_at) VALUES
('AC-S-SEED01', 4, 'U:4', 'Farhan Siddiqui', 'farhan.siddiqui@example.com', '+919812345670', '22 Hazratganj, Near GPO', NULL, 'Lucknow', 'Uttar Pradesh', '226001', 'India', 1,
 'RAZORPAY', 'INR', 6248.00, 6099.00, 0.00, 149.00, 6248.00, NULL, 'order_seed_s01', 'pay_seed_s01', 'DELIVERED', 'Delhivery', 'DLV7781203345', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 21 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 21 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 14 DAY)),
('AC-S-SEED02', 2, 'U:2', 'Diya Patel', 'diya@example.com', NULL, 'B-14, Vasant Vihar', NULL, 'New Delhi', 'Delhi', '110057', 'India', 1,
 'RAZORPAY', 'INR', 2307.20, 2398.00, 239.80, 149.00, 2307.20, 'WELCOME10', 'order_seed_s02', 'pay_seed_s02', 'SHIPPED', 'Blue Dart', 'BD558812090IN', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
('AC-S-SEED03', 5, 'U:5', 'Tanvi Kulkarni', 'tanvi.kulkarni@example.com', '+919822001144', 'Flat 7, Shaniwar Peth', NULL, 'Pune', 'Maharashtra', '411030', 'India', 1,
 'RAZORPAY', 'INR', 1848.00, 1699.00, 0.00, 149.00, 1848.00, NULL, 'order_seed_s03', 'pay_seed_s03', 'PACKED', NULL, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), CURRENT_TIMESTAMP),
('AC-S-SEED04', NULL, 'S:seed-guest-intl', 'Emma Lindqvist', 'emma.lindqvist@example.se', NULL, 'Hornsgatan 48', NULL, 'Stockholm', NULL, '118 21', 'Sweden', 1,
 'PAYPAL', 'USD', 33.31, 799.00, 0.00, 1999.00, 2798.00, NULL, 'PAYPAL-SEED04', 'CAPTURE-SEED04', 'PAID', NULL, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 HOUR), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 HOUR), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 HOUR)),
('AC-S-SEED05', 3, 'U:3', 'Ishita Bose', 'ishita.bose@example.com', NULL, '9 Lake Place', NULL, 'Kolkata', 'West Bengal', '700029', 'India', 1,
 'RAZORPAY', 'INR', 1348.00, 1199.00, 0.00, 149.00, 1348.00, NULL, 'order_seed_s05', NULL, 'PAYMENT_PENDING', NULL, NULL, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE));

INSERT INTO shop_order_items (order_id, painting_id, format, size_code, finish, frame, quantity, title, artist_name, image_url, variant_label, unit_price_inr, line_total_inr, discount_inr, edition_no, status) VALUES
('AC-S-SEED01', 2, 'CANVAS', 'M', 'STRETCHED', 'NONE', 1, 'The Old Mill at Dawn', 'Vaishali J Goswami', '/assets/images/course1.jpg', 'Limited edition canvas · 24 × 24 in · stretched', 6099.00, 6099.00, 0.00, 1, 'OK'),
('AC-S-SEED02', 1, 'POSTER', 'M', 'MATTE', 'NONE', 2, 'Scarlet Macaw Study', 'Vaishali J Goswami', '/assets/images/portfolio-2.jpg', 'Fine art poster · 18 × 13.5 in · matte', 1199.00, 2398.00, 239.80, NULL, 'OK'),
('AC-S-SEED03', 6, 'POSTER', 'L', 'MATTE', 'NONE', 1, 'Copper Kettle Still Life', 'Vaishali J Goswami', '/assets/images/portfolio-5.jpg', 'Fine art poster · 24 × 18 in · matte', 1699.00, 1699.00, 0.00, NULL, 'OK'),
('AC-S-SEED04', 3, 'POSTER', 'S', 'GLOSSY', 'NONE', 1, 'Lines of a Lifetime', 'Vaishali J Goswami', '/assets/images/course2.jpg', 'Fine art poster · 12 × 7.7 in · glossy', 799.00, 799.00, 0.00, NULL, 'OK'),
('AC-S-SEED05', 5, 'POSTER', 'M', 'MATTE', 'NONE', 1, 'Flameback Woodpecker', 'Vaishali J Goswami', '/assets/images/login-banner.jpg', 'Fine art poster · 18 × 15.8 in · matte', 1199.00, 1199.00, 0.00, NULL, 'PENDING');
UPDATE paintings SET editions_sold = 1 WHERE id = 2;

-- more coupons: an expired one and one for a single painting
INSERT INTO shop_coupons (code, description, discount_type, discount_value, painting_id, artist_name, min_subtotal, max_uses, used_count,
    starts_at, expires_at, is_active, created_at, updated_at) VALUES
('MONSOON15', 'Monsoon sale: 15% off (ended)', 'PERCENT', 15, NULL, NULL, 2000, 200, 37, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 120 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 60 DAY), 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 120 DAY), CURRENT_TIMESTAMP),
('MILL1000', 'Rs 1,000 off The Old Mill at Dawn', 'FLAT', 1000, 2, NULL, NULL, 10, 1, NULL, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY), 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
UPDATE shop_coupons SET used_count = 1 WHERE code = 'WELCOME10';

-- original-art orders: a delivered sale to a signed-in collector and an abandoned checkout
INSERT INTO marketplace_orders (order_id, painting_id, user_id, holder_key, buyer_name, buyer_email, buyer_phone, address_line1, address_line2, city, state, postal_code, country,
    gateway, currency, amount, price_inr, provider_ref, transaction_id, status, courier, tracking_number, paid_at, created_at, updated_at) VALUES
('AC-M-SEED03', 10, 4, 'U:4', 'Farhan Siddiqui', 'farhan.siddiqui@example.com', '+919812345670', '22 Hazratganj, Near GPO', NULL, 'Lucknow', 'Uttar Pradesh', '226001', 'India',
 'RAZORPAY', 'INR', 12000.00, 12000.00, 'order_seed_m03', NULL, 'CANCELLED', NULL, NULL, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 50 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 50 DAY)),
('AC-M-SEED04', 2, 3, 'U:3', 'Ishita Bose', 'ishita.bose@example.com', NULL, '9 Lake Place', NULL, 'Kolkata', 'West Bengal', '700029', 'India',
 'RAZORPAY', 'INR', 58500.00, 58500.00, 'order_seed_m04', NULL, 'EXPIRED', NULL, NULL, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY));

-- ---- custom commissions, one per stage --------------------------------------------------------
INSERT INTO commission_orders (order_id, user_id, customer_name, customer_email, customer_phone, fulfillment_type, special_instructions, uploaded_photo_url,
    shipping_address_line1, shipping_city, shipping_postal_code, shipping_country, payment_method, payment_provider_ref, payment_transaction_id, order_status, currency, total_price, created_at, updated_at) VALUES
('AC-SEED-C01', 2, 'Diya Patel', 'diya@example.com', NULL, 'ORIGINAL_PAINTING', 'Portrait of my grandmother for her 80th birthday. Please keep her smile and the red bindi.', '/assets/images/portfolio-4.jpg',
 'B-14, Vasant Vihar', 'New Delhi', '110057', 'India', 'RAZORPAY_UPI', 'order_seed_c01', 'pay_seed_c01', 'IN_PRODUCTION', 'INR', 57790.00, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 12 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 DAY)),
('AC-SEED-C02', 4, 'Farhan Siddiqui', 'farhan.siddiqui@example.com', '+919812345670', 'PHYSICAL_PRINT', 'Our family house in Lucknow, from the photo I uploaded. Warm evening light please.', '/assets/images/portfolio-6.jpg',
 '22 Hazratganj, Near GPO', 'Lucknow', '226001', 'India', 'RAZORPAY_UPI', 'order_seed_c02', 'pay_seed_c02', 'PROOF_SENT', 'INR', 12100.00, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 8 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)),
('AC-SEED-C03', NULL, 'Liam O''Connor', 'liam.oconnor@example.ie', NULL, 'DIGITAL_ONLY', 'Charcoal sketch of our dog Biscuit for a phone wallpaper.', '/assets/images/portfolio-1.jpg',
 NULL, NULL, NULL, NULL, 'PAYPAL', 'PAYPAL-SEED-C03', 'CAPTURE-SEED-C03', 'COMPLETED', 'USD', 39.20, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 40 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 33 DAY)),
('AC-SEED-C04', 5, 'Tanvi Kulkarni', 'tanvi.kulkarni@example.com', '+919822001144', 'ORIGINAL_PAINTING', 'A watercolour of the Pune hills for my parents'' anniversary.', '/assets/images/portfolio-3.jpg',
 'Flat 7, Shaniwar Peth', 'Pune', '411030', 'India', 'RAZORPAY_UPI', 'order_seed_c04', NULL, 'PAYMENT_PENDING', 'INR', 38060.00, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 MINUTE), NULL);
INSERT INTO commission_artwork_items (item_id, order_id, artwork_theme, width_inches, height_inches, frame_material, has_matting) VALUES
('AC-SEED-C01-1', 'AC-SEED-C01', 'PORTRAIT', 16, 20, 'WARM_WALNUT', 1),
('AC-SEED-C02-1', 'AC-SEED-C02', 'LANDSCAPE', 12, 16, 'NONE', 0),
('AC-SEED-C03-1', 'AC-SEED-C03', 'CHARCOAL', 8, 10, 'NONE', 0),
('AC-SEED-C04-1', 'AC-SEED-C04', 'WATERCOLOR', 12, 16, 'GALLERY_WHITE', 1);

-- ---- "Meet the artist" conversations ------------------------------------------------------------
INSERT INTO artist_threads (painting_id, user_id, owner_key, buyer_name, buyer_email, context, status, staff_unread, buyer_unread, last_message_at, created_at) VALUES
(1, 2, 'U:2', 'Diya Patel', 'diya@example.com', 'Original', 'OPEN', 1, 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 HOUR), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)),
(2, 4, 'U:4', 'Farhan Siddiqui', 'farhan.siddiqui@example.com', 'Canvas print', 'CLOSED', 0, 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 20 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 22 DAY)),
(11, NULL, 'S:seed-guest-intl', 'Emma Lindqvist', 'emma.lindqvist@example.se', 'Original', 'OPEN', 0, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 HOUR), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 HOUR));
INSERT INTO artist_thread_messages (thread_id, sender, admin_id, body, created_at) VALUES
(1, 'BUYER', NULL, 'Hello! Is the macaw study on archival paper, and can it ship to Delhi before the 20th?', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 HOUR)),
(2, 'BUYER', NULL, 'Would the canvas print of The Old Mill work in a 24 inch space above a sofa?', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 22 DAY)),
(2, 'ARTIST', 2, 'Yes, the medium canvas is 24 x 24 inches. Leave a hand''s width each side and it will sit well.', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 20 DAY)),
(3, 'BUYER', NULL, 'Could you ship this original to Sweden? Happy to pay for tracked shipping.', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 HOUR)),
(3, 'ARTIST', 1, 'Yes, we ship originals worldwide with tracking. The studio will confirm the cost by email today.', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 HOUR));

-- ---- blog: two more topics, three more tags, two more stories (one still a draft) ---------------
INSERT INTO categories (name, slug, status, created_at, updated_at) VALUES
('Materials & Tools', 'materials-and-tools', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Artist Interviews', 'artist-interviews', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO tags (name, slug, status, created_at, updated_at) VALUES
('florals', 'florals', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('landscapes', 'landscapes', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('studio life', 'studio-life', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO posts (title, subtitle, slug, body, status, posted_by, author_id, image, featured, `like`, dislike, read_minutes, published_at, created_at, updated_at) VALUES
('How to Stretch Watercolour Paper', 'Flat paper, every time', 'how-to-stretch-watercolour-paper',
 '<p>Buckling paper ruins washes. Soak a sheet of 300gsm paper for five minutes, lay it on a board, sponge off the extra water and tape all four edges with gummed tape.</p><h2>What you need</h2><ul><li>Gummed paper tape</li><li>A wooden or MDF board</li><li>A clean sponge</li></ul><p>Leave it to dry flat overnight and it will stay drum-tight while you paint.</p>',
 1, 2, 2, 'blog-4.jpg', 0, 0, 0, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
('In the Studio with Rajat Malhotra', 'Six hundred portraits later', 'in-the-studio-with-rajat-malhotra',
 '<p>Rajat talks about measuring, why he still starts every portrait with a plumb line, and the charcoal he cannot work without.</p>',
 0, 4, 4, 'blog-5.jpg', 0, 0, 0, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO category_post (category_id, post_id, created_at, updated_at) VALUES
(4, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO post_tag (post_id, tag_id, created_at, updated_at) VALUES
(3, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
UPDATE posts SET author_id = posted_by WHERE author_id IS NULL;

-- ---- site content: testimonials, free lessons, enquiries, newsletter --------------------------
INSERT INTO testimonials (name, slug, title, photo, description, status, created_at, updated_at) VALUES
('Tanvi Kulkarni', 'tanvi-kulkarni', 'Watercolour Landscapes, certified', 'avatar.png', 'I came in painting muddy skies and left with a certificate and my first sale in the marketplace.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Sneha Reddy', 'sneha-reddy', 'Parent of a young learner', 'avatar.png', 'My 11-year-old looks forward to every class. The teachers are patient and the projects are just the right size.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Daniel Brooks', 'daniel-brooks', 'Learner from Toronto', 'avatar.png', 'The live sessions fit my evenings in Canada and the recordings meant I never missed a demo.', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO free_courses (admin_id, title, slug, description, status, photo, video_url, created_at, updated_at) VALUES
(2, 'Holding a Pencil for Sketching', 'holding-a-pencil-for-sketching', 'Tripod, overhand and underhand grips, and when to use each for line and shading.', 1, 'free.jpg', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'Mixing Greens for Landscapes', 'mixing-greens-for-landscapes', 'Mix natural greens from two blues and two yellows instead of using green straight from the tube.', 1, 'free.jpg', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'Five-Value Thumbnails', 'five-value-thumbnails', 'Plan any painting with a two-minute value sketch before you start.', 0, 'free.jpg', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO contacts (name, mobile_no, email, message, status, read_at, created_at, updated_at) VALUES
('Kunal Deshpande', '+919850011223', 'kunal.d@example.com', 'Do you run weekend classes for children aged 8 to 10? I would like to enrol my son.', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
('Aisha Khan', NULL, 'aisha.khan@example.com', 'Our office wants a 2-hour team painting workshop for 25 people in Hyderabad. Is that possible?', 1, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 HOUR), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 HOUR)),
('Robert Fernandes', '+919900887766', 'robert.f@example.com', 'The print I received is perfect. Can I order the same canvas in a larger size?', 1, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)),
('Meghna Iyer', NULL, 'meghna.iyer@example.com', 'I missed the live class on Saturday. Will there be a recording?', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 DAY));

INSERT INTO newsletters (email, source, unsubscribed_at, created_at, updated_at) VALUES
('student@artistikcity.com', 'signup', NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 60 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 60 DAY)),
('diya@example.com', 'footer', NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)),
('kunal.d@example.com', 'footer', NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY)),
('emma.lindqvist@example.se', 'checkout', NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 HOUR), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 HOUR)),
('farhan.siddiqui@example.com', 'footer', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 40 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 DAY));

-- ---- offers on originals: one in each state (Diya waiting, Farhan countered, Ishita declined, Aarav expired) --------------
UPDATE paintings SET offers_enabled = 1 WHERE id = 11;
INSERT INTO painting_offers (painting_id, user_id, buyer_name, buyer_email, list_price_inr, amount_inr, counter_inr, agreed_inr, status, round_no, message,
    expires_at, pay_by, closed_reason, buyer_unread, seller_unread, created_at, updated_at) VALUES
(2, 2, 'Diya Patel', 'diya@example.com', 58500, 47000, NULL, NULL, 'OPEN', 1, 'This would be the centrepiece of our new living room. Would you consider ₹47,000?',
 DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 48 HOUR), NULL, NULL, 0, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)),
(11, 4, 'Farhan Siddiqui', 'farhan.siddiqui@example.com', 4200, 3200, 3800, NULL, 'COUNTERED', 1, 'Lovely piece. I collect Pune cityscapes.',
 DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 60 HOUR), NULL, NULL, 1, 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 20 HOUR), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 12 HOUR)),
(3, 3, 'Ishita Bose', 'ishita.bose@example.com', 42000, 25000, NULL, NULL, 'DECLINED', 1, NULL,
 NULL, NULL, NULL, 1, 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY)),
(6, 1, 'Aarav Sharma', 'student@artistikcity.com', 9025, 7000, NULL, NULL, 'EXPIRED', 1, NULL,
 NULL, NULL, NULL, 0, 0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 DAY));
INSERT INTO painting_offer_events (offer_id, actor, actor_name, kind, amount_inr, note, created_at) VALUES
(1, 'BUYER', 'Diya Patel', 'OFFER', 47000, 'This would be the centrepiece of our new living room. Would you consider ₹47,000?', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)),
(2, 'BUYER', 'Farhan Siddiqui', 'OFFER', 3200, 'Lovely piece. I collect Pune cityscapes.', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 20 HOUR)),
(2, 'SELLER', 'Tanvi Kulkarni', 'COUNTER', 3800, 'Thank you! It took me three evenings on site; I can do ₹3,800.', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 12 HOUR)),
(3, 'BUYER', 'Ishita Bose', 'OFFER', 25000, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 DAY)),
(3, 'STAFF', 'Site Admin', 'DECLINE', NULL, 'Thank you for the offer. This piece is one of Vaishali''s favourites, so it stays at its price.', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY)),
(4, 'BUYER', 'Aarav Sharma', 'OFFER', 7000, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 DAY)),
(4, 'SYSTEM', NULL, 'EXPIRE', NULL, 'No answer in time.', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 DAY));


-- ---------------------------------------------------------------------
-- Landing Page Management demo (landing pages 1-2)
-- 1: Watercolour Landscapes for Beginners, PUBLISHED at the course's own address (/course/watercolour-landscapes-for-beginners),
--    custom payments: UPI (+QR, intent, GPay, PhonePe, Paytm), credit/debit cards, net banking, international cards, PayPal; wallets and EMI off.
--    Early-bird price until 30 Nov 2026. Coupon WATERCOLOUR10 (10%, this page only).
-- 2: Acrylic portrait festive campaign page, DRAFT at /course/acrylic-portrait-festive-masterclass, UPI and cards only.
-- FIRSTCOURSE: Rs 500 off a first course, all landing pages.
-- ---------------------------------------------------------------------
INSERT INTO landing_pages (id, name, slug, course_id, campaign, description, template_key, status, draft_config, draft_saved_at, draft_saved_by, published_version_id, published_at, created_by, created_at, updated_at) VALUES
(1, 'Watercolour Landscapes - main page', 'watercolour-landscapes-for-beginners', 1, NULL, 'The course page for the December batch.', 'artist-showcase', 'PUBLISHED', '{"schema": 1, "design": {"palette": "burgundy", "accent": "#9C0D48", "ink": "#14213D", "surface": "#FBF8F4", "font": "jakarta", "radius": "soft"}, "sections": [{"id": "hero-wc", "type": "hero", "enabled": true, "layout": "split", "background": "tint", "hideOnMobile": false, "content": {"eyebrow": "", "heading": "", "subheading": "Learn washes, layering and colour mixing to paint glowing landscapes, live with a working artist.", "body": "", "image": "", "imageAlt": "Watercolour painting of an old mill beside a mossy stream", "video": "", "language": "English, Hindi", "items": [{"title": "Live classes with the artist", "text": ""}, {"title": "Personal feedback on your work", "text": ""}, {"title": "Certificate of completion", "text": ""}]}}, {"id": "intro-wc", "type": "intro", "enabled": true, "layout": "columns", "background": "plain", "hideOnMobile": false, "content": {"heading": "Paint luminous landscapes, one wash at a time", "body": "Watercolour rewards patience and a plan. In four weeks you''ll learn how water and pigment behave, how to plan a painting from light to dark, and how to finish three complete landscapes you''ll be proud to frame.\\n\\nEvery class is live and small, so you can ask questions as you paint and get feedback on the work you share.", "items": [{"title": "Who it''s for", "text": "Complete beginners and returning painters aged 12+. No drawing experience needed."}, {"title": "What it fixes", "text": "Muddy colours, hard edges where you wanted soft ones, and paintings that never feel finished."}, {"title": "What you finish with", "text": "Three finished landscapes, a personal colour chart and a certificate."}]}}, {"id": "outcomes-wc", "type": "outcomes", "enabled": true, "layout": "cards", "background": "tint", "hideOnMobile": false, "content": {"heading": "What you''ll be able to do", "items": [{"title": "Control water and pigment", "text": "Judge how wet the paper and brush are, and use it on purpose."}, {"title": "Lay flat, graded and wet-in-wet washes", "text": "The three washes behind almost every landscape sky."}, {"title": "Mix clean, glowing colour", "text": "Avoid mud with a limited palette and a simple mixing order."}, {"title": "Plan light to dark", "text": "Save your whites and build depth in layers."}, {"title": "Paint skies, water and trees", "text": "Reusable shapes and strokes for the elements of a landscape."}, {"title": "Finish and present a painting", "text": "Know when to stop, then sign, mount and photograph your work."}]}}, {"id": "curriculum-wc", "type": "curriculum", "enabled": true, "layout": "accordion", "background": "plain", "hideOnMobile": false, "content": {"heading": "What we''ll cover, week by week"}}, {"id": "gallery-wc", "type": "gallery", "enabled": true, "layout": "masonry", "background": "tint", "hideOnMobile": false, "content": {"heading": "Made in the studio", "body": "Paintings from the course and from students who have learned with us.", "items": [], "beforeImage": "", "afterImage": ""}}, {"id": "artist-wc", "type": "artist", "enabled": true, "layout": "portrait", "background": "plain", "hideOnMobile": false, "content": {"heading": "Learn with Vaishali", "body": "", "ids": [], "items": [], "socials": [], "video": ""}}, {"id": "benefits-wc", "type": "benefits", "enabled": true, "layout": "grid", "background": "tint", "hideOnMobile": false, "content": {"heading": "Everything that''s included", "items": []}}, {"id": "testimonials-wc", "type": "testimonials", "enabled": true, "layout": "cards", "background": "plain", "hideOnMobile": false, "content": {"heading": "What students say", "source": "reviews", "ids": []}}, {"id": "pricing-wc", "type": "pricing", "enabled": true, "layout": "split", "background": "tint", "hideOnMobile": false, "content": {"heading": "Join the December batch", "body": "Small live groups, so every student gets feedback. Seats are limited by the size of the class."}}, {"id": "faq-wc", "type": "faq", "enabled": true, "layout": "accordion", "background": "plain", "hideOnMobile": false, "content": {"heading": "Questions, answered", "items": [{"title": "Do I need any experience?", "text": "No. We start with materials and simple washes. If you can hold a brush, you can take this course."}, {"title": "What materials do I need?", "text": "A small set of artist-quality watercolours, two round brushes, cold-pressed watercolour paper and a water pot. You''ll get the full list with links when you enrol."}, {"title": "What if I miss a live class?", "text": "Each class is recorded and shared within a day, and you can post your painting for feedback any time during the course."}, {"title": "How do I get the certificate?", "text": "Submit your three finished landscapes; once they''re approved, the certificate is issued in your studio."}, {"title": "Can I pay from outside India?", "text": "Yes. International cards are accepted, or you can pay in US dollars with PayPal."}]}}, {"id": "related-wc", "type": "related", "enabled": true, "layout": "cards", "background": "plain", "hideOnMobile": false, "content": {"heading": "Keep painting"}}, {"id": "final-wc", "type": "final_cta", "enabled": true, "layout": "artwork", "background": "plain", "hideOnMobile": false, "content": {"heading": "Your first glowing landscape is four weeks away", "body": "Live classes start on 7 December. Join now and we''ll send the materials list straight away.", "image": ""}}], "cta": {"primary": "Enroll now", "secondary": "See the curriculum", "sticky": true}, "seo": {"title": "Watercolour Landscapes for Beginners · Live course | ArtistikCity", "description": "Learn washes, layering and colour mixing in four weeks of live classes with artist Vaishali J Goswami. Small groups, personal feedback and a certificate.", "canonical": "", "ogTitle": "", "ogDescription": "", "ogImage": "", "index": true, "sitemap": true, "structuredData": true}, "marketing": {"ga4": "", "gtm": "", "metaPixel": "", "whatsapp": "", "leadCapture": false}}', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), 1, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
(2, 'Acrylic portraits - festive campaign', 'acrylic-portrait-festive-masterclass', 2, 'Festive 2026', 'Campaign page for the festive newsletter and Instagram ads.', 'premium-masterclass', 'DRAFT', '{"schema": 1, "design": {"palette": "ink", "accent": "#C8A15A", "ink": "#111318", "surface": "#F6F3EE", "font": "serif", "radius": "sharp"}, "sections": [{"id": "hero-ac", "type": "hero", "enabled": true, "layout": "immersive", "background": "tint", "hideOnMobile": false, "content": {"eyebrow": "Festive masterclass · limited seats", "heading": "Paint portraits that feel alive", "subheading": "Capture likeness, skin tones and light with fast-drying acrylics.", "body": "", "image": "", "imageAlt": "", "video": "", "language": "English, Hindi", "items": [{"title": "Live classes", "text": ""}, {"title": "Feedback on every portrait", "text": ""}]}}, {"id": "outcomes-ac", "type": "outcomes", "enabled": true, "layout": "list", "background": "plain", "hideOnMobile": false, "content": {"heading": "What you''ll learn", "items": []}}, {"id": "curriculum-ac", "type": "curriculum", "enabled": true, "layout": "accordion", "background": "tint", "hideOnMobile": false, "content": {"heading": "Curriculum"}}, {"id": "artist-ac", "type": "artist", "enabled": true, "layout": "portrait", "background": "plain", "hideOnMobile": false, "content": {"heading": "Your artist", "body": "", "ids": [], "items": [], "socials": [], "video": ""}}, {"id": "pricing-ac", "type": "pricing", "enabled": true, "layout": "card", "background": "tint", "hideOnMobile": false, "content": {"heading": "Reserve your seat", "body": ""}}, {"id": "faq-ac", "type": "faq", "enabled": true, "layout": "accordion", "background": "plain", "hideOnMobile": false, "content": {"heading": "FAQ", "items": []}}, {"id": "final-ac", "type": "final_cta", "enabled": true, "layout": "band", "background": "plain", "hideOnMobile": false, "content": {"heading": "Ready to paint your first portrait?", "body": "", "image": ""}}], "cta": {"primary": "Enroll now", "secondary": "See the curriculum", "sticky": true}, "seo": {"title": "", "description": "", "canonical": "", "ogTitle": "", "ogDescription": "", "ogImage": "", "index": true, "sitemap": true, "structuredData": true}, "marketing": {"ga4": "", "gtm": "", "metaPixel": "", "whatsapp": "", "leadCapture": false}}', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), 1, NULL, NULL, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY));
INSERT INTO landing_page_versions (id, landing_page_id, version_no, template_key, config, note, created_by, created_at) VALUES
(1, 1, 1, 'artist-showcase', '{"schema": 1, "design": {"palette": "burgundy", "accent": "#9C0D48", "ink": "#14213D", "surface": "#FBF8F4", "font": "jakarta", "radius": "soft"}, "sections": [{"id": "hero-wc", "type": "hero", "enabled": true, "layout": "split", "background": "tint", "hideOnMobile": false, "content": {"eyebrow": "", "heading": "", "subheading": "Learn washes, layering and colour mixing to paint glowing landscapes, live with a working artist.", "body": "", "image": "", "imageAlt": "Watercolour painting of an old mill beside a mossy stream", "video": "", "language": "English, Hindi", "items": [{"title": "Live classes with the artist", "text": ""}, {"title": "Personal feedback on your work", "text": ""}, {"title": "Certificate of completion", "text": ""}]}}, {"id": "intro-wc", "type": "intro", "enabled": true, "layout": "columns", "background": "plain", "hideOnMobile": false, "content": {"heading": "Paint luminous landscapes, one wash at a time", "body": "Watercolour rewards patience and a plan. In four weeks you''ll learn how water and pigment behave, how to plan a painting from light to dark, and how to finish three complete landscapes you''ll be proud to frame.\\n\\nEvery class is live and small, so you can ask questions as you paint and get feedback on the work you share.", "items": [{"title": "Who it''s for", "text": "Complete beginners and returning painters aged 12+. No drawing experience needed."}, {"title": "What it fixes", "text": "Muddy colours, hard edges where you wanted soft ones, and paintings that never feel finished."}, {"title": "What you finish with", "text": "Three finished landscapes, a personal colour chart and a certificate."}]}}, {"id": "outcomes-wc", "type": "outcomes", "enabled": true, "layout": "cards", "background": "tint", "hideOnMobile": false, "content": {"heading": "What you''ll be able to do", "items": [{"title": "Control water and pigment", "text": "Judge how wet the paper and brush are, and use it on purpose."}, {"title": "Lay flat, graded and wet-in-wet washes", "text": "The three washes behind almost every landscape sky."}, {"title": "Mix clean, glowing colour", "text": "Avoid mud with a limited palette and a simple mixing order."}, {"title": "Plan light to dark", "text": "Save your whites and build depth in layers."}, {"title": "Paint skies, water and trees", "text": "Reusable shapes and strokes for the elements of a landscape."}, {"title": "Finish and present a painting", "text": "Know when to stop, then sign, mount and photograph your work."}]}}, {"id": "curriculum-wc", "type": "curriculum", "enabled": true, "layout": "accordion", "background": "plain", "hideOnMobile": false, "content": {"heading": "What we''ll cover, week by week"}}, {"id": "gallery-wc", "type": "gallery", "enabled": true, "layout": "masonry", "background": "tint", "hideOnMobile": false, "content": {"heading": "Made in the studio", "body": "Paintings from the course and from students who have learned with us.", "items": [], "beforeImage": "", "afterImage": ""}}, {"id": "artist-wc", "type": "artist", "enabled": true, "layout": "portrait", "background": "plain", "hideOnMobile": false, "content": {"heading": "Learn with Vaishali", "body": "", "ids": [], "items": [], "socials": [], "video": ""}}, {"id": "benefits-wc", "type": "benefits", "enabled": true, "layout": "grid", "background": "tint", "hideOnMobile": false, "content": {"heading": "Everything that''s included", "items": []}}, {"id": "testimonials-wc", "type": "testimonials", "enabled": true, "layout": "cards", "background": "plain", "hideOnMobile": false, "content": {"heading": "What students say", "source": "reviews", "ids": []}}, {"id": "pricing-wc", "type": "pricing", "enabled": true, "layout": "split", "background": "tint", "hideOnMobile": false, "content": {"heading": "Join the December batch", "body": "Small live groups, so every student gets feedback. Seats are limited by the size of the class."}}, {"id": "faq-wc", "type": "faq", "enabled": true, "layout": "accordion", "background": "plain", "hideOnMobile": false, "content": {"heading": "Questions, answered", "items": [{"title": "Do I need any experience?", "text": "No. We start with materials and simple washes. If you can hold a brush, you can take this course."}, {"title": "What materials do I need?", "text": "A small set of artist-quality watercolours, two round brushes, cold-pressed watercolour paper and a water pot. You''ll get the full list with links when you enrol."}, {"title": "What if I miss a live class?", "text": "Each class is recorded and shared within a day, and you can post your painting for feedback any time during the course."}, {"title": "How do I get the certificate?", "text": "Submit your three finished landscapes; once they''re approved, the certificate is issued in your studio."}, {"title": "Can I pay from outside India?", "text": "Yes. International cards are accepted, or you can pay in US dollars with PayPal."}]}}, {"id": "related-wc", "type": "related", "enabled": true, "layout": "cards", "background": "plain", "hideOnMobile": false, "content": {"heading": "Keep painting"}}, {"id": "final-wc", "type": "final_cta", "enabled": true, "layout": "artwork", "background": "plain", "hideOnMobile": false, "content": {"heading": "Your first glowing landscape is four weeks away", "body": "Live classes start on 7 December. Join now and we''ll send the materials list straight away.", "image": ""}}], "cta": {"primary": "Enroll now", "secondary": "See the curriculum", "sticky": true}, "seo": {"title": "Watercolour Landscapes for Beginners · Live course | ArtistikCity", "description": "Learn washes, layering and colour mixing in four weeks of live classes with artist Vaishali J Goswami. Small groups, personal feedback and a certificate.", "canonical": "", "ogTitle": "", "ogDescription": "", "ogImage": "", "index": true, "sitemap": true, "structuredData": true}, "marketing": {"ga4": "", "gtm": "", "metaPixel": "", "whatsapp": "", "leadCapture": false}}', 'First version', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY));
UPDATE landing_pages SET published_version_id = 1 WHERE id = 1;
INSERT INTO landing_pricing (landing_page_id, list_price, price, price_usd, promo_label, promo_price, promo_starts_at, promo_ends_at, early_price, early_ends_at, coupons_enabled, tax_mode, tax_rate, tax_label, access_duration, refund_policy, inclusions, updated_by, updated_at) VALUES
(1, NULL, 4999, 69, NULL, NULL, NULL, NULL, 3999, '2026-11-30 23:59:00', 1, 'INCLUDED', 18, 'GST', 'Recordings and materials for 12 months', 'Full refund if you cancel before the first class. After that, a refund for the classes not yet held, within 7 days of the first class.', 'Eight live classes, 2 hours each\nRecordings of every class\nMaterials list and reference photos\nFeedback on your paintings\nCertificate of completion', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
(2, NULL, 7499, 99, 'Festive offer', 6499, '2026-11-01 00:00:00', '2026-11-20 23:59:00', NULL, NULL, 1, 'INCLUDED', 18, 'GST', 'Recordings for 12 months', NULL, 'Twelve live classes\nFeedback on every portrait', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY));
INSERT INTO landing_checkout (landing_page_id, require_phone, require_address, require_terms, require_privacy, show_coupon, show_summary, success_message, success_redirect, failure_message, pending_message, updated_by, updated_at) VALUES
(1, 1, 0, 1, 0, 1, 1, 'Welcome to the class! The materials list is on its way to your inbox.', NULL, NULL, NULL, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
(2, 1, 0, 1, 1, 1, 1, NULL, NULL, NULL, NULL, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY));
INSERT INTO landing_payment_settings (landing_page_id, mode, default_provider, updated_by, updated_at) VALUES
(1, 'CUSTOM', 'razorpay', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
(2, 'CUSTOM', 'razorpay', 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY));
INSERT INTO landing_payment_providers (landing_page_id, provider, enabled, priority) VALUES
(1, 'razorpay', 1, 1), (1, 'paypal', 1, 2), (1, 'cashfree', 0, 3), (1, 'payu', 0, 4),
(2, 'razorpay', 1, 1), (2, 'paypal', 0, 2), (2, 'cashfree', 0, 3), (2, 'payu', 0, 4);
INSERT INTO landing_payment_methods (landing_page_id, method, enabled, sort_order) VALUES
(1, 'upi', 1, 0),
(1, 'upi_qr', 1, 1),
(1, 'upi_intent', 1, 2),
(1, 'gpay', 1, 3),
(1, 'phonepe', 1, 4),
(1, 'paytm', 1, 5),
(1, 'credit_card', 1, 6),
(1, 'debit_card', 1, 7),
(1, 'visa', 0, 8),
(1, 'mastercard', 0, 9),
(1, 'rupay', 0, 10),
(1, 'netbanking', 1, 11),
(1, 'wallets', 0, 12),
(1, 'emi', 0, 13),
(1, 'nocost_emi', 0, 14),
(1, 'intl_cards', 1, 15),
(1, 'paypal', 1, 16),
(2, 'upi', 1, 0),
(2, 'upi_qr', 1, 1),
(2, 'upi_intent', 1, 2),
(2, 'gpay', 0, 3),
(2, 'phonepe', 0, 4),
(2, 'paytm', 0, 5),
(2, 'credit_card', 1, 6),
(2, 'debit_card', 1, 7),
(2, 'visa', 0, 8),
(2, 'mastercard', 0, 9),
(2, 'rupay', 0, 10),
(2, 'netbanking', 0, 11),
(2, 'wallets', 0, 12),
(2, 'emi', 0, 13),
(2, 'nocost_emi', 0, 14),
(2, 'intl_cards', 0, 15),
(2, 'paypal', 0, 16);
INSERT INTO landing_coupons (id, code, description, kind, value, campaign, all_pages, min_amount, max_uses, per_user_limit, first_time_only, starts_at, expires_at, is_active, used_count, created_by, created_at, updated_at) VALUES
(1, 'WATERCOLOUR10', '10% off the watercolour course page', 'PERCENT', 10, 'Newsletter November', 0, NULL, 100, 1, 0, NULL, '2026-12-06 23:59:00', 1, 0, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
(2, 'FIRSTCOURSE', 'Rs 500 off your first course with us', 'FLAT', 500, NULL, 1, 2000, NULL, 1, 1, NULL, NULL, 1, 0, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY)),
(3, 'SUMMER25', 'Old summer code (ended)', 'PERCENT', 25, 'Summer 2026', 1, NULL, NULL, NULL, 0, NULL, '2026-08-31 23:59:00', 0, 0, 1, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 90 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 60 DAY));
INSERT INTO landing_coupon_targets (coupon_id, target_type, target_id) VALUES (1, 'PAGE', 1);
