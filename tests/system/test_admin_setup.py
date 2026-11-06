"""Admin > System parameters and Studio Stories, end to end (HTTP + DB).

* Who may call the new admin APIs: guests and students get 401, a teacher and a moderator get 403, on every path
  (/admin/api/setup/config, /admin/api/setup/sms-test, /admin/api/setup/site-settings, /admin/api/content/stories ...).
* GET /admin/api/setup/config never sends a secret: saved ones and ones from the server come back as the mask.
* POST /admin/api/setup/config: valid values are stored in config_overrides (secrets encrypted) and take effect at once;
  sending the mask back keeps a secret; bad values get a 422 with one message per field and nothing is stored;
  "reset" gives a setting back to the server value; the security log never holds a secret.
* SEO & Layout Controls (site settings) validation and the public site using them.
* Studio Stories: create, PUT, workflow (draft / review / scheduled / published / archived), view counting, analytics.

Run against the dev profile (run_app.sh dev) on a fresh database:
    python3 tests/system/test_admin_setup.py
"""
import hashlib, hmac, html, json, os, re, subprocess, time, urllib.parse
import requests

B = 'http://localhost:8080'
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
MASK = '•' * 8
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
def form(s, path, data): return s.post(B + path, files={'data': (None, json.dumps(data))}, headers=H(s))
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'}, allow_redirects=False)
def page(s, path):
    r = s.get(B + path)
    m = re.search(r'data-page="([^"]+)"', r.text)
    d = json.loads(html.unescape(m.group(1))) if m else {}
    return d.get('component'), d.get('props', {}), r
def admin_login(email='admin@artistikcity.com'):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False)
    assert r.status_code == 302, (email, r.status_code)
    s.get(B + '/admin/dashboard'); return s
def student():
    s = requests.Session(); s.get(B + '/login')
    r = s.post(B + '/login', json={'email': 'student@artistikcity.com', 'password': 'password'}, headers=H(s))
    assert r.status_code == 200, r.text[:200]
    return s
def fields(payload):
    return {f['key']: f for t in payload['tabs'] for g in t['groups'] for f in g['fields']}
def groups(payload):
    return {f"{t['id']}.{g['id']}": g for t in payload['tabs'] for g in t['groups']}

sql("delete from config_overrides")
adm = admin_login()

# ======================================================================== who may call the new paths
CALLS = [
    ('GET', '/admin/api/setup/config', None), ('POST', '/admin/api/setup/config', {'values': {'commission.inr-per-usd': '90'}}),
    ('POST', '/admin/api/setup/sms-test', {'to': '+919876543210'}), ('POST', '/admin/api/setup/site-settings', None),
    ('GET', '/admin/api/content/stories', None), ('POST', '/admin/api/content/stories', {'data': {'title': 'x'}}),
    ('PUT', '/admin/api/content/stories/1', {'data': {'title': 'x'}}), ('POST', '/admin/api/content/stories/1/workflow', {'workflow': 'archived'}),
]
def call(s, method, path, body):
    if method == 'GET': return get(s, path)
    if method == 'PUT': return put(s, path, body)
    if path.endswith('site-settings'): return form(s, path, {'landing': {'hero_heading': 'Hacked'}})
    return post(s, path, body)

guest = requests.Session(); guest.get(B + '/admin/login')
codes = [call(guest, m, p, b).status_code for m, p, b in CALLS]
check('guest: every new admin API answers 401', all(c == 401 for c in codes), codes)

stu = student()
r = call(stu, 'GET', '/admin/api/setup/config', None)
check('student session: 401 on the setup API', r.status_code == 401, r.status_code)

for who in ('vaishali@artistikcity.com', 'moderator@artistikcity.com'):
    got = []
    for i, (m, p, b) in enumerate(CALLS):
        if i % 4 == 0: s = admin_login(who)            # the security watch signs staff out after 5 refusals
        got.append(call(s, m, p, b).status_code)
    # 403 each time; after 5 refusals in 10 minutes the security watch also ends the staff session (then 401)
    check(f'{who.split("@")[0]}: refused (403) on every new admin API, then signed out (401)', got[:5] == [403] * 5 and all(c in (401, 403) for c in got), got)
    s = admin_login(who)
    comp, props, r = page(s, '/admin/setup')
    check(f'{who.split("@")[0]}: the Configuration Panel page is refused', comp != 'Admin/Setup/Config' and 'setup' not in (props.get('admin') or {}).get('capabilities', []), comp)
check('nothing was stored by the refused calls', sql("select count(*) from config_overrides") == '0' and sql("select count(*) from posts where title = 'x'") == '0'
      and sql("select count(*) from settings where meta_tag = 'landing' and meta_value = 'Hacked'") == '0')

# ======================================================================== GET: secrets are masked
comp, props, r = page(adm, '/admin/setup')
check('admin: Configuration Panel page renders', comp == 'Admin/Setup/Config' and len(props.get('tabs', [])) == 3, comp)
check('page lists the Payments, SMS and Google tabs', [t['id'] for t in props['tabs']] == ['payments', 'sms', 'google'])
r = get(adm, '/admin/api/setup/config'); cfg = r.json(); f = fields(cfg)
check('GET config: 200', r.status_code == 200)
# the dev run passes these secrets on the command line (server values)
check('server secret (Google client secret) comes back masked', f['social.google.client-secret']['value'] == MASK and f['social.google.client-secret']['source'] in ('server', 'default'), f['social.google.client-secret'])
check('server secret (Razorpay webhook secret) comes back masked', f['razorpay.webhook-secret']['value'] == MASK)
check('a secret that is not set is empty, not masked', f['app.payments.stripe.secret-key']['value'] == '' and f['app.payments.stripe.secret-key']['isSet'] is False)
check('no secret value anywhere in the answer', 'g-secret' not in r.text and 'test-wh-secret' not in r.text and 'test-otp-secret' not in r.text)
check('non-secret values are shown', f['social.google.client-id']['value'] == 'g-client' and f['paypal.mode']['type'] == 'select')
check('Stripe is marked not connected', groups(cfg)['payments.stripe']['status'] == 'not_connected')
check('each field says where its value comes from', all(x['source'] in ('panel', 'server', 'default', 'none') for x in f.values()))

# ======================================================================== POST: bad input -> 422, nothing stored
r = post(adm, '/admin/api/setup/config', {'values': {
    'razorpay.key': 'abc', 'paypal.mode': 'production', 'app.sms.provider': 'carrier-pigeon', 'commission.inr-per-usd': 'eighty',
    'social.google.redirect-uri': 'https://evil.example.com/steal', 'app.payments.stripe.secret-key': 'sk_nope', 'spring.datasource.password': 'x',
    'app.sms.twilio.from': '++12'}})
e = r.json().get('errors', {}) if r.headers.get('content-type', '').startswith('application/json') else {}
check('bad values: 422 with one message per field', r.status_code == 422 and {'razorpay.key', 'paypal.mode', 'app.sms.provider', 'commission.inr-per-usd',
      'social.google.redirect-uri', 'app.payments.stripe.secret-key', 'spring.datasource.password', 'app.sms.twilio.from'} <= set(e), (r.status_code, e))
check('messages are descriptive', 'rzp_' in e.get('razorpay.key', '') and 'sandbox' in e.get('paypal.mode', '') and '/social-login/google/callback' in e.get('social.google.redirect-uri', '')
      and "can't be changed here" in e.get('spring.datasource.password', ''), e)
check('...and nothing was stored', sql("select count(*) from config_overrides") == '0')
r = post(adm, '/admin/api/setup/config', {'values': {'app.sms.provider': 'twilio'}})
check('cross-check: Twilio without its SID and token is refused', r.status_code == 422 and 'app.sms.provider' in r.json()['errors'] or any('twilio' in k for k in r.json().get('errors', {})), r.text)
r = post(adm, '/admin/api/setup/config', {'values': {'razorpay.key': 'rzp_test_Abcdefgh1234', 'razorpay.secret': 'short'}})
check('one bad field fails the whole save', r.status_code == 422 and 'razorpay.secret' in r.json()['errors'] and sql("select count(*) from config_overrides") == '0', r.text)
r = adm.post(B + '/admin/api/setup/config', data='not json', headers={**H(adm), 'Content-Type': 'application/json'})
check('a body that is not JSON: 400 with a message', r.status_code == 400 and 'JSON' in r.json().get('message', ''), r.status_code)

# ======================================================================== POST: valid values commit and apply
body = '{"event":"payment.authorized","payload":{}}'
sig = lambda secret: hmac.new(secret.encode(), body.encode(), hashlib.sha256).hexdigest()
hook = lambda secret: requests.post(B + '/api/v1/courses/razorpay-webhook', data=body, headers={'X-Razorpay-Signature': sig(secret), 'Content-Type': 'application/json'}).status_code
check('before: the server webhook secret is the one in use', hook('test-wh-secret') == 200 and hook('panel-webhook-secret-42') == 401)
r = post(adm, '/admin/api/setup/config', {'values': {'commission.inr-per-usd': '86.5', 'razorpay.webhook-secret': 'panel-webhook-secret-42',
                                                     'social.google.scopes': 'openid email profile https://www.googleapis.com/auth/user.birthday.read', 'paypal.mode': 'sandbox'}})
check('valid values: 200', r.status_code == 200 and set(r.json()['changed']) >= {'commission.inr-per-usd', 'razorpay.webhook-secret', 'social.google.scopes'}, r.text)
row = sql("select value_text, value_enc, is_secret from config_overrides where config_key = 'razorpay.webhook-secret'").split('\t')
check('secret stored encrypted (v1:iv:ct), never in clear', row[0] in ('NULL', '') and row[1].startswith('v1:') and 'panel-webhook' not in row[1] and row[2] == '1', row)
check('plain value stored as text', sql("select value_text from config_overrides where config_key = 'commission.inr-per-usd'") == '86.5')
check('the new secret works at once (no restart), the old one is refused', hook('panel-webhook-secret-42') == 200 and hook('test-wh-secret') == 401)
f = fields(r.json()['config'])
check('answer shows the new value with source "panel", secret still masked', f['commission.inr-per-usd']['value'] == '86.5' and f['commission.inr-per-usd']['source'] == 'panel'
      and f['razorpay.webhook-secret']['value'] == MASK and f['razorpay.webhook-secret']['source'] == 'panel' and 'panel-webhook-secret-42' not in r.text)
check('last change is recorded with who made it', (f['commission.inr-per-usd'].get('changed') or {}).get('by'))
enc_before = sql("select value_enc from config_overrides where config_key = 'razorpay.webhook-secret'")
r = post(adm, '/admin/api/setup/config', {'values': {'razorpay.webhook-secret': MASK, 'commission.inr-per-usd': '86.5'}})
check('sending the mask back keeps the secret (nothing changed)', r.status_code == 200 and r.json()['changed'] == []
      and sql("select value_enc from config_overrides where config_key = 'razorpay.webhook-secret'") == enc_before and hook('panel-webhook-secret-42') == 200, r.text)
r = post(adm, '/admin/api/setup/config', {'values': {'razorpay.webhook-secret': ''}})
check('an empty secret box keeps the secret too', r.status_code == 200 and hook('panel-webhook-secret-42') == 200)
loc = requests.get(B + '/social-login/google', allow_redirects=False).headers.get('Location', '')
check('Google scopes from the panel are used', 'user.birthday.read' in urllib.parse.unquote(loc), loc[:300])

audit = sql("select group_concat(concat_ws(' ', action, summary, coalesce(before_json, ''), coalesce(after_json, '')) separator ' ') from audit_logs where resource_type = 'config'")
check('the change is in the security log', 'commission.inr-per-usd' in audit, audit[:300])
leaks = sql("select count(*) from audit_logs where concat_ws(' ', summary, before_json, after_json, path) like '%panel-webhook-secret-42%'")
leaks2 = sql("select count(*) from admin_audit where details like '%panel-webhook-secret-42%'")
placeholder = sql("select count(*) from audit_logs where resource_type = 'config' and after_json like '%(secret, saved%'")
check('no secret ever reaches the security logs (only "(secret, saved <time>)")', leaks == '0' and leaks2 == '0' and int(placeholder) >= 1, (leaks, leaks2, placeholder))

# ======================================================================== reset to the server value
r = post(adm, '/admin/api/setup/config', {'reset': ['razorpay.webhook-secret', 'commission.inr-per-usd']})
f = fields(r.json()['config'])
check('reset: rows removed, server values back', r.status_code == 200 and sql("select count(*) from config_overrides where config_key in ('razorpay.webhook-secret', 'commission.inr-per-usd')") == '0'
      and f['commission.inr-per-usd']['source'] != 'panel' and hook('test-wh-secret') == 200, r.text)
r = post(adm, '/admin/api/setup/config', {'reset': ['spring.datasource.url']})
check('reset of an unknown key: 422', r.status_code == 422)
r = post(adm, '/admin/api/setup/sms-test', {'to': '98765'})
check('test SMS: number without country code -> 422', r.status_code == 422 and 'country code' in r.json()['errors']['to'])
r = post(adm, '/admin/api/setup/sms-test', {'to': '+919876543210'})
check('test SMS through the log provider: 200, says it was only logged', r.status_code == 200 and 'log' in r.json()['message'], r.text)
check('the test is in the security log with the number masked', '+919876543210' not in sql("select coalesce(group_concat(concat_ws(' ', action, summary)), '') from audit_logs where action like '%sms test%'"))

# ======================================================================== sidebar and pages
comp, props, _ = page(adm, '/admin/setup/room-preview')
check('Room Preview Setup moved under /admin/setup/room-preview', comp == 'Admin/Rooms', comp)
check('old room preview address still works', page(adm, '/admin/marketplace/rooms')[0] == 'Admin/Rooms')
r = adm.get(B + '/admin/settings', allow_redirects=False)
check('old site settings address -> SEO & Layout Controls', r.status_code == 302 and r.headers['Location'].endswith('/admin/setup/site-settings'), r.status_code)
comp, sprops, _ = page(adm, '/admin/setup/site-settings')
check('SEO & Layout Controls page renders with every group', comp == 'Admin/Setup/SiteSettings' and {'landing', 'nav', 'cookie', 'social', 'seo', 'contact'} <= set(sprops.get('values', {})), comp)
js = open('src/main/resources/static/js/app.js', encoding='utf-8').read()
check('sidebar has the collapsible System Parameters group', re.search(r'group: ["\']System Parameters["\']', js) is not None and 'collapsible: true' in js and re.search(r'href: ["\']/admin/setup/site-settings', js) is not None)

# ======================================================================== SEO & Layout Controls
def site_save(data): return form(adm, '/admin/api/setup/site-settings', data)
r = site_save({'landing': {'hero_heading': '', 'cta_label': 'Go', 'cta_link': 'javascript:alert(1)'}, 'cookie': {'enabled': '1', 'message': ''},
               'social': {'x': 'twitter.com/x', 'hidden': 'myspace'}, 'nav': {'menu': [{'label': f'L{i}', 'href': '/x'} for i in range(11)]}})
e = r.json().get('errors', {})
check('site settings: bad values -> 422 per field', r.status_code == 422 and {'landing.hero_heading', 'landing.cta_link', 'cookie.message', 'social.x', 'social.hidden', 'nav.menu'} <= set(e), e)
r = site_save({'nav': {'menu': [{'label': 'A', 'href': '//evil.com'}]}})
check('menu link to another site without https -> 422', r.status_code == 422 and 'nav.menu' in r.json()['errors'])
r = site_save({'nav': {'menu': [{'label': 'Shop', 'href': '/marketplace'}, {'label': 'shop', 'href': '/x'}]}})
check('duplicate menu labels -> 422', r.status_code == 422 and 'twice' in r.json()['errors']['nav.menu'])
menu = [{'label': 'Courses', 'href': '/courses?type=all', 'visible': True}, {'label': 'Marketplace', 'href': '/marketplace', 'visible': True, 'accent': True},
        {'label': 'Hidden one', 'href': '/faq', 'visible': False}]
r = site_save({'landing': {'hero_heading': 'Paint with us', 'hero_emphasis': 'live.', 'hero_subtext': 'Small groups.', 'cta_label': 'See courses', 'cta_link': '/courses?type=all',
               'search_placeholder': 'Try watercolour'}, 'nav': {'menu': menu},
               'cookie': {'enabled': '1', 'message': 'We use cookies.', 'accept_label': 'OK', 'decline_label': 'No thanks', 'policy_link': '/privacy-policy'},
               'social': {'instagram': 'https://instagram.com/artistikcity', 'x': 'https://x.com/artistikcity', 'hidden': 'x'}})
check('site settings: valid values saved', r.status_code == 200, r.text[:300])
check('only the groups sent were changed (contact kept)', sql("select count(*) from settings where meta_tag = 'contact'") == sql("select count(*) from settings where meta_tag = 'contact'"))
_, home, _ = page(requests.Session(), '/')
site = home.get('site', {})
check('public site gets the landing hero', (site.get('landing') or {}).get('hero_heading') == 'Paint with us' and site['landing'].get('cta_link') == '/courses?type=all', site.get('landing'))
saved_menu = json.loads((site.get('nav') or {}).get('menu') or '[]')
check('public site gets the menu (order, hidden flag, accent)', [m['label'] for m in saved_menu] == ['Courses', 'Marketplace', 'Hidden one'] and saved_menu[2]['visible'] is False and saved_menu[1]['accent'] is True, saved_menu)
check('public site gets the cookie banner', (site.get('cookie') or {}).get('enabled') == '1' and site['cookie'].get('message') == 'We use cookies.')
check('public site gets the social matrix', (site.get('social') or {}).get('hidden') == 'x' and site['social'].get('x') == 'https://x.com/artistikcity')
check('the site settings change is in the security log', 'landing' in sql("select coalesce(group_concat(concat_ws(' ', summary, coalesce(after_json, ''))), '') from audit_logs where resource_type = 'settings'"))

# ======================================================================== Studio Stories
sql("delete from posts where slug like 'cms-test-%'")
comp, props, _ = page(adm, '/admin/content/stories')
check('Studio Stories dashboard renders', comp == 'Admin/Content/StudioStories' and props.get('mode') == 'list' and 'views30' in (props.get('analytics') or {}), comp)
check('old blog list address -> Studio Stories', adm.get(B + '/admin/blog/post', allow_redirects=False).headers.get('Location', '').endswith('/admin/content/stories'))
r = form(adm, '/admin/api/content/stories', {'title': '', 'workflow': 'published', 'slug': 'Bad Slug!'})
e = r.json().get('errors', {})
check('create: bad input -> 422 per field', r.status_code == 422 and {'title', 'subtitle', 'body', 'slug'} <= set(e), e)
r = form(adm, '/admin/api/content/stories', {'title': 'Scheduling', 'workflow': 'scheduled', 'subtitle': 's', 'body': '<p>text</p>', 'publish_at': '2020-01-01T10:00'})
check('scheduled in the past -> 422', r.status_code == 422 and 'publish_at' in r.json()['errors'])
words = ' '.join(['brush'] * 450)
r = form(adm, '/admin/api/content/stories', {'title': 'CMS test story', 'slug': 'cms-test-story', 'subtitle': 'A test.', 'body': f'<p>{words}</p>', 'workflow': 'draft', 'tags': ['Ink', 'ink', 'Wash']})
check('create a draft', r.status_code == 200 and r.json()['post']['workflow'] == 'draft', r.text[:300])
pid = r.json()['id']
check('read time = words / 200', sql(f"select read_minutes from posts where id = {pid}") == '3')
check('tags de-duplicated', sql(f"select count(*) from post_tag where post_id = {pid}") == '2')
check('draft is not public', requests.get(B + '/blog/post/cms-test-story').status_code == 404)
r = put(adm, f'/admin/api/content/stories/{pid}', {'data': {'title': 'CMS test story (edited)', 'slug': 'cms-test-story', 'subtitle': 'A test.', 'body': f'<p>{words}</p>', 'workflow': 'review'}})
check('PUT (JSON) updates and moves to review', r.status_code == 200 and r.json()['post']['workflow'] == 'review' and sql(f"select status from posts where id = {pid}") == '3', r.text[:300])
r = post(adm, f'/admin/api/content/stories/{pid}/workflow', {'workflow': 'published'})
check('quick action: publish', r.status_code == 200 and sql(f"select status, published_at is not null from posts where id = {pid}") == '1\t1', r.text)
v0 = int(sql(f"select views from posts where id = {pid}"))
UA = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0'}
reader = requests.Session(); reader.headers.update(UA)
check('published story is public', reader.get(B + '/blog/post/cms-test-story').status_code == 200)
reader.get(B + '/blog/post/cms-test-story')
requests.get(B + '/blog/post/cms-test-story', headers=UA)
check('views are counted once per visitor, admins not counted', int(sql(f"select views from posts where id = {pid}")) == v0 + 2 and sql(f"select coalesce(sum(views), 0) from post_views where post_id = {pid}") == '2')
adm.get(B + '/blog/post/cms-test-story', headers=UA)
check('an admin reading it does not count', int(sql(f"select views from posts where id = {pid}")) == v0 + 2)
a = get(adm, '/admin/api/content/stories?q=cms-test').json()
check('list API: search, analytics and counts', a['grid']['rows'][0]['id'] == pid and a['analytics']['views30'] >= 2 and a['analytics']['velocity'] > 0 and a['counts']['published'] >= 1, a['analytics'])
check('top stories include it', any(t['id'] == pid for t in a['analytics']['top']))
r = get(adm, '/admin/api/content/stories?status=archived&q=cms-test')
check('filter by status', r.json()['grid']['rows'] == [])
r = post(adm, f'/admin/api/content/stories/{pid}/workflow', {'workflow': 'archived'})
check('quick action: archive takes it off the site', r.status_code == 200 and requests.get(B + '/blog/post/cms-test-story').status_code == 404 and sql(f"select archived_at is not null from posts where id = {pid}") == '1')
r = post(adm, f'/admin/api/content/stories/{pid}/workflow', {'workflow': 'nonsense'})
check('unknown workflow -> 422', r.status_code == 422)
future = time.strftime('%Y-%m-%dT%H:%M', time.localtime(time.time() + 86400 * 2))
r = form(adm, f'/admin/api/content/stories/{pid}', {'title': 'CMS test story', 'slug': 'cms-test-story', 'subtitle': 'A test.', 'body': f'<p>{words}</p>', 'workflow': 'scheduled', 'publish_at': future})
check('schedule for later', r.status_code == 200 and sql(f"select status from posts where id = {pid}") == '4' and requests.get(B + '/blog/post/cms-test-story').status_code == 404, r.text[:200])
sql(f"update posts set publish_at = date_sub(publish_at, interval 3 day) where id = {pid}")
ok = False
for _ in range(50):
    if sql(f"select status from posts where id = {pid}") == '1': ok = True; break
    time.sleep(3)
check('a due scheduled story is published by itself', ok and requests.get(B + '/blog/post/cms-test-story').status_code == 200)
r = adm.delete(B + f'/admin/api/content/stories/{pid}', headers=H(adm))
check('delete removes the story and its view history', r.status_code == 200 and sql(f"select count(*) from post_views where post_id = {pid}") == '0')

print('ALL PASSED' if fails == 0 else f'{fails} FAILED')
