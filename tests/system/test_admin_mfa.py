"""Two-step verification (MFA) for the admin console. Dev profile (it has an encryption key), fresh database
(schema.sql + seed.sql), from the repository root:   python3 tests/system/test_admin_mfa.py

Covers: set-up from Account › Security (QR code decodes to the otpauth address, wrong code refused, recovery codes),
sign-in now asks for a code (password alone gives no session), wrong codes and lock-out, a code can't be used twice,
recovery codes work once, "trust this browser", turning off / new codes need a current code, the super admin policy
(own MFA first, then staff are taken through the set-up at sign-in), resetting a colleague, permissions, secrets never
leave the server (encrypted in the database, not in page props), and the security log.
"""
import base64, hashlib, hmac, os, re, struct, subprocess, time, urllib.parse
import requests

B = os.environ.get('BASE_URL', 'http://localhost:8080')
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
fails = 0
def sql(q): return subprocess.run(MYSQL + [q], capture_output=True, text=True, check=True).stdout.strip()
def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  {str(extra)[:600]}'))
    if not cond: fails += 1
def xsrf(s): return urllib.parse.unquote(s.cookies.get('XSRF-TOKEN', ''))
def H(s): return {'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'}
def post(s, path, body=None): return s.post(B + path, json=body or {}, headers=H(s))
def put(s, path, body=None): return s.put(B + path, json=body or {}, headers=H(s))
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'})


def totp(secret, step=None):
    key = base64.b32decode(secret.replace(' ', '') + '=' * (-len(secret.replace(' ', '')) % 8))
    step = int(time.time() // 30) if step is None else step
    h = hmac.new(key, struct.pack('>Q', step), hashlib.sha1).digest()
    o = h[-1] & 15
    return '%06d' % ((struct.unpack('>I', h[o:o + 4])[0] & 0x7fffffff) % 1000000)


def fresh_code(secret, used):
    """A code for a step newer than the last one used (waits for the next 30-second step when needed)."""
    while int(time.time() // 30) <= used[0]:
        time.sleep(1)
    used[0] = int(time.time() // 30)
    return totp(secret)


def form_token(html):
    m = re.search(r'name="_token" value="([^"]+)"', html)
    return m.group(1) if m else ''


def password_step(email, pw='password', s=None):
    s = s or requests.Session()
    tok = form_token(s.get(B + '/admin/login').text)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': pw}, allow_redirects=False)
    return s, r


def signed_in(s):
    return s.get(B + '/admin/api/account/mfa', headers={'Accept': 'application/json'}).status_code == 200


def code_step(s, code, path='/admin/login/verify', **extra):
    page = s.get(B + path)
    data = {'_token': form_token(page.text), 'code': code}
    data.update(extra)
    return s.post(B + path, data=data, allow_redirects=False)


def qr_decodes(svg):
    try:
        import cv2, numpy as np
    except ImportError:
        print('(cv2 not installed: QR decode not checked)')
        return None
    vb = re.search(r'viewBox="0 0 (\d+) (\d+)"', svg)
    n = int(vb.group(1))
    img = np.full((n, n), 255, np.uint8)
    for x, y in re.findall(r'M(\d+),(\d+)h1v1h-1z', svg):
        img[int(y), int(x)] = 0
    img = cv2.resize(img, (n * 10, n * 10), interpolation=cv2.INTER_NEAREST)
    img = cv2.copyMakeBorder(img, 40, 40, 40, 40, cv2.BORDER_CONSTANT, value=255)
    text, _, _ = cv2.QRCodeDetector().detectAndDecode(img)
    return text


# ------------------------------------------------------------------ before: password only
s, r = password_step('admin@artistikcity.com')
check('without MFA the password signs in as before', r.status_code == 302 and r.headers['Location'].endswith('/admin/dashboard') and signed_in(s))
adm = s
v = get(adm, '/admin/api/account/mfa').json()
check('status: available, off, super admin can manage', v['available'] and not v['enabled'] and v['canManage'] and len(v['staff']) >= 5, v)
check('the account page loads', adm.get(B + '/admin/account/security').status_code == 200)

# ------------------------------------------------------------------ set-up
r = post(adm, '/admin/api/account/mfa/setup')
j = r.json()
secret = j['secret']
check('set-up: a 32-character Base32 secret, otpauth address and a QR code', r.status_code == 200 and re.fullmatch(r'[A-Z2-7]{32}', secret)
      and j['uri'].startswith('otpauth://totp/ArtistikCity:admin%40artistikcity.com?secret=' + secret) and j['qr'].startswith('<svg'), j.get('uri'))
dec = qr_decodes(j['qr'])
if dec is not None:
    check('the QR code decodes to the otpauth address', dec == j['uri'], dec)
check('not saved before it is confirmed', sql("select coalesce(mfa_secret, '-') from admins where id = 1") == '-')
check('a wrong code is refused', post(adm, '/admin/api/account/mfa/enable', {'code': '000000' if totp(secret) != '000000' else '111111'}).status_code == 422)
used = [int(time.time() // 30)]
r = post(adm, '/admin/api/account/mfa/enable', {'code': totp(secret)})
j = r.json()
codes = j.get('recoveryCodes', [])
check('turned on with 10 recovery codes', r.status_code == 200 and j['enabled'] and len(codes) == 10 and all(re.fullmatch(r'[a-z2-9]{5}-[a-z2-9]{5}', c) for c in codes), r.text[:300])
stored = sql("select mfa_secret from admins where id = 1")
check('the secret is stored encrypted, recovery codes as hashes', secret not in stored and len(stored) > 40
      and sql("select count(*) from admin_mfa_recovery_codes where admin_id = 1") == '10'
      and not any(c.replace('-', '') in sql("select group_concat(code_hash) from admin_mfa_recovery_codes") for c in codes))
props = adm.get(B + '/admin/account/security', headers={'X-Inertia': 'true', 'X-Requested-With': 'XMLHttpRequest'}).text
check('the secret never reaches page props', stored not in props and 'mfa_secret' not in props)
check('audited', sql("select count(*) from admin_audit where details = 'Two-step verification turned on'") == '1'
      and sql("select count(*) from audit_logs where summary like '%Two-step verification turned on%' or action like '%Two-step%'") != '0')

# ------------------------------------------------------------------ sign-in with MFA
s, r = password_step('admin@artistikcity.com')
check('password now leads to the code step, no session yet', r.status_code == 302 and r.headers['Location'] == '/admin/login/verify' and not signed_in(s))
check('the admin API refuses the half-signed-in session', get(s, '/admin/api/landing/overview').status_code == 401)
page = s.get(B + '/admin/login/verify').text
check('code page: asks for the authenticator code', 'Two-step verification' in page and 'one-time-code' in page and 'Use a recovery code' in page)
r = code_step(s, '000000' if totp(secret) != '000000' else '111111')
check('a wrong code: 422, tries left', r.status_code == 422 and '4 tries left' in r.text and not signed_in(s), r.status_code)
code = fresh_code(secret, used)
r = code_step(s, code)
check('the right code signs in', r.status_code == 302 and r.headers['Location'] == '/admin/dashboard' and signed_in(s), (r.status_code, r.headers.get('Location')))
check('sign-in logged with two-step verification', sql("select count(*) from audit_logs where action = 'admin_login' and summary = 'two-step verification'") != '0')
s2, _ = password_step('admin@artistikcity.com')
r = code_step(s2, code)
check('the same code can\'t be used twice', r.status_code == 422 and 'already used' in r.text and not signed_in(s2))
s3, _ = password_step('admin@artistikcity.com')
for i in range(5):
    r = code_step(s3, '%06d' % (123450 + i) if totp(secret) != '%06d' % (123450 + i) else '999999')
check('5 wrong codes: back to the password', r.status_code == 302 and r.headers['Location'] == '/admin/login' and not signed_in(s3))
check('… and the code page is gone', s3.get(B + '/admin/login/verify', allow_redirects=False).headers.get('Location') == '/admin/login')
s4, _ = password_step('admin@artistikcity.com')
r = code_step(s4, codes[0].upper().replace('-', ' '), path='/admin/login/verify?recovery=1', recovery='1')
check('a recovery code signs in (any case / spacing)', r.status_code == 302 and signed_in(s4), r.status_code)
check('… and is used up', get(s4, '/admin/api/account/mfa').json()['recoveryLeft'] == 9)
s5, _ = password_step('admin@artistikcity.com')
check('a used recovery code doesn\'t work again', code_step(s5, codes[0], path='/admin/login/verify?recovery=1', recovery='1').status_code == 422)
s6, _ = password_step('admin@artistikcity.com')
r = code_step(s6, fresh_code(secret, used), trust='1')
check('"trust this browser" sets an HttpOnly cookie for /admin', r.status_code == 302 and 'ac_mfa_trust' in s6.cookies.get_dict() and 'HttpOnly' in r.headers.get('Set-Cookie', ''), r.headers.get('Set-Cookie'))
trust = s6.cookies.get('ac_mfa_trust')
s7 = requests.Session(); s7.cookies.set('ac_mfa_trust', trust, path='/admin')
s7, r = password_step('admin@artistikcity.com', s=s7)
check('a trusted browser skips the code (password still needed)', r.status_code == 302 and r.headers['Location'].endswith('/admin/dashboard') and signed_in(s7))
s8 = requests.Session(); s8.cookies.set('ac_mfa_trust', trust.rsplit('.', 1)[0] + '.' + '0' * 64, path='/admin')
s8, r = password_step('admin@artistikcity.com', s=s8)
check('a forged trust cookie is ignored', r.headers['Location'] == '/admin/login/verify')
r = post(s6, '/admin/api/account/mfa/forget-browsers')
s9 = requests.Session(); s9.cookies.set('ac_mfa_trust', trust, path='/admin')
s9, r = password_step('admin@artistikcity.com', s=s9)
check('"forget trusted browsers" makes old cookies worthless', r.headers['Location'] == '/admin/login/verify')
adm = s6

# ------------------------------------------------------------------ manage
check('new recovery codes need a current code', post(adm, '/admin/api/account/mfa/recovery-codes', {'code': '000000'}).status_code == 422)
r = post(adm, '/admin/api/account/mfa/recovery-codes', {'code': fresh_code(secret, used)})
codes = r.json().get('recoveryCodes', [])
check('new recovery codes replace the old ones', r.status_code == 200 and len(codes) == 10 and r.json()['recoveryLeft'] == 10, r.text[:200])

# ------------------------------------------------------------------ policy and staff
teacher_s, r = password_step('vaishali@artistikcity.com')
teacher = teacher_s
check('teacher (no MFA, not required) signs in with the password', signed_in(teacher))
check('teacher sees no staff list and can\'t change the policy', 'staff' not in get(teacher, '/admin/api/account/mfa').json()
      and put(teacher, '/admin/api/setup/mfa-policy', {'required': True}).status_code == 403)
check('teacher can\'t reset anyone', post(teacher, '/admin/api/setup/mfa/1/reset').status_code == 403)
r = put(adm, '/admin/api/setup/mfa-policy', {'required': True})
check('super admin makes it compulsory (audited)', r.status_code == 200 and r.json()['required'] is True
      and sql("select count(*) from admin_audit where details = 'Two-step verification required for all staff'") == '1', r.text[:200])
sv, r = password_step('vaishali@artistikcity.com')
check('required: staff without MFA are taken to the set-up after the password', r.headers.get('Location') == '/admin/login/setup' and not signed_in(sv))
page = sv.get(B + '/admin/login/setup').text
tsecret = re.search(r'data-testid="mfa-secret">([A-Z2-7 ]+)<', page).group(1).replace(' ', '')
check('set-up page: QR code and the key', '<svg' in page and len(tsecret) == 32 and 'required for every' in page)
r = sv.post(B + '/admin/login/setup', data={'_token': form_token(page), 'code': '000000' if totp(tsecret) != '000000' else '111111'}, allow_redirects=False)
check('set-up: a wrong code is refused', r.status_code == 422 and not signed_in(sv))
page = sv.get(B + '/admin/login/setup').text
r = sv.post(B + '/admin/login/setup', data={'_token': form_token(page), 'code': totp(tsecret)}, allow_redirects=False)
check('set-up done: recovery codes shown once, signed in', r.status_code == 200 and r.text.count('<li><code>') == 10 and signed_in(sv), r.status_code)
check('teacher now has MFA', get(sv, '/admin/api/account/mfa').json()['enabled'] is True)
check('turning it off is refused while it is required', post(sv, '/admin/api/account/mfa/disable', {'code': totp(tsecret)}).status_code == 409)
r = post(adm, '/admin/api/setup/mfa/2/reset')
check('super admin resets a colleague (lost phone), audited', r.status_code == 200 and sql("select coalesce(mfa_secret, '-') from admins where id = 2") == '-'
      and sql("select count(*) from admin_audit where details like 'Two-step verification reset for%'") == '1', r.text[:200])
check('can\'t reset yourself there', post(adm, '/admin/api/setup/mfa/1/reset').status_code == 409)
sv2, r = password_step('vaishali@artistikcity.com')
check('after a reset, the next sign-in asks to set it up again', r.headers.get('Location') == '/admin/login/setup')
check('cancel goes back to the password page', sv2.get(B + '/admin/login/cancel', allow_redirects=False).headers['Location'] == '/admin/login'
      and sv2.get(B + '/admin/login/setup', allow_redirects=False).headers.get('Location') == '/admin/login')
r = put(adm, '/admin/api/setup/mfa-policy', {'required': False})
check('policy back to optional', r.status_code == 200 and r.json()['required'] is False)
r = post(adm, '/admin/api/account/mfa/disable', {'code': fresh_code(secret, used)})
check('super admin turns own MFA off (with a code)', r.status_code == 200 and r.json()['enabled'] is False
      and sql("select count(*) from admin_mfa_recovery_codes where admin_id = 1") == '0', r.text[:200])
r = put(adm, '/admin/api/setup/mfa-policy', {'required': True})
check('can\'t require it for everyone without having it yourself', r.status_code == 409)
s, r = password_step('admin@artistikcity.com')
check('MFA off: the password alone signs in again', signed_in(s))
check('every step is in the security log', all(sql(f"select count(*) from audit_logs where action = '{a}'") != '0' for a in ('admin_mfa_challenge', 'admin_mfa_failed', 'admin_mfa_setup_required')))

print(f'\n{"ALL PASSED" if not fails else str(fails) + " FAILED"}')
raise SystemExit(1 if fails else 0)
