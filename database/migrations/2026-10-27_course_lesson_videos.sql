-- =====================================================================
--  Separate videos for the course page and for each lesson. Safe to run more than once.
--   * courses.intro_video_url             : the trailer / landing video on the public course page
--   * course_module_lessons.video_url     : each lesson's own video in the classroom
--  Both hold either a link (https://... YouTube, Vimeo or a video file) or an uploaded file
--  (/storage/uploads/courses/<id>/videos/<file>.mp4).
--
--  Before this change a course had one shared list (course_videos): the public page showed the first
--  one and lesson N in the classroom played video N. When the columns are first added, that is copied
--  over so students keep seeing the same videos: the first video becomes the intro video and lesson N
--  (in curriculum order) gets video N. course_videos is kept but no longer used.
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

SET @ac_new_intro = (SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'courses' AND column_name = 'intro_video_url');
SET @ac_new_lesson = (SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'course_module_lessons' AND column_name = 'video_url');

CALL ac_exec_if(@ac_new_intro, 'ALTER TABLE courses ADD COLUMN intro_video_url VARCHAR(1000) NULL AFTER course_based');
CALL ac_exec_if(@ac_new_lesson, 'ALTER TABLE course_module_lessons ADD COLUMN video_url VARCHAR(1000) NULL AFTER lesson_pdf');

-- one-time copy from the old shared list (only right after the columns were created)
CALL ac_exec_if(@ac_new_intro,
    'UPDATE courses c JOIN (SELECT v.course_id, v.video_url FROM course_videos v
        JOIN (SELECT course_id, MIN(id) AS first_id FROM course_videos WHERE status = 1 AND video_url IS NOT NULL AND video_url <> '''' GROUP BY course_id) f
          ON f.first_id = v.id) x ON x.course_id = c.id
     SET c.intro_video_url = x.video_url');
CALL ac_exec_if(@ac_new_lesson,
    'UPDATE course_module_lessons l JOIN (
        SELECT ls.id AS lesson_id, vs.video_url FROM
          (SELECT l2.id, l2.course_id, ROW_NUMBER() OVER (PARTITION BY l2.course_id ORDER BY m.sort_order, m.id, l2.sort_order, l2.id) AS n
             FROM course_module_lessons l2 JOIN course_modules m ON m.id = l2.module_id
            WHERE l2.status = ''1'' AND m.status = ''1'') ls
        JOIN
          (SELECT course_id, video_url, ROW_NUMBER() OVER (PARTITION BY course_id ORDER BY id) AS n
             FROM course_videos WHERE status = 1 AND video_url IS NOT NULL AND video_url <> '''') vs
          ON vs.course_id = ls.course_id AND vs.n = ls.n) x ON x.lesson_id = l.id
     SET l.video_url = x.video_url');

DROP PROCEDURE IF EXISTS ac_exec_if;
