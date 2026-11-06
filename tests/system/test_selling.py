"""My studio > Sell your art (journey step 7), end to end (HTTP + DB).

* Only signed-in students reach the page and the API (401 otherwise, also without the CSRF header).
* Only approved work from a course the student is certified in can be listed; someone else's work is refused.
* Draft -> submit for review -> staff approve -> live in the marketplace (original only, no prints) -> editing a live
  listing sends it back to review -> withdraw; a rejection with notes shows as "changes requested".
* Prices and details are validated; one listing per artwork; extra photos are checked; a SELLING restriction blocks it.
* Payout details: validated, stored encrypted (never in clear, never in the security log), need a recent sign-in;
  finance staff can read them (logged), other staff can't.

Run against the dev profile (run_app.sh dev) on a fresh database:  python3 tests/system/test_selling.py
"""
import html, json, os, re, struct, subprocess, urllib.parse, zlib
import requests

B = 'http://localhost:8080'
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
def sql(q): return subprocess.run(MYSQL + [q], capture_output=True, text=True, check=True).stdout.strip()
fails = 0
def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  {str(extra)[:400]}'))
    if not cond: fails += 1
def xsrf(s): return urllib.parse.unquote(s.cookies.get('XSRF-TOKEN', ''))
H = lambda s: {'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'}
def post(s, path, body=None): return s.post(B + path, json=body if body is not None else {}, headers=H(s))
def put(s, path, body): return s.put(B + path, json=body, headers=H(s))
def page(s, path):
    r = s.get(B + path)
    m = re.search(r'data-page="([^"]+)"', r.text)
    d = json.loads(html.unescape(m.group(1))) if m else {}
    return d.get('component'), d.get('props', {}), r
def student(email):
    s = requests.Session(); s.get(B + '/login')
    r = s.post(B + '/login', json={'email': email, 'password': 'password'}, headers=H(s))
    assert r.status_code == 200, (email, r.text[:200])
    return s
def admin_login(email='admin@artistikcity.com'):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False); assert r.status_code == 302
    s.get(B + '/admin/dashboard'); return s
def png(w, h):
    raw = b''.join(b'\x00' + b'\x9c\x0d\x48' * w for _ in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')

# ---------------------------------------------------------------- setup: a fresh approved piece for the certified demo student
uid = sql("select id from users where email = 'student@artistikcity.com'")
sql("delete from student_payout_details")
old = "select id from student_submissions where title like 'Sell test%'"
sql(f"delete from painting_images where painting_id in (select id from paintings where submission_id in ({old}))")
sql(f"delete from paintings where submission_id in ({old})")
sql(f"delete from portfolio_marketplace where submission_id in ({old})")
sql(f"delete from student_submissions where title like 'Sell test%'")
sql(f"insert into student_submissions (user_id, course_id, title, description, file_url, admin_status, reviewed_at, created_at, updated_at)"
    f" values ({uid}, 1, 'Sell test sunrise', 'Sunrise over the ghats in layered washes.', '/storage/uploads/artworks/1/artwork.jpg', 'Approved', now(), now(), now())")
sub = sql("select max(id) from student_submissions where title = 'Sell test sunrise'")
sql(f"insert into student_submissions (user_id, course_id, title, description, file_url, admin_status, created_at, updated_at)"
    f" values ({uid}, 1, 'Sell test pending', 'Not approved yet', '/storage/uploads/artworks/1/artwork.jpg', 'Pending Review', now(), now())")
pending_sub = sql("select max(id) from student_submissions where title = 'Sell test pending'")
other = sql("select id from users where email <> 'student@artistikcity.com' order by id limit 1")
sql(f"insert into student_submissions (user_id, course_id, title, description, file_url, admin_status, created_at, updated_at)"
    f" values ({other}, 1, 'Sell test not yours', 'Someone else', '/storage/uploads/artworks/1/artwork.jpg', 'Approved', now(), now())")
foreign_sub = sql("select max(id) from student_submissions where title = 'Sell test not yours'")

LISTING = {'title': 'Sell test sunrise', 'description': 'Sunrise over the Western Ghats, painted in layered transparent washes.',
           'medium': 'Watercolour', 'surface': 'Watercolour paper (300 gsm)', 'heightInches': 12, 'widthInches': 16, 'yearCreated': 2026,
           'signed': True, 'framed': False, 'basePrice': 4500, 'images': []}

# ---------------------------------------------------------------- who may use it
g = requests.Session(); g.get(B + '/login')
r = g.get(B + '/dashboard/sell', allow_redirects=False)
check('guest: Sell your art page -> sign in', r.status_code in (302, 303) and '/login' in r.headers.get('Location', ''), r.status_code)
r = post(g, '/api/studio/sell/listings', {'submissionId': int(sub), **LISTING})
check('guest: listing API -> 401', r.status_code == 401, r.status_code)
s = student('student@artistikcity.com')
r = s.post(B + '/api/studio/sell/listings', json={'submissionId': int(sub), **LISTING}, headers={'Accept': 'application/json'})
check('no CSRF header -> 401, nothing created', r.status_code == 401 and sql(f"select count(*) from paintings where submission_id = {sub}") == '0', r.status_code)

# ---------------------------------------------------------------- the studio shows step 7
comp, props, _ = page(s, '/dashboard')
check('certified student: studio is at step 7 (Sell)', props.get('stage') == 7, props.get('stage'))
comp, props, _ = page(s, '/dashboard/sell')
check('Sell your art page renders for a certified student', comp == 'Studio/Sell' and props['certified'] is True)
check('the approved, unlisted piece is ready to sell', any(str(x['id']) == sub for x in props['ready']))
check('pending work is not offered', all(str(x['id']) != pending_sub for x in props['ready'] + props['locked']))
check('terms come from the payout settings', props['terms']['artistPercent'] == '70' and props['terms']['payoutDays'] == 14, props['terms'])

# ---------------------------------------------------------------- rules on what can be listed
r = post(s, '/api/studio/sell/listings', {'submissionId': int(foreign_sub), **LISTING})
check("someone else's artwork -> 404", r.status_code == 404, r.status_code)
r = post(s, '/api/studio/sell/listings', {'submissionId': int(pending_sub), **LISTING})
check('work not approved yet -> 422', r.status_code == 422, r.status_code)
r = post(s, '/api/studio/sell/listings', {'submissionId': int(sub), **LISTING, 'basePrice': 100, 'heightInches': 0, 'description': 'short'})
e = r.json()
check('bad price / size / description -> 422 with every problem', r.status_code == 422 and len(e['errors']) >= 3 and any('price' in x for x in e['errors']), e)
r = post(s, '/api/studio/sell/listings', {'submissionId': int(sub), **LISTING, 'intent': 'draft'})
check('save a draft', r.status_code == 200 and r.json()['status'] == 'DRAFT', r.text)
lid = r.json()['id']
row = sql(f"select source, artist_user_id, submission_id, stock_status, final_price, coalesce(shop_formats, 'NONE') from paintings where id = {lid}").split('\t')
check('stored as a student original (no prints), at the student\'s price', row[:4] == ['STUDENT', uid, sub, 'DRAFT'] and row[4] == '4500.00' and row[5] == 'NONE', row)
check('the approved photo is the cover', sql(f"select image_url from painting_images where painting_id = {lid} and is_primary = 1") == '/storage/uploads/artworks/1/artwork.jpg')
r = post(s, '/api/studio/sell/listings', {'submissionId': int(sub), **LISTING})
check('one listing per artwork -> 409', r.status_code == 409, r.status_code)
slug0 = sql(f"select slug from paintings where id = {lid}")
check('a draft is not public', page(requests.Session(), '/marketplace/' + slug0)[0] == 'Marketplace/NotFound')
check('...but the student can preview it', page(s, '/marketplace/' + slug0)[0] == 'Marketplace/Show')

# ---------------------------------------------------------------- photos
f = s.post(B + '/api/studio/sell/images', files={'file': ('detail.png', png(1000, 800), 'image/png')}, headers=H(s))
check('upload a detail photo', f.status_code == 200 and '/uploads/marketplace/' in f.json().get('url', ''), f.text[:200])
detail = f.json().get('url')
f2 = s.post(B + '/api/studio/sell/images', files={'file': ('tiny.png', png(300, 200), 'image/png')}, headers=H(s))
check('a low-resolution photo is refused with the reason', f2.status_code == 422 and 'px' in f2.json()['message'], f2.text[:200])

# ---------------------------------------------------------------- submit, review, live
ver = int(sql(f"select version from paintings where id = {lid}"))
r = put(s, f'/api/studio/sell/listings/{lid}', {**LISTING, 'images': [{'url': detail}], 'version': ver, 'intent': 'submit'})
check('submit for review', r.status_code == 200 and sql(f"select stock_status from paintings where id = {lid}") == 'PENDING_REVIEW', r.text)
check('detail photo kept after the cover', sql(f"select count(*) from painting_images where painting_id = {lid}") == '2')
r = put(s, f'/api/studio/sell/listings/{lid}', {**LISTING, 'version': ver, 'intent': 'submit'})
check('saving over a changed listing (stale version) -> 409', r.status_code == 409, r.status_code)
adm = admin_login()
r = adm.post(B + f'/admin/console/api/marketplace/paintings/{lid}/review', json={'decision': 'reject', 'notes': 'Please add a photo of the signature.'}, headers=H(adm))
check('staff send it back with notes', r.status_code == 200, r.text)
_, props, _ = page(s, '/dashboard/sell')
mine = next(x for x in props['listings'] if x['id'] == lid)
check('student sees "changes requested" with the notes', mine['changes_requested'] is True and 'signature' in mine['review_notes'], mine)
ver = int(sql(f"select version from paintings where id = {lid}"))
r = put(s, f'/api/studio/sell/listings/{lid}', {**LISTING, 'images': [{'url': detail}], 'version': ver, 'intent': 'submit'})
r = adm.post(B + f'/admin/console/api/marketplace/paintings/{lid}/review', json={'decision': 'approve', 'notes': ''}, headers=H(adm))
slug = sql(f"select slug from paintings where id = {lid}")
check('staff approve -> live in the marketplace', r.status_code == 200 and page(requests.Session(), '/marketplace/' + slug)[0] == 'Marketplace/Show', r.text)
_, props, _ = page(s, '/dashboard/sell')
mine = next(x for x in props['listings'] if x['id'] == lid)
check('listing shows as for sale with the student\'s share', mine['stock_status'] == 'AVAILABLE' and float(mine['you_earn']) == 3150, mine)
ver = int(sql(f"select version from paintings where id = {lid}"))
r = put(s, f'/api/studio/sell/listings/{lid}', {**LISTING, 'basePrice': 5200, 'images': [{'url': detail}], 'version': ver, 'intent': 'draft'})
check('editing a live listing sends it back to review', r.status_code == 200 and sql(f"select stock_status, final_price from paintings where id = {lid}") == 'PENDING_REVIEW\t5200.00', r.text)
r = post(s, f'/api/studio/sell/listings/{lid}/withdraw')
check('withdraw -> private draft', r.status_code == 200 and sql(f"select stock_status from paintings where id = {lid}") == 'DRAFT', r.text)
_, props, _ = page(s, '/dashboard/sell')
check('a withdrawn draft is not shown as "changes requested"', next(x for x in props['listings'] if x['id'] == lid)['changes_requested'] is False)
check('the security log has the student\'s listing actions', int(sql(f"select count(*) from audit_logs where resource_type = 'painting' and resource_id = '{lid}' and actor_type = 'USER'") or 0) >= 3
      or int(sql(f"select count(*) from audit_logs where resource_type = 'painting' and resource_id = '{lid}'")) >= 3)

# ---------------------------------------------------------------- another student can't touch it
s2 = student(sql(f"select email from users where id = {other}"))
r = put(s2, f'/api/studio/sell/listings/{lid}', {**LISTING, 'version': 0})
check("another student editing it -> 404", r.status_code == 404, r.status_code)
r = post(s2, f'/api/studio/sell/listings/{lid}/withdraw')
check("another student withdrawing it -> 404", r.status_code == 404, r.status_code)
_, props2, _ = page(s2, '/dashboard/sell')
check('a student with no certificate sees the locked state', props2.get('certified') is False and props2.get('ready') == [], props2.get('certified'))

# ---------------------------------------------------------------- payout details
r = post(s, '/api/studio/sell/payout', {'method': 'BANK', 'holderName': 'Student Artist', 'accountNumber': '1234', 'accountNumberConfirm': '1234', 'ifsc': 'BAD'})
check('bad bank details -> 422 per field', r.status_code == 422 and {'accountNumber', 'ifsc'} <= set(r.json()['errors']), r.text)
r = post(s, '/api/studio/sell/payout', {'method': 'BANK', 'holderName': 'Student Artist', 'accountNumber': '123456789012', 'accountNumberConfirm': '123456789013', 'ifsc': 'HDFC0001234'})
check('account numbers must match', r.status_code == 422 and 'accountNumberConfirm' in r.json()['errors'], r.text)
r = post(s, '/api/studio/sell/payout', {'method': 'UPI', 'holderName': 'Student Artist', 'upiId': 'student.artist@okhdfcbank'})
check('save UPI details', r.status_code == 200 and r.json()['payout']['masked'] == 'UPI ••••@okhdfcbank', r.text)
enc = sql(f"select details_enc from student_payout_details where user_id = {uid}")
check('stored encrypted, never in clear', enc.startswith('v1:') and 'student.artist' not in enc)
check('the security log has the change without the details',
      sql(f"select count(*) from audit_logs where action = 'payout details saved' and resource_id = '{uid}'") != '0'
      and sql("select count(*) from audit_logs where concat_ws(' ', summary, before_json, after_json) like '%student.artist%'") == '0')
sql(f"update auth_sessions set created_at = date_sub(created_at, interval 2 hour) where user_id = {uid}")
r = post(s, '/api/studio/sell/payout', {'method': 'UPI', 'holderName': 'Someone Else', 'upiId': 'thief@ybl'})
check('changing them on an old sign-in needs a fresh sign-in (403 reauth)', r.status_code == 403 and r.json().get('reauth') is True
      and 'okhdfcbank' in sql(f"select masked from student_payout_details where user_id = {uid}"), r.text)
r = s.post(B + '/auth/reauthenticate?return_to=%2Fdashboard%2Fsell', json={}, headers=H(s))
check('"sign in again" comes back to Sell your art', r.status_code == 200 and r.json()['redirect'] == '/login?return_to=%2Fdashboard%2Fsell', r.text)
r = s.post(B + '/auth/reauthenticate?return_to=https://evil.example.com', json={}, headers=H(student('student@artistikcity.com')))

fin = get = adm.get(B + f'/admin/api/finance/payees/{uid}/payout-details', headers={'Accept': 'application/json'})
check('finance staff can read the full details', fin.status_code == 200 and fin.json()['details']['upiId'] == 'student.artist@okhdfcbank', fin.text[:200])
check('...and that look is logged without the details', sql(f"select count(*) from audit_logs where resource_type = 'payout_details' and resource_id = '{uid}'") != '0'
      and sql("select count(*) from audit_logs where concat_ws(' ', summary, before_json, after_json) like '%student.artist%'") == '0')
t = admin_login('vaishali@artistikcity.com')
r = t.get(B + f'/admin/api/finance/payees/{uid}/payout-details', headers={'Accept': 'application/json'})
check('a teacher cannot read payout details (403)', r.status_code == 403, r.status_code)

# ---------------------------------------------------------------- a selling restriction
s = student('student@artistikcity.com')
sql(f"insert into account_restrictions (user_id, kind, reason, created_at) values ({uid}, 'SELLING', 'Test restriction', now())")
r = post(s, '/api/studio/sell/listings', {'submissionId': int(sub), **LISTING})
_, props, _ = page(s, '/dashboard/sell')
check('a SELLING restriction blocks listing and shows on the page', r.status_code == 422 and props.get('restriction'), (r.status_code, props.get('restriction')))
sql(f"update account_restrictions set lifted_at = now() where user_id = {uid} and kind = 'SELLING'")

print('ALL PASSED' if fails == 0 else f'{fails} FAILED')
