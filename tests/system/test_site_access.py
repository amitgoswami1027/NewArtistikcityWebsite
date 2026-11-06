"""Site password protection (Admin > System parameters > Site access, /admin/parameters), end to end (HTTP + DB).

* Off by default: the site is open and GET /admin/api/setup/site-access says so.
* Only super admins may read or change it (guest 401, student 401, teacher / moderator 403).
* Validation: switching on without a password, a short password, a mismatched confirmation -> 422, nothing stored.
* Switching on: every page shows "Site under construction" (401, noindex), APIs answer JSON 401, in-app (Inertia) visits get
  409 + X-Inertia-Location, robots.txt disallows all; health check, webhooks, static files and the admin stay open; an admin
  signed in sees the site.
* The right password sets an HttpOnly cookie that holds a token (never the password) and opens pages and APIs; a wrong one
  doesn't; a new password signs everyone out; switching off opens the site at once.
* Stored as a bcrypt hash under settings.meta_tag = 'system' (never in the public settings); the security log has
  "password changed" and never the password.

Run against the dev profile (run_app.sh dev):
    python3 tests/system/test_site_access.py
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
API = '/admin/api/setup/site-access'
def save(s, body): return s.post(B + API, json=body, headers=H(s))
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
def component(r):
    m = re.search(r'data-page="([^"]+)"', r.text)
    return json.loads(html.unescape(m.group(1))).get('component') if m else None

PW1, PW2 = 'Gallery-Night-26', 'Second-Preview-77'
sql("delete from settings where meta_tag = 'system' and meta_key like 'site\\_lock\\_%'")
adm = admin_login()

# ------------------------------------------------------------------ off by default, who may use it
r = adm.get(B + API, headers={'Accept': 'application/json'})
check('admin: GET site access -> off, no password', r.status_code == 200 and r.json()['enabled'] is False and r.json()['hasPassword'] is False, r.text)
check('the API never returns a password or hash', not re.search(r'\$2[aby]\$|password"\s*:\s*"', r.text), r.text)
r = adm.get(B + '/admin/parameters')
check('/admin/parameters renders the Site access screen', r.status_code == 200 and component(r) == 'Admin/Setup/SiteAccess', (r.status_code, component(r)))
r = adm.get(B + '/admin/setup/site-access')
check('/admin/setup/site-access is the same screen', component(r) == 'Admin/Setup/SiteAccess', component(r))
check('site open by default', requests.get(B + '/marketplace').status_code == 200)

guest = requests.Session(); guest.get(B + '/admin/login')
check('guest: 401 on the site-access API', guest.get(B + API, headers={'Accept': 'application/json'}).status_code == 401
      and save(guest, {'enabled': True, 'password': PW1, 'confirm': PW1}).status_code == 401)
stu = student()
check('student: 401 on the site-access API', save(stu, {'enabled': True, 'password': PW1, 'confirm': PW1}).status_code == 401)
for who in ('vaishali@artistikcity.com', 'moderator@artistikcity.com'):
    s = admin_login(who)
    check(f'{who}: 403 on GET and POST', s.get(B + API, headers={'Accept': 'application/json'}).status_code == 403
          and save(s, {'enabled': True, 'password': PW1, 'confirm': PW1}).status_code == 403)
    check(f'{who}: the screen is "no access"', component(s.get(B + '/admin/parameters')) == 'Admin/Denied')
check('nothing stored by refused calls', sql("select count(*) from settings where meta_key like 'site\\_lock\\_%'") == '0')

# ------------------------------------------------------------------ validation
r = save(adm, {'enabled': True})
check('switching on without a password -> 422 on password', r.status_code == 422 and 'password' in r.json().get('errors', {}), r.text)
r = save(adm, {'enabled': True, 'password': 'short', 'confirm': 'short'})
check('short password -> 422', r.status_code == 422 and 'at least' in r.json()['errors']['password'], r.text)
r = save(adm, {'enabled': True, 'password': PW1, 'confirm': PW1 + 'x'})
check('confirmation mismatch -> 422 on confirm', r.status_code == 422 and 'confirm' in r.json()['errors'], r.text)
r = save(adm, {'enabled': 'yes'})
check('enabled must be true/false -> 400', r.status_code == 400, r.status_code)
check('still nothing stored after 422s', sql("select count(*) from settings where meta_key like 'site\\_lock\\_%'") == '0')
check('site still open after refused saves', requests.get(B + '/').status_code == 200)

# ------------------------------------------------------------------ switch on
r = save(adm, {'enabled': True, 'password': PW1, 'confirm': PW1})
check('admin switches protection on with a password', r.status_code == 200 and r.json()['access']['active'] is True, r.text)
h = sql("select meta_value from settings where meta_tag = 'system' and meta_key = 'site_lock_hash'")
check('stored as a bcrypt hash, not the password', h.startswith('$2') and PW1 not in h, h[:10])
check('stored under the private "system" tag only', sql(f"select count(*) from settings where meta_value like '%{PW1}%'") == '0'
      and sql("select meta_tag from settings where meta_key = 'site_lock_hash'") == 'system')

v = requests.Session()
r = v.get(B + '/marketplace')
check('visitor: page -> 401 "Site under construction"', r.status_code == 401 and 'Site under construction' in r.text and 'Access password' in r.text, r.status_code)
check('visitor: the real page is not in the response', 'data-page=' not in r.text)
check('visitor: noindex header', 'noindex' in r.headers.get('X-Robots-Tag', ''))
for path in ('/', '/courses', '/studio-stories', '/login', '/register', '/marketplace/scarlet-macaw-study', '/storage/uploads/artworks/1/artwork.jpg'):
    check(f'visitor: {path} locked', v.get(B + path).status_code == 401)
r = v.get(B + '/api/studio-stories', headers={'Accept': 'application/json'})
check('visitor: API -> JSON 401 PREVIEW_LOCKED', r.status_code == 401 and r.json().get('code') == 'PREVIEW_LOCKED', r.text[:200])
r = v.post(B + '/login', json={'email': 'student@artistikcity.com', 'password': 'password'}, headers={'Accept': 'application/json'})
check('visitor: login API locked too', r.status_code == 401 and 'PREVIEW_LOCKED' in r.text, r.status_code)
r = v.get(B + '/courses', headers={'X-Inertia': 'true', 'X-Requested-With': 'XMLHttpRequest'})
check('visitor: Inertia visit -> 409 + X-Inertia-Location', r.status_code == 409 and r.headers.get('X-Inertia-Location') == '/courses', (r.status_code, r.headers))
check('robots.txt disallows all', 'Disallow: /' in v.get(B + '/robots.txt').text)
check('health check open', v.get(B + '/healthz').status_code == 200)
check('static files open', v.get(B + '/assets/images/logo.png').status_code == 200)
check('webhook path not locked', v.post(B + '/api/v1/marketplace/webhook', data='{}').status_code != 401)
check('admin sign-in page open', v.get(B + '/admin/login').status_code == 200)
check('signed-in admin sees the site', adm.get(B + '/marketplace').status_code == 200 and 'data-page=' in adm.get(B + '/marketplace').text)

# ------------------------------------------------------------------ the password form
r = v.post(B + '/__preview', data={'password': 'wrong-password-1', 'next': '/courses'}, allow_redirects=False)
check('wrong password -> 401 with message, no cookie', r.status_code == 401 and "isn't right" in r.text and 'ac_preview' not in v.cookies, r.status_code)
r = v.post(B + '/__preview', data={'password': PW1, 'next': '/courses?type=all'}, allow_redirects=False)
check('right password -> 303 back to the page asked for', r.status_code == 303 and r.headers.get('Location') == '/courses?type=all', (r.status_code, r.headers.get('Location')))
ck = r.headers.get('Set-Cookie', '')
check('cookie is HttpOnly, SameSite=Lax, 30 days', 'HttpOnly' in ck and 'SameSite=Lax' in ck and 'Max-Age=2592000' in ck, ck)
check('cookie holds a token, not the password', PW1 not in ck and v.cookies.get('ac_preview', '').startswith('s.'), ck)
r = v.post(B + '/__preview', data={'password': PW1, 'next': 'https://evil.example.com/'}, allow_redirects=False)
check('next to another site is ignored', r.headers.get('Location') == '/', r.headers.get('Location'))
check('with the cookie: pages open', v.get(B + '/marketplace').status_code == 200)
check('with the cookie: APIs open', v.get(B + '/api/studio-stories', headers={'Accept': 'application/json'}).status_code == 200)
r = v.post(B + '/login', json={'email': 'student@artistikcity.com', 'password': 'password'}, headers=H(v))
check('with the cookie: a student can sign in', r.status_code == 200, r.status_code)
forged = requests.Session(); forged.cookies.set('ac_preview', 's.' + '0' * 64)
check('forged cookie refused', forged.get(B + '/').status_code == 401)

# ------------------------------------------------------------------ change password, then switch off
r = save(adm, {'enabled': True, 'password': PW1, 'confirm': PW1})
check('the same password again -> 422', r.status_code == 422, r.text)
r = save(adm, {'enabled': True, 'password': PW2, 'confirm': PW2})
check('new password saved', r.status_code == 200 and 'Everyone who used the old one' in r.json()['message'], r.text)
check('new password signs out holders of the old cookie', v.get(B + '/marketplace').status_code == 401)
check('old password no longer works', v.post(B + '/__preview', data={'password': PW1}, allow_redirects=False).status_code == 401)
check('new password works', v.post(B + '/__preview', data={'password': PW2}, allow_redirects=False).status_code == 303)

r = save(adm, {'enabled': False})
check('admin switches protection off', r.status_code == 200 and r.json()['access']['active'] is False and r.json()['access']['hasPassword'] is True, r.text)
check('site open to everyone at once', requests.get(B + '/marketplace').status_code == 200 and requests.get(B + '/robots.txt').text.find('Disallow: /\n') == -1)
r = save(adm, {'enabled': True})
check('switching back on reuses the saved password (no new one needed)', r.status_code == 200 and r.json()['access']['active'] is True, r.text)
check('...and the earlier cookie for that password still works', v.get(B + '/marketplace').status_code == 200)
save(adm, {'enabled': False})

# ------------------------------------------------------------------ audit
log = sql("select concat_ws(' | ', action, coalesce(summary, ''), coalesce(cast(before_json as char), ''), coalesce(cast(after_json as char), '')) from audit_logs where action like 'site_access%' order by id")
check('security log has the switch and the password changes', 'protection switched on' in log and 'password changed' in log and 'protection switched off' in log, log)
check('security log never holds the password or its hash', PW1 not in log and PW2 not in log and '$2' not in log, log)
r = adm.get(B + API, headers={'Accept': 'application/json'})
check('changedBy names the admin', 'admin@artistikcity.com' in (r.json().get('changedBy') or ''), r.text)

sql("delete from settings where meta_tag = 'system' and meta_key like 'site\\_lock\\_%'")
print('\n' + ('ALL PASSED' if fails == 0 else f'{fails} FAILED'))
raise SystemExit(1 if fails else 0)
