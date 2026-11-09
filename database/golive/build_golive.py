"""Builds the go-live starter data: database/golive/golive-seed.sql and the files it points to under
database/golive/files/uploads/ (copied to storage / S3 at the same paths by GO-LIVE-RESET).

Six sample courses (four courses, two workshops) with every course field filled, three modules of two lessons each,
a task per module, a final project, two photos, a schedule PDF, a PDF per lesson, a trailer and a lesson video.
The photos are the studio's own artworks that already ship in storage/uploads/courses/1xx. Prices, dates and texts are
placeholders for testing: edit or hide the courses in Admin > Courses before announcing anything.

No reviews, testimonials, orders or enrolments are made up. Accounts come from the variables the reset sets first:
@ga_admin_email, @ga_admin_name, @ga_admin_hash, @ga_student_email, @ga_student_hash.

Run from the repo root (needs Pillow, reportlab and ffmpeg):  python3 database/golive/build_golive.py
"""
import json, os, re, shutil, subprocess, sys
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'database', 'golive')
FILES = os.path.join(OUT, 'files', 'uploads')
SRC = os.path.join(ROOT, 'storage', 'uploads')
SEED = open(os.path.join(ROOT, 'database', 'seed.sql'), encoding='utf-8').read()


def q(v):
    if v is None:
        return 'NULL'
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace('\\', '\\\\').replace("'", "''") + "'"


def first_insert(table):
    """The first INSERT INTO <table> ... ; block of seed.sql (the reference lists)."""
    m = re.search(r'^INSERT INTO ' + table + r' \(.*?;\s*$', SEED, re.S | re.M)
    if not m:
        sys.exit('seed.sql has no INSERT INTO ' + table)
    return m.group(0).strip()


AGE = {'sub': 'Sub-junior - 8 to 12 yrs', 'junior': 'Junior - 12 to 15 yrs', 'adult': 'Adult - 16 yrs and above'}
# seed ids: mediums 1 Watercolour 2 Acrylic 3 Pencil Sketch 4 Oil Painting 5 Charcoal 6 Digital Art; genres 1 Landscape
# 2 Portrait 3 Still Life 4 Abstract; skills 1 Beginner 2 Intermediate 3 Advanced; course types 1 Course 2 Workshop;
# categories 1 Art Tips 2 Techniques 3 Inspiration
COURSES = [
    dict(slug='watercolour-landscapes-for-beginners', title='Watercolour Landscapes for Beginners', type=1, medium=1, genre='1', skill=1, cat=2,
         age='adult', inr=4999, usd=69, start=21, weeks='4', sessions='8', projects='3', photos=[108, 101],
         sub='Learn washes, layering and colour mixing to paint glowing landscapes.',
         intro='A step-by-step introduction to painting landscapes in watercolour.',
         learn=['Flat, graded and wet-in-wet washes', 'Mixing clean greens and skies', 'Painting trees, water and light', 'Planning a finished landscape'],
         modules=[('Materials and first washes', ['Paper, brushes and paints', 'Three basic washes']),
                  ('Skies, trees and water', ['Painting skies', 'Trees and reflections']),
                  ('Your finished landscape', ['Planning the composition', 'Painting the final piece'])],
         project=('Final landscape', 'Paint a complete landscape that uses washes, layering and at least two of the techniques from the course.')),
    dict(slug='pencil-sketching-essentials', title='Pencil Sketching Essentials', type=1, medium=3, genre='3', skill=1, cat=2,
         age='junior', inr=2999, usd=39, start=28, weeks='4', sessions='6', projects='4', photos=[109, 111],
         sub='Build confident drawing skills with graphite pencils.',
         intro='Drawing fundamentals for everyone: lines, shapes, shading and perspective.',
         learn=['Holding the pencil and drawing confident lines', 'Seeing shapes and proportions', 'Shading with a value scale', 'One- and two-point perspective'],
         modules=[('Lines and shapes', ['Warm-up lines', 'Drawing from simple shapes']),
                  ('Light and shade', ['Building a value scale', 'Shading everyday objects']),
                  ('Perspective and still life', ['Perspective basics', 'A complete still life'])],
         project=('Still life sketch', 'Draw a still life of three objects from your home with full shading.')),
    dict(slug='charcoal-portrait-drawing', title='Charcoal Portrait Drawing', type=1, medium=5, genre='2', skill=2, cat=2,
         age='adult', inr=5999, usd=79, start=35, weeks='6', sessions='12', projects='4', photos=[104, 112],
         sub='Draw expressive portraits with proportion, value and texture.',
         intro='A guided course in drawing faces with charcoal, from proportions to a finished portrait.',
         learn=['Facial proportions and placement', 'Drawing eyes, nose and lips', 'Mapping values and soft edges', 'Hair, skin and texture with charcoal'],
         modules=[('Proportions of the face', ['The guideline method', 'Placing the features']),
                  ('Features in detail', ['Eyes', 'Nose, lips and ears']),
                  ('The finished portrait', ['Values and edges', 'Hair and final touches'])],
         project=('Portrait study', 'Draw a portrait from a reference photo using the guideline method and a full value range.')),
    dict(slug='acrylic-colour-and-texture', title='Acrylic Colour and Texture', type=1, medium=2, genre='4,1', skill=2, cat=3,
         age='adult', inr=6499, usd=89, start=42, weeks='6', sessions='10', projects='3', photos=[106, 107],
         sub='Explore bold colour, blending and texture with acrylics.',
         intro='Work with fast-drying acrylics: colour harmony, blending, glazing and textured surfaces.',
         learn=['Colour harmony and mixing', 'Blending before the paint dries', 'Glazes and layers', 'Texture with palette knives and mediums'],
         modules=[('Colour and mixing', ['The colour wheel in practice', 'Mixing harmonious palettes']),
                  ('Blending and layering', ['Wet blending', 'Glazing in layers']),
                  ('Texture and composition', ['Palette knife texture', 'An abstract composition'])],
         project=('Abstract in three colours', 'Paint an abstract piece with a limited palette and at least two texture techniques.')),
    dict(slug='weekend-watercolour-florals-workshop', title='Weekend Watercolour Florals Workshop', type=2, medium=1, genre='3', skill=1, cat=1,
         age='adult', inr=1499, usd=25, start=14, weeks='2', sessions='2', projects='2', photos=[101, 108], seats=20,
         sub='A relaxing two-day workshop painting loose florals.',
         intro='Paint loose, joyful flowers and leaves in watercolour over one weekend.',
         learn=['Loose brush strokes for petals', 'Leaves and stems in one stroke', 'Colour harmony for bouquets', 'A finished floral card'],
         modules=[('Day 1: strokes and petals', ['Brush strokes', 'Roses and peonies']),
                  ('Day 1: leaves', ['One-stroke leaves', 'Stems and buds']),
                  ('Day 2: bouquet', ['Arranging a bouquet', 'Finishing a floral card'])],
         project=('Floral card', 'Paint a small bouquet on a greeting card.')),
    dict(slug='bird-drawing-workshop-for-kids', title='Bird Drawing Workshop for Kids', type=2, medium=3, genre='1', skill=1, cat=1,
         age='sub', inr=999, usd=15, start=10, weeks='2', sessions='2', projects='2', photos=[102, 105], seats=20,
         sub='A fun two-day workshop drawing birds step by step.',
         intro='Children learn to draw birds from simple shapes and add feathers, texture and colour.',
         learn=['Drawing birds from circles and ovals', 'Beaks, eyes and feet', 'Feather patterns', 'Adding colour'],
         modules=[('Day 1: shapes', ['Birds from circles', 'Beaks and eyes']),
                  ('Day 1: details', ['Feathers', 'Feet and branches']),
                  ('Day 2: colour', ['Colour pencils', 'A finished bird drawing'])],
         project=('My bird', 'Draw and colour a bird sitting on a branch.')),
]


def pdf(path, title, lines):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    c = canvas.Canvas(path, pagesize=A4)
    c.setTitle(title)
    c.setFillColorRGB(0.61, 0.05, 0.28)
    c.rect(0, A4[1] - 30 * mm, A4[0], 30 * mm, fill=1, stroke=0)
    c.setFillColorRGB(1, 1, 1)
    c.setFont('Helvetica-Bold', 18)
    c.drawString(20 * mm, A4[1] - 19 * mm, 'ArtistikCity')
    c.setFillColorRGB(0.17, 0.24, 0.31)
    c.setFont('Helvetica-Bold', 16)
    c.drawString(20 * mm, A4[1] - 45 * mm, title[:80])
    c.setFont('Helvetica', 11)
    y = A4[1] - 58 * mm
    for ln in lines:
        c.drawString(20 * mm, y, ln[:95])
        y -= 7 * mm
    c.setFont('Helvetica-Oblique', 9)
    c.setFillColorRGB(0.29, 0.35, 0.40)
    c.drawString(20 * mm, 15 * mm, 'Sample document for testing. Replace it in Admin > Courses.')
    c.save()


def video(path, label):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    font = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
    text = label.replace(':', '\\:').replace("'", '')
    draw = (f"drawtext=fontfile={font}:text='{text}':fontcolor=white:fontsize=40:x=(w-text_w)/2:y=(h-text_h)/2-30,"
            f"drawtext=fontfile={font}:text='Sample video - replace it in Admin > Courses':fontcolor=white@0.85:fontsize=24:x=(w-text_w)/2:y=(h/2)+40")
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'lavfi', '-i', 'color=c=0x9C0D48:s=1280x720:d=8:r=24',
                    '-vf', draw, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-preset', 'veryslow', '-crf', '30', '-movflags', '+faststart', path], check=True)


def main():
    if os.path.isdir(os.path.join(OUT, 'files')):
        shutil.rmtree(os.path.join(OUT, 'files'))
    sql = []
    sql.append('-- =====================================================================')
    sql.append('--  ArtistikCity go-live starter data. GENERATED by database/golive/build_golive.py: edit that, not this file.')
    sql.append('--  Loaded by GO-LIVE-RESET (after schema.sql) into an empty database. Needs the variables')
    sql.append('--  @ga_admin_email, @ga_admin_name, @ga_admin_hash, @ga_student_email, @ga_student_hash (bcrypt).')
    sql.append('--  One super admin (id 1, every right), one student (id 1, test buyer), the reference lists and six sample courses.')
    sql.append('-- =====================================================================')
    sql.append("INSERT INTO admins (id, name, slug, email, password, admin_type, location, profile_title, designation, profile_description, created_at, updated_at) VALUES")
    sql.append("(1, @ga_admin_name, 'site-admin', @ga_admin_email, @ga_admin_hash, 'admin', 'India', 'Administrator', 'Administrator', 'Runs ArtistikCity.', NOW(), NOW());")
    sql.append("INSERT INTO users (id, name, slug, email, email_verified_at, password_hash, is_verified, notifications, language_preference, status, test_buyer, created_at, updated_at) VALUES")
    sql.append("(1, 'Test Student', 'test-student', @ga_student_email, NOW(), @ga_student_hash, 1, 1, 'English', 1, 1, NOW(), NOW());")
    sql.append('')
    sql.append('-- reference lists (same as seed.sql)')
    for t in ('mediums', 'genres', 'course_types', 'skills', 'categories', 'faqs'):
        sql.append(first_insert(t))
    # settings: the home texts, SEO defaults and the real social links; contact details are left for Admin > SEO & Layout
    sql.append("INSERT INTO settings (meta_tag, meta_key, meta_value, created_at, updated_at) VALUES")
    rows = []
    for tag, key, val in [('home', 'course_text_1', 'Live online classes'), ('home', 'course_text_2', 'Personal feedback'),
                          ('home', 'course_text_3', 'Certificate of completion'), ('home', 'workshop_text_1', 'Weekend sessions'),
                          ('home', 'workshop_text_2', 'All materials list provided'), ('home', 'workshop_text_3', 'Small batches'),
                          ('seo', 'site_title', 'ArtistikCity - Live online art classes'),
                          ('seo', 'meta_description', 'Learn to draw and paint online with live classes, personal feedback and certificates.'),
                          ('social', 'facebook', 'https://www.facebook.com/StudioVaishaliArts/'), ('social', 'instagram', 'https://www.instagram.com/artistikcity/'),
                          ('social', 'youtube', 'https://www.youtube.com/@artistikcityYT'),
                          ('shop', 'simulate_payments', 'test_buyers')]:
        rows.append(f"({q(tag)}, {q(key)}, {q(val)}, NOW(), NOW())")
    sql.append(',\n'.join(rows) + ';')
    sql.append('')

    lesson_id = 0
    module_id = 0
    for i, c in enumerate(COURSES, start=1):
        cid = i
        vid_dir = os.path.join(FILES, 'courses', str(cid), 'videos')
        # photos
        for k, src in enumerate(c['photos'], start=1):
            dst = os.path.join(FILES, 'courses', str(cid), f'photo-{k}.jpg')
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(os.path.join(SRC, 'courses', str(src), 'cover.jpg'), dst)
        video(os.path.join(vid_dir, 'trailer.mp4'), c['title'])
        video(os.path.join(vid_dir, 'lesson.mp4'), c['title'] + ': lesson')
        weeks = c['weeks']
        workshop = c['type'] == 2
        sched_lines = [f"{c['title']}", '', f"Format: {'workshop' if workshop else 'live online course'}, {c['sessions']} sessions",
                       f"Starts {c['start']} days after the site goes live (see the course page for the date).", '']
        for n, (mt, ls) in enumerate(c['modules'], start=1):
            sched_lines.append(f"Part {n}: {mt}")
            for l in ls:
                sched_lines.append(f"   - {l}")
        pdf(os.path.join(FILES, 'courses', 'schedule', str(cid), 'schedule.pdf'), c['title'] + ' - schedule', sched_lines)
        learn = c['learn']
        detail = '<p>' + c['intro'] + '</p><p>Each session is live with the instructor, followed by practice at home and feedback on what you submit in My studio.</p>'
        summary_details = '<ul>' + ''.join(f'<li>{x}</li>' for x in learn) + '</ul>'
        schedule_details = '<p>' + '<br>'.join(f"Part {n}: {mt}" for n, (mt, _) in enumerate(c['modules'], start=1)) + '</p>'
        cols = dict(admin_id=1, medium_id=c['medium'], genre_id=c['genre'], course_type_id=c['type'], skill_id=c['skill'], category_id=c['cat'],
                    title=c['title'], slug=c['slug'], sub_title=c['sub'], introduction=c['intro'], introduction_details=detail,
                    summary=f"{len(c['modules'])} parts, {c['sessions']} live sessions.", summary_details=summary_details,
                    schedule=('Two live days over a weekend.' if workshop else 'Live sessions twice a week.'), schedule_details=schedule_details,
                    schedule_pdf='schedule.pdf', age_group=AGE[c['age']], duration=weeks, sessions=c['sessions'], mini_projects=c['projects'],
                    course_modules=str(len(c['modules'])), time_required=('3' if workshop else '2'),
                    includes='Live sessions, recordings for 30 days, feedback on your work, certificate.',
                    course_includes=json.dumps({'certificate': 'yes', 'study_material_access': 'yes'}),
                    course_highlights=json.dumps(['Live classes', 'Feedback on every submission', 'Certificate on completion']),
                    course_for=json.dumps(['Beginners' if c['skill'] == 1 else 'Learners who know the basics', AGE[c['age']]]),
                    course_not_for=json.dumps(['Anyone looking for a recorded-only course']),
                    course_problems_solved=json.dumps(['Not knowing where to start', 'Practising without feedback']),
                    course_learn=json.dumps(learn), course_outcome=f"A finished piece: {c['project'][0].lower()}.",
                    course_deliverables=json.dumps(['Certificate', 'Lesson notes (PDF)', 'Session recordings']),
                    course_prerequisites=('None.' if c['skill'] == 1 else 'Basic drawing practice.'),
                    course_based='instructor_based', intro_video_url=f'/storage/uploads/courses/{cid}/videos/trailer.mp4',
                    seat_limit=c.get('seats'), is_free_course=0, status=1)
        names = ', '.join(['id'] + list(cols) + ['course_start_date', 'created_at', 'updated_at'])
        vals = ', '.join([str(cid)] + [q(v) for v in cols.values()] + [f"DATE_ADD(CURDATE(), INTERVAL {c['start']} DAY)", 'NOW()', 'NOW()'])
        sql.append(f"-- course {cid}: {c['title']}")
        sql.append(f"INSERT INTO courses ({names}) VALUES ({vals});")
        sql.append(f"INSERT INTO course_prices (course_id, price_inr, price_usd, created_at, updated_at) VALUES ({cid}, {c['inr']}, {c['usd']}, NOW(), NOW());")
        sql.append("INSERT INTO course_photos (course_id, photo_name, created_at, updated_at) VALUES " +
                   ', '.join(f"({cid}, 'photo-{k}.jpg', NOW(), NOW())" for k in range(1, len(c['photos']) + 1)) + ';')
        for n, (mt, lessons) in enumerate(c['modules'], start=1):
            module_id += 1
            sql.append(f"INSERT INTO course_modules (id, admin_id, course_id, module_title, module_duration, sort_order, status, created_at, updated_at) VALUES "
                       f"({module_id}, 1, {cid}, {q(mt)}, {q('1 day' if workshop else '1 week')}, {n}, '1', NOW(), NOW());")
            for m, lt in enumerate(lessons, start=1):
                lesson_id += 1
                pdf(os.path.join(FILES, 'courses', 'lessons', str(lesson_id), 'lesson-notes.pdf'), lt,
                    [c['title'], f"Part {n}: {mt}", '', 'In this lesson:', f'   - {lt}', '   - Practise at home and upload a photo in My studio.'])
                sql.append(f"INSERT INTO course_module_lessons (id, admin_id, course_id, module_id, lesson_title, lesson_description, lesson_pdf, video_url, sort_order, status, created_at, updated_at) VALUES "
                           f"({lesson_id}, 1, {cid}, {module_id}, {q(lt)}, {q('<p>' + lt + ': watch the lesson, read the notes and practise.</p>')}, 'lesson-notes.pdf', "
                           f"'/storage/uploads/courses/{cid}/videos/lesson.mp4', {m}, '1', NOW(), NOW());")
            sql.append(f"INSERT INTO course_module_lesson_tasks (admin_id, course_id, module_id, lesson_id, task_title, task_description, status, created_at, updated_at) VALUES "
                       f"(1, {cid}, {module_id}, {lesson_id}, {q('Practice: ' + mt)}, {q('Practise what you learned in ' + mt.lower() + ' and upload a photo of your work.')}, '1', NOW(), NOW());")
        sql.append(f"INSERT INTO course_projects (admin_id, course_id, project_title, project_description, status, created_at, updated_at) VALUES "
                   f"(1, {cid}, {q(c['project'][0])}, {q(c['project'][1])}, '1', NOW(), NOW());")
        sql.append('')
    # medium tiles on the home page
    for mid in range(1, 7):
        s = os.path.join(SRC, 'mediums', str(mid), 'medium.jpg')
        if os.path.exists(s):
            d = os.path.join(FILES, 'mediums', str(mid), 'medium.jpg')
            os.makedirs(os.path.dirname(d), exist_ok=True)
            shutil.copyfile(s, d)
    open(os.path.join(OUT, 'golive-seed.sql'), 'w', encoding='utf-8', newline='\n').write('\n'.join(sql) + '\n')
    total = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(FILES) for f in fs)
    print(f'golive-seed.sql: {len(COURSES)} courses, {module_id} modules, {lesson_id} lessons; files {total // 1024} KB')


if __name__ == '__main__':
    main()
