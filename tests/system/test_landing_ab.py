"""Landing page A/B tests (LandingAbTests, editor › Preview & publish › A/B test).

Start: only on a published page, with a saved draft that differs from the live page, a name and a 10-90 % split; the
draft becomes a version row "A/B variant B". While it runs: visitors are split by their visitor cookie (sticky; header
X-AB-Variant), B shows the draft's content, staff can force ?ab=A|B, every event records ab_test_id / ab_variant, a
purchase is credited to the variant the buyer saw, publish / rollback answer 409. End: B = published as the new live
version for everyone (old one kept in the history), A = live page stays, none = just stops; unpublishing stops a test.
Permissions: guests 401, roles without landing.publish 403. Dev profile, fresh DB (schema + seed).
"""
import html, json, os, re, subprocess, urllib.parse
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
def put(s, path, body=None): return s.put(B + path, json=body if body is not None else {}, headers=H(s))
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'})
def staff(email='admin@artistikcity.com'):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False); assert r.status_code == 302, email
    s.get(B + '/admin/dashboard'); return s
def student(email):
    s = requests.Session(); s.get(B + '/login')
    r = s.post(B + '/login', json={'email': email, 'password': 'password'}, headers=H(s)); assert r.status_code == 200, r.text[:200]
    return s

P = int(sql("select id from landing_pages where slug = 'watercolour-landscapes-for-beginners'"))
URL = '/landing/watercolour-landscapes-for-beginners'
sql("delete from landing_ab_tests"); sql(f"delete from landing_events where landing_page_id = {P}")
adm = staff()
ed = lambda: get(adm, f'/admin/api/landing/pages/{P}').json()

# make the draft equal to the live page, so the test starts from a known state
live_cfg = json.loads(sql(f"select v.config from landing_pages p join landing_page_versions v on v.id = p.published_version_id where p.id = {P}"))
d = ed()
r = put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': live_cfg, 'savedAt': d['page']['draft_saved_at'], 'force': True})
check('draft reset to the live config', r.status_code == 200, r.text[:200])
d = ed()
check('editor data has the A/B block', d['ab']['ready'] is True and d['ab']['running'] is None, d.get('ab'))

# ============================================================================== permissions and validation
check('guest: start -> 401', requests.post(B + f'/admin/api/landing/pages/{P}/ab/start', json={}).status_code in (401, 419))
mod = staff('pooja@artistikcity.com')
r = post(mod, f'/admin/api/landing/pages/{P}/ab/start', {'name': 'x', 'split': 50})
check('moderator (no landing.publish): start -> 403', r.status_code == 403, r.status_code)
r = post(adm, f'/admin/api/landing/pages/{P}/ab/start', {'name': 'Same page', 'split': 50})
check('draft equal to live page -> 422 explained', r.status_code == 422 and 'same as the live page' in r.json()['errors'].get('ab', ''), r.text[:300])

cfg = ed()['config']
hero = next(s for s in cfg['sections'] if s.get('type') == 'hero')
OLD = hero['content'].get('heading', '')
NEW = 'Paint glowing landscapes in four weekends'
hero['content']['heading'] = NEW
r = put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': cfg, 'savedAt': ed()['page']['draft_saved_at']})
check('changed draft saved (B)', r.status_code == 200 and r.json()['hasUnpublishedChanges'], r.text[:200])
check('no name -> 422', post(adm, f'/admin/api/landing/pages/{P}/ab/start', {'name': '', 'split': 50}).status_code == 422)
r = post(adm, f'/admin/api/landing/pages/{P}/ab/start', {'name': 'New hero', 'split': 95})
check('split 95 -> 422', r.status_code == 422 and 'split' in r.json()['errors'])
check('split 5 -> 422', post(adm, f'/admin/api/landing/pages/{P}/ab/start', {'name': 'New hero', 'split': 5}).status_code == 422)

# ============================================================================== start
r = post(adm, f'/admin/api/landing/pages/{P}/ab/start', {'name': 'New hero', 'split': 50})
check('start -> 200', r.status_code == 200 and r.json()['data']['ab']['running']['name'] == 'New hero', r.text[:300])
t = r.json()['data']['ab']['running']
check('variant stored as a version "A/B variant B"', sql(f"select note from landing_page_versions where id = {t['variant_version_id']}") == 'A/B variant B: New hero')
check('control = the live version', str(t['control_version_id']) == sql(f"select published_version_id from landing_pages where id = {P}"))
check('second start while running -> 422', post(adm, f'/admin/api/landing/pages/{P}/ab/start', {'name': 'Again', 'split': 50}).status_code == 422)
check('publish while running -> 409', post(adm, f'/admin/api/landing/pages/{P}/publish', {'note': 'x'}).status_code == 409)
check('schedule while running -> 409', post(adm, f'/admin/api/landing/pages/{P}/schedule', {'publishAt': '2030-01-01T10:00'}).status_code == 409)
v1 = sql(f"select min(id) from landing_page_versions where landing_page_id = {P}")
check('rollback while running -> 409', post(adm, f'/admin/api/landing/pages/{P}/versions/{v1}/restore').status_code == 409)

# ============================================================================== visitors
seen = {'A': 0, 'B': 0}; content_ok = True; sticky = True
for i in range(60):
    s = requests.Session()
    r = s.get(B + URL)
    v = r.headers.get('X-AB-Variant')
    if v not in ('A', 'B'): content_ok = False; continue
    seen[v] += 1
    has_new = NEW in r.text
    if (v == 'B') != has_new: content_ok = False
    if s.get(B + URL).headers.get('X-AB-Variant') != v: sticky = False
check('every visitor gets A or B with the matching content', content_ok, seen)
check('a visitor keeps the same variant', sticky)
check('the split is roughly 50/50', 15 <= seen['B'] <= 45, seen)
views = dict(x.split(':') for x in sql(f"select concat(ab_variant, ':', count(distinct visitor)) from landing_events where ab_test_id = {t['id']} and event = 'VIEW' group by ab_variant").split('\n') if x)
check('views recorded per variant (one per visitor)', int(views.get('A', 0)) == seen['A'] and int(views.get('B', 0)) == seen['B'], (views, seen))
g = requests.Session(); first = g.get(B + URL).headers.get('X-AB-Variant')
check('a visitor cannot pick a variant with ?ab=', g.get(B + URL + '?ab=' + ('A' if first == 'B' else 'B')).headers.get('X-AB-Variant') == first)
check('staff can look at B with ?ab=B', NEW in adm.get(B + URL + '?ab=B').text and NEW not in adm.get(B + URL + '?ab=A').text)

# a purchase is credited to the variant the buyer saw
diya = student('diya@example.com')
dv = diya.get(B + URL).headers.get('X-AB-Variant')
r = post(diya, f'/landing/checkout/{P}/order', {'name': 'Diya Patel', 'phone': '+91 98765 43210', 'terms': True})
check('order created', r.status_code == 200, r.text[:200])
oid = r.json().get('orderId') or r.json().get('order', {}).get('id')
r = post(diya, f'/landing/checkout/{P}/pay', {'orderId': oid, 'method': 'upi'})
r = post(diya, '/checkout/test-pay', {'orderId': oid})
check('paid', sql(f"select payment_status from orders where id = {oid}") == '1', r.text[:200])
check('purchase credited to the buyer\'s variant', sql(f"select ab_variant from landing_events where event = 'PURCHASE' and order_id = {oid}") == dv, dv)
st = ed()['ab']['running']['stats']
is_test = sql(f"select coalesce(is_test, 0) from orders where id = {oid}") == '1'
check('editor figures: the purchase on that variant; revenue counts real (non-test) orders only, like Analytics',
      st['variants'][dv]['purchases'] == 1 and (st['variants'][dv]['revenue'] == 0 if is_test else st['variants'][dv]['revenue'] > 0), (is_test, st))
check('editor figures: visitors per variant', st['variants']['A']['visitors'] >= seen['A'] and st['variants']['B']['visitors'] >= seen['B'], st)
check('confidence and "enough" are reported', 'confidence' in st and st['enough'] in (True, False))

# ============================================================================== end: promote B
check('end with a bad winner -> 422', post(adm, f'/admin/api/landing/pages/{P}/ab/end', {'winner': 'C'}).status_code == 422)
before_versions = int(sql(f"select count(*) from landing_page_versions where landing_page_id = {P}"))
r = post(adm, f'/admin/api/landing/pages/{P}/ab/end', {'winner': 'B'})
check('promote B -> 200', r.status_code == 200 and r.json()['data']['ab']['running'] is None, r.text[:300])
check('B published as a new version', int(sql(f"select count(*) from landing_page_versions where landing_page_id = {P}")) == before_versions + 1
      and sql(f"select v.note from landing_pages p join landing_page_versions v on v.id = p.published_version_id where p.id = {P}") == 'A/B winner B: New hero')
ok = True
for i in range(10):
    r = requests.get(B + URL)
    if NEW not in r.text or r.headers.get('X-AB-Variant'): ok = False
check('everyone sees B now, no split', ok)
check('test ended with winner B, kept in history', sql(f"select concat(status, ':', winner) from landing_ab_tests where id = {t['id']}") == 'ENDED:B'
      and len(ed()['ab']['history']) == 1)
check('old live version still in the history (rollback possible)', sql(f"select count(*) from landing_page_versions where id = {t['control_version_id']}") == '1')
check('audit trail', int(sql(f"select count(*) from admin_audit where details like 'Started A/B test%'")) >= 1 and int(sql(f"select count(*) from admin_audit where details like 'Ended A/B test%winner B'")) >= 1)

# ============================================================================== keep A, stop, unpublish
cfg = ed()['config']; next(s for s in cfg['sections'] if s.get('type') == 'hero')['content']['heading'] = 'A third headline'
put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': cfg, 'savedAt': ed()['page']['draft_saved_at']})
r = post(adm, f'/admin/api/landing/pages/{P}/ab/start', {'name': 'Third', 'split': 20})
check('second test starts', r.status_code == 200, r.text[:200])
r = post(adm, f'/admin/api/landing/pages/{P}/ab/end', {'winner': 'A'})
check('keep A: live page unchanged, draft kept', r.status_code == 200 and NEW in requests.get(B + URL).text and ed()['config']['sections'][0] is not None
      and 'A third headline' in json.dumps(ed()['config']))
check('winner A recorded', sql("select winner from landing_ab_tests where name = 'Third'") == 'A')
post(adm, f'/admin/api/landing/pages/{P}/ab/start', {'name': 'Fourth', 'split': 30})
r = post(adm, f'/admin/api/landing/pages/{P}/unpublish')
check('unpublishing stops the test (no winner)', r.status_code == 200 and sql("select concat(status, ':', coalesce(winner, '-')) from landing_ab_tests where name = 'Fourth'") == 'ENDED:-')
# put the page back as it was for the other suites
d = ed()
put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': live_cfg, 'savedAt': d['page']['draft_saved_at'], 'force': True})
r = post(adm, f'/admin/api/landing/pages/{P}/publish', {'note': 'Back to the original'})
check('page published again with the original content', r.status_code == 200 and requests.get(B + URL).status_code == 200 and (not OLD or OLD in requests.get(B + URL).text), r.text[:200])
check('stop without a running test -> 409', post(adm, f'/admin/api/landing/pages/{P}/ab/end', {'winner': 'none'}).status_code == 409)

print('\n' + ('ALL PASS' if fails == 0 else f'{fails} FAILED'))
raise SystemExit(1 if fails else 0)
