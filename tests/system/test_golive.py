"""Go-live acceptance: the site right after GO-LIVE-RESET (database\\golive). Production profile.

Checks the clean state (one super admin with every right, one student who is a test buyer, six live courses with every
field filled and every file reachable, nothing made up: no orders, reviews, testimonials, paintings or stories), that
configuration survived, that testing switches are gone, that the passwords typed at the reset work and the demo
password does not, and that a student can browse, buy a course with a simulated payment (test buyer), learn (lesson
video + notes) and see the order. Then every admin page the super admin can open answers, and every public page too.

Run after:  pwsh GO-LIVE-RESET.ps1 -Local ...  and a restart of the app (production profile):
    GA_ADMIN_PW=... GA_STUDENT_PW=... python3 tests/system/test_golive.py
"""
import html, json, os, re, subprocess, urllib.parse
import requests

B = 'http://localhost:8080'
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
def sql(q): return subprocess.run(MYSQL + [q], capture_output=True, text=True, check=True).stdout.strip()
ADMIN, STUDENT = os.environ.get('GA_ADMIN_EMAIL', 'admin@artistikcity.com'), os.environ.get('GA_STUDENT_EMAIL', 'student@artistikcity.com')
APW, SPW = os.environ['GA_ADMIN_PW'], os.environ['GA_STUDENT_PW']
fails = 0
def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  {str(extra)[:400]}'))
    if not cond: fails += 1
def xsrf(s): return urllib.parse.unquote(s.cookies.get('XSRF-TOKEN', ''))
H = lambda s: {'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'}
def post(s, path, body=None): return s.post(B + path, json=body if body is not None else {}, headers=H(s))
def page(s, path):
    r = s.get(B + path)
    m = re.search(r'data-page="([^"]+)"', r.text)
    d = json.loads(html.unescape(m.group(1))) if m else {}
    return d.get('component'), d.get('props', {}), r
def admin_login(email, pw):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': pw}, allow_redirects=False)
    return s, r
def student_login(email, pw):
    s = requests.Session(); s.get(B + '/login')
    return s, s.post(B + '/login', json={'email': email, 'password': pw}, headers=H(s))

# ============================================================================== the clean state
check('one staff account, a super admin', sql("select concat(count(*), ':', min(admin_type), ':', min(email)) from admins") == f'1:admin:{ADMIN}')
check('one student, a test buyer with a verified e-mail', sql("select concat(count(*), ':', min(email), ':', min(test_buyer), ':', min(email_verified_at is not null)) from users") == f'1:{STUDENT}:1:1')
for t in ('orders', 'enrollments', 'shop_orders', 'marketplace_orders', 'commission_orders', 'paintings', 'posts', 'course_reviews',
          'testimonials', 'contacts', 'newsletters', 'student_submissions', 'certificates', 'landing_events', 'audit_logs', 'admin_audit', 'payment_logs'):
    check(f'empty: {t}', sql(f"select count(*) from {t}") == '0', sql(f"select count(*) from {t}"))
check('six live courses (four courses, two workshops)', sql("select concat(count(*), ':', sum(course_type_id = 1), ':', sum(course_type_id = 2)) from courses where status = 1") == '6:4:2')
cols = [c for c in sql("select column_name from information_schema.columns where table_schema = database() and table_name = 'courses'").split('\n')
        if c not in ('main_course_id', 'cancelled_at', 'cancel_reason')]
for c in cols:
    n = sql(f"select count(*) from courses where coalesce(cast(`{c}` as char), '') = ''") if c != 'seat_limit' else sql("select count(*) from courses where course_type_id = 2 and seat_limit is null")
    check(f'every course has {c}', n == '0', n)
check('3 modules x 2 lessons per course, every lesson with notes and a video', sql("select concat(count(distinct m.id), ':', count(l.id), ':', sum(l.lesson_pdf is not null and l.video_url is not null)) from course_modules m join course_module_lessons l on l.module_id = m.id") == '18:36:36')
check('a task per module and a project per course', sql("select count(*) from course_module_lesson_tasks") == '18' and sql("select count(*) from course_projects") == '6')
check('prices and two photos per course', sql("select count(*) from course_prices where price_inr > 0 and price_usd > 0") == '6' and sql("select count(*) from course_photos") == '12')
check('course dates are in the future', sql("select count(*) from courses where course_start_date > curdate()") == '6')
check('testing switches gone', sql("select count(*) from settings where (meta_tag = 'landing_pages' and meta_key like 'simulate%') or meta_key like 'site_lock%'") == '0')
login = sql("select meta_value from settings where meta_tag = 'landing_pages' and meta_key = 'login'")
check('simulated sign-in codes off (the rest of Login & verification kept)', not login or (json.loads(login).get('simulatePhone') is False and json.loads(login).get('simulateEmail') is False), login)
check('every course has plain numbers for duration and hours (the page adds the units)', sql("select count(*) from courses where duration regexp '^[0-9]+$' and time_required regexp '^[0-9]+$'") == '6')
check('shop simulation: test buyers only', sql("select meta_value from settings where meta_tag = 'shop' and meta_key = 'simulate_payments'") == 'test_buyers')
check('no placeholder contact phone or misspelt e-mail', sql("select count(*) from settings where meta_value in ('9876 543 201', 'info@artistickcity.com')") == '0')
check('every migration recorded', int(sql("select count(*) from schema_migrations")) == len([f for f in os.listdir('database/migrations') if f.endswith('.sql')]))

# ============================================================================== sign-in
s, r = admin_login(ADMIN, 'password')
check('demo password "password" does not open the admin (back to the sign-in form)', r.headers.get('Location') == '/admin/login'
      and s.get(B + '/admin/dashboard', allow_redirects=False).status_code == 302, r.headers.get('Location'))
adm, r = admin_login(ADMIN, APW)
check('super admin signs in with the password typed at the reset', r.status_code == 302 and '/admin/login' not in r.headers.get('Location', ''), r.headers.get('Location'))
_, r = student_login(STUDENT, 'password')
check('demo password does not open the student account', r.status_code != 200, r.status_code)
stu, r = student_login(STUDENT, SPW)
check('student signs in with the password typed at the reset', r.status_code == 200, r.text[:200])

# ============================================================================== public pages
courses = sql("select id, slug from courses order by id").split('\n')
c, p, r = page(requests.Session(), '/courses?type=all')
check('/courses lists the six', r.status_code == 200 and len((p.get('courses') or {}).get('data', [])) == 6, len((p.get('courses') or {}).get('data', [])))
files = set()
for row in courses:
    cid, slug = row.split('\t')
    c, p, r = page(requests.Session(), f'/course/{slug}')
    check(f'course page {slug}', r.status_code == 200 and c == 'CourseDetails', (r.status_code, c))
    for f in sql(f"select concat('/storage/uploads/courses/', course_id, '/', photo_name) from course_photos where course_id = {cid}").split('\n'): files.add(f)
    files.add(sql(f"select intro_video_url from courses where id = {cid}"))
    files.add(sql(f"select concat('/storage/uploads/courses/schedule/', id, '/', schedule_pdf) from courses where id = {cid}"))
for f in sql("select concat('/storage/uploads/courses/lessons/', id, '/', lesson_pdf) from course_module_lessons").split('\n'): files.add(f)
for f in sql("select distinct video_url from course_module_lessons").split('\n'): files.add(f)
bad = [f for f in sorted(files) if requests.get(B + f, stream=True).status_code != 200]
check(f'every course file answers ({len(files)} photos, videos and PDFs)', not bad, bad[:5])
for path in ('/', '/courses?type=all', '/marketplace', '/studio-stories', '/how-it-works', '/artistikcity-vision', '/instructor', '/customer-support',
             '/faq', '/contact', '/privacy-policy', '/terms-conditions', '/commission/step-1', '/login', '/join', '/sitemap.xml', '/robots.txt'):
    r = requests.get(B + path)
    check(f'public {path} answers 200', r.status_code == 200, r.status_code)
check('robots.txt allows crawling (no site lock)', 'Disallow: /\n' not in requests.get(B + '/robots.txt').text.replace('\r', ''))
check('home page has no placeholder text', not re.search(r'lorem ipsum|Title Here', requests.get(B + '/').text, re.I))

# ============================================================================== a student buys, learns, sees the order
slug = sql("select slug from courses where course_type_id = 1 order by id limit 1")
c, p, r = page(stu, '/checkout?course=' + slug)
check('checkout opens for the student', r.status_code == 200 and c, (r.status_code, c))
check('test buyer: "Simulate payment" offered', (p.get('methods') or {}).get('simulate') is True, p.get('methods'))
r = post(stu, '/checkout/billing', {'billing_name': 'Test Student', 'billing_address': '1 Road', 'billing_city': 'Pune', 'billing_zipcode': '411001', 'billing_country': 'India'})
oid = (r.json() or {}).get('orderId') if r.status_code == 200 else None
check('order created', bool(oid), r.text[:200])
r = post(stu, '/checkout/pay', {'orderId': oid, 'method': 'test'})
check('simulated payment starts', r.status_code == 200 and r.json().get('simulated') is True, r.text[:200])
r = post(stu, '/checkout/test-pay', {'orderId': oid})
check('simulated payment completes', r.status_code == 200, r.text[:200])
check('paid, and kept out of revenue as a test order', sql(f"select concat(payment_status, ':', is_test) from orders where id = {oid}") == '1:1')
c, p, r = page(stu, f'/user/course/{oid}')
check('the course opens for the student', r.status_code == 200 and c == 'Course/Overview', (r.status_code, c))
mod = sql(f"select min(id) from course_modules where course_id = (select course_id from orders where id = {oid})")
c, p, r = page(stu, f'/user/course/modules/{mod}')
check('the learning view opens with the lesson video', r.status_code == 200 and c == 'Course/Learn' and '/videos/lesson.mp4' in json.dumps(p), (r.status_code, c))
c, p, r = page(stu, '/user/orders')
check('the order is under Your orders', r.status_code == 200 and str(oid) in json.dumps(p) or slug in json.dumps(p), c)
for path in ('/dashboard', '/dashboard/certificates', '/dashboard/sell', '/user/my-account', '/user/wishlist', '/user/offers'):
    r = stu.get(B + path)
    check(f'student {path} answers', r.status_code == 200, r.status_code)

# ============================================================================== every admin page
c, p, r = page(adm, '/admin/dashboard')
check('admin overview opens', r.status_code == 200 and c, (r.status_code, c))
nav_src = open('frontend/resources/js/Components/Admin/AdminLayout.js', encoding='utf-8').read()
hrefs = sorted(set(h for h in re.findall(r"href: '(/admin/[a-z0-9/\-]+)'", nav_src)))
check('found the admin menu', len(hrefs) >= 20, len(hrefs))
bad = []
for h in hrefs:
    r = adm.get(B + h)
    if r.status_code != 200: bad.append((h, r.status_code))
check(f'every admin menu page answers ({len(hrefs)})', not bad, bad)
for path in ('/admin/courses/edit/1', '/admin/landing', '/admin/setup/site-settings#faq', '/admin/account/security'):
    r = adm.get(B + path.split('#')[0])
    check(f'admin {path} answers', r.status_code == 200, r.status_code)

print('\n' + ('ALL PASS' if fails == 0 else f'{fails} FAILED'))
raise SystemExit(1 if fails else 0)
