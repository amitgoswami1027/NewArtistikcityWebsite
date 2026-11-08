"""Login & verification on landing pages. Run against a server WITHOUT test mode, SMS provider, mail server or Razorpay
keys, like a fresh live site:   java -jar target/artistikcity-1.0.0.jar   (no dev profile, no PAYMENT_TEST_MODE)
on a fresh database (schema.sql + seed.sql), from the repository root, then   python3 tests/system/test_landing_login_verify.py

Covers: a visitor sees the page and the checkout without an account; the admin page (defaults, permissions, audit,
per-page choice); simulated SMS / e-mail codes; at Pay a guest verifies with a mobile code, gets an account and is signed
in; simulated Razorpay for every visitor; the congratulations result with the single-use "Log in to My studio" link;
the thank-you e-mail (logo, course, "Go to my course"); real accounts are never given a simulated code; an e-mail that
belongs to another account is never attached to a new one; the main log-in page (simulated SMS code, e-mail code,
return_to); switching everything off again.
"""
import glob, html, os, re, subprocess, time, urllib.parse
import requests

B = 'http://localhost:8080'
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
OUTBOX = os.environ.get('MAIL_OUTBOX', 'logs/mail-outbox')
def sql(q): return subprocess.run(MYSQL + [q], capture_output=True, text=True, check=True).stdout.strip()
fails = 0
def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  {str(extra)[:600]}'))
    if not cond: fails += 1
def xsrf(s): return urllib.parse.unquote(s.cookies.get('XSRF-TOKEN', ''))
H = lambda s: {'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'}
def post(s, path, body=None): return s.post(B + path, json=body or {}, headers=H(s))
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'})
def put(s, path, body=None): return s.put(B + path, json=body or {}, headers=H(s))
def staff(email='admin@artistikcity.com'):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False); assert r.status_code == 302, email
    s.get(B + '/admin/dashboard'); return s
def guest():
    s = requests.Session()
    r = s.get(B + '/landing/watercolour-landscapes-for-beginners')
    assert r.status_code == 200, r.status_code
    return s
def outbox_after(t0, needle):
    for f in sorted(glob.glob(os.path.join(OUTBOX, '*.html')), key=os.path.getmtime, reverse=True):
        if os.path.getmtime(f) >= t0 - 1:
            txt = open(f, encoding='utf-8').read()
            if needle in txt: return f, html.unescape(txt)
    return None, ''

adm = staff()
teacher = staff('vaishali@artistikcity.com')

# ------------------------------------------------------------------ admin page and defaults
check('teacher can\'t open Login & verification', get(teacher, '/admin/api/landing/login').status_code == 403)
v = get(adm, '/admin/api/landing/login').json()
check('defaults: mobile code at Pay, no simulation', v['settings'] == {'checkoutVerify': 'PHONE', 'simulatePhone': False, 'simulateEmail': False}, v['settings'])
check('server: no SMS provider, no mail server', v['server']['sms'] is False and v['server']['mail'] is False and v['server']['phoneReady'] is False, v['server'])
check('warns that codes can\'t be sent yet', any('can\'t send them yet' in w for w in v['warnings']), v['warnings'])
check('every course page listed with its setting', any(p['id'] == 1 and p['verifyWith'] == 'DEFAULT' and p['channels'] == [] for p in v['pages']), v['pages'])
check('the admin tab exists', adm.get(B + '/admin/landing/login').status_code == 200)

g = guest()
st = get(g, '/landing/checkout/1/state').json()
check('nothing set up: a guest is asked to sign in first (as before)', st['user'] is None and st['verify']['mode'] == 'PHONE' and st['verify']['channels'] == [], st['verify'])
check('without setup, sending a code is refused with sign-in', post(g, '/landing/checkout/1/verify/send', {'channel': 'phone', 'name': 'A B', 'email': 'a@b.co', 'phone': '9876500011'}).json().get('signIn') is True)

check('invalid mode refused', put(adm, '/admin/api/landing/login', {'checkoutVerify': 'FAX'}).status_code == 422)
r = put(adm, '/admin/api/landing/login', {'simulatePhone': True})
check('simulated SMS codes switched on, audited', r.status_code == 200 and r.json()['settings']['simulatePhone'] is True
      and sql("select count(*) from admin_audit where details like 'Login & verification: simulated SMS codes on%'") == '1', r.text)
check('warning about simulated codes', any('Simulated codes are on' in w for w in r.json()['warnings']))
r = post(adm, '/admin/api/landing/test-payments/simulate-razorpay', {'on': True})
check('simulated Razorpay on', r.status_code == 200)
st = get(g, '/landing/checkout/1/state').json()
check('guest who can verify with a code: Pay is open (who they are decides after the code)', st['paymentsClosed'] is False, st)
check('teacher can\'t let everyone pay', post(teacher, '/admin/api/landing/test-payments/simulate-razorpay', {'everyone': True}).status_code == 403)
r = post(adm, '/admin/api/landing/test-payments/simulate-razorpay', {'everyone': True})
check('every visitor may pay with simulated Razorpay, audited', r.status_code == 200 and r.json()['simulateForEveryone'] is True
      and sql("select count(*) from admin_audit where details = 'Simulated Razorpay: every visitor may pay'") == '1', r.text)

# ------------------------------------------------------------------ a guest buys
st = get(g, '/landing/checkout/1/state').json()
check('guest sees the checkout without an account: mobile code at Pay', st['user'] is None and st['verify']['channels'] == ['phone']
      and st['verify']['simulated']['phone'] is True and st['paymentsClosed'] is False and st['simulatedForEveryone'] is True, st['verify'])
check('guest sees the payment methods', [c['key'] for c in st['choices']] == ['upi', 'card', 'netbanking'])
check('guest can try a coupon', post(g, '/landing/checkout/1/quote', {'coupon': 'WATERCOLOUR10'}).json()['quote']['coupon'] == 'WATERCOLOUR10')
check('guest can\'t place an order before verifying', post(g, '/landing/checkout/1/order', {'name': 'X', 'terms': True}).status_code == 401)
r = post(g, '/landing/checkout/1/verify/send', {'channel': 'phone', 'name': '', 'email': 'nope', 'phone': '12'})
check('send: details checked', r.status_code == 422 and set(r.json()['errors']) == {'name', 'email', 'phone'}, r.text)
r = post(g, '/landing/checkout/1/verify/send', {'channel': 'phone', 'name': 'Meera Joshi', 'email': 'meera.joshi@example.com', 'phone': '98765 00011'})
j = r.json()
check('send: simulated code shown, number masked, nothing sent', r.status_code == 200 and j['simulated'] is True and re.fullmatch(r'\d{6}', j.get('code', ''))
      and j['channel'] == 'phone' and '9876500011' not in j['masked'], j)
t_sent = time.time()
check('send: logged as simulated, not sent', sql("select count(*) from auth_events where event = 'otp_simulated'") == '1' and sql("select count(*) from auth_events where event = 'otp_sent'") == '0')
code = j['code']
check('resend too soon is limited', post(g, '/landing/checkout/1/verify/send', {'channel': 'phone', 'name': 'Meera Joshi', 'email': 'meera.joshi@example.com', 'phone': '9876500011'}).status_code == 429)
wrong = '000000' if code != '000000' else '111111'
r = post(g, '/landing/checkout/1/verify/check', {'code': wrong})
check('wrong code: tries left', r.status_code == 422 and r.json()['attemptsLeft'] == 4, r.text)
other = requests.Session(); other.get(B + '/landing/watercolour-landscapes-for-beginners')
check('the code can\'t be used from another browser', post(other, '/landing/checkout/1/verify/check', {'code': code}).status_code == 410)
r = post(g, '/landing/checkout/1/verify/check', {'code': code})
check('right code: signed in, account created', r.status_code == 200 and r.json()['created'] is True and r.json()['user']['email'] == 'meera.joshi@example.com', r.text)
uid = sql("select id from users where email = 'meera.joshi@example.com'")
check('account: verified mobile, name, marked as made with a simulated code',
      sql(f"select concat(phone_number, '/', phone_verified_at is not null, '/', name) from users where id = {uid}") == '+919876500011/1/Meera Joshi'
      and sql(f"select count(*) from auth_events where user_id = {uid} and event = 'simulated_signup'") == '1')
check('session method is otp', sql(f"select auth_method from auth_sessions where user_id = {uid} order by id desc limit 1") == 'otp')
st = get(g, '/landing/checkout/1/state').json()
check('state: signed in, no verify step', st['user']['email'] == 'meera.joshi@example.com' and st['verify'] is None)
o = post(g, '/landing/checkout/1/order', {'name': 'Meera Joshi', 'phone': '9876500011', 'terms': True}).json()['orderId']
r = post(g, '/landing/checkout/1/pay', {'orderId': o, 'method': 'upi'})
check('a new buyer pays in the simulated Razorpay window', r.status_code == 200 and r.json()['gateway'] == 'razorpay-sim', r.text)
t0 = time.time()
r = post(g, '/landing/checkout/1/simulate', {'orderId': o, 'outcome': 'success'})
check('paid, as a test order', r.status_code == 200 and sql(f"select concat(payment_status, '/', is_test) from orders where id = {o}") == '1/1', r.text)
res = get(g, f'/landing/checkout/1/state?order={o}').json()['result']
check('congratulations: name, success and a "Log in to My studio" link', res['state'] == 'success' and res['name'] == 'Meera Joshi'
      and res['continueUrl'].startswith('/auth/continue?t=') and res['mailed'] is False, res)
again = get(g, f'/landing/checkout/1/state?order={o}').json()['result']
check('the same link while it is unused', again['continueUrl'] == res['continueUrl'])

f, mail = outbox_after(t0, 'Congratulations')
check('thank-you e-mail written', f is not None, os.listdir(OUTBOX)[-3:] if os.path.isdir(OUTBOX) else 'no outbox')
check('e-mail: logo, congratulations, course, amount, order', 'assets/images/logo.png' in mail and 'Congratulations, Meera!' in mail
      and 'Watercolour Landscapes' in mail and sql(f"select order_number from orders where id = {o}") in mail and '₹' in mail)
check('e-mail: "Go to my course" button to the log-in page, then the course', 'Go to my course' in mail
      and f'/login?return_to=%2Fuser%2Fcourse%2F{o}' in mail, re.findall(r'href="([^"]*login[^"]*)"', mail))
check('e-mail: log in with the mobile or e-mail code', 'one-time code' in mail and '9876500011' not in mail and 'test payment' in mail)
check('the standard confirmation is not sent as well', not [x for x in glob.glob(os.path.join(OUTBOX, '*order-confirmation*')) if os.path.getmtime(x) >= t0 - 1])

# "Log in to My studio"
stranger = requests.Session()
r = stranger.get(B + res['continueUrl'], allow_redirects=False)
check('the link does nothing in another browser', r.status_code == 302 and r.headers['Location'].startswith('/login'), r.headers.get('Location'))
fresh = requests.Session(); fresh.cookies.update({k: v for k, v in g.cookies.items() if k not in ('ac_session', 'ac_refresh')})
r = fresh.get(B + res['continueUrl'], allow_redirects=False)
check('the link signs the buyer in and opens My studio', r.status_code == 302 and r.headers['Location'] == '/dashboard', (r.status_code, r.headers.get('Location')))
check('… really signed in', fresh.get(B + '/dashboard', allow_redirects=False).status_code == 200)
r = fresh.get(B + res['continueUrl'], allow_redirects=False)
check('single use (signed in already: still My studio)', r.status_code == 302 and r.headers['Location'] == '/dashboard')
check('signed in, /login?return_to= goes to the course', fresh.get(B + f'/login?return_to=/user/course/{o}', allow_redirects=False).headers['Location'] == f'/user/course/{o}')
check('… and never to another site', fresh.get(B + '/login?return_to=//evil.example', allow_redirects=False).headers['Location'] == '/dashboard')

# ------------------------------------------------------------------ real accounts stay safe
sql("update users set phone_number = '+919876500099', phone_verified_at = '2026-01-01 00:00:00' where email = 'ishita.bose@example.com'")
g2 = guest()
r = post(g2, '/landing/checkout/1/verify/send', {'channel': 'phone', 'name': 'Someone', 'email': 'someone.new@example.com', 'phone': '9876500099'})
check('a real account\'s number never gets a simulated code', r.status_code == 409 and r.json().get('code') == 'CODE_UNAVAILABLE'
      and r.json().get('signIn') is True, r.text)
r = post(g2, '/landing/checkout/1/verify/send', {'channel': 'phone', 'name': 'Diya', 'email': 'diya@example.com', 'phone': '9876500012'})
check('a new number with somebody\'s e-mail: not attached, log in instead', r.status_code == 409 and r.json()['code'] == 'EMAIL_HAS_ACCOUNT', r.text)
put(adm, '/admin/api/landing/login', {'simulateEmail': True})
r = post(g2, '/landing/checkout/1/verify/send', {'channel': 'phone', 'name': 'Diya', 'email': 'diya@example.com', 'phone': '9876500012'})
check('… with e-mail codes on, a real account still gets no simulated e-mail code', r.status_code == 409 and r.json().get('code') == 'CODE_UNAVAILABLE', r.text)
post(adm, '/admin/api/landing/test-payments/buyers', {'email': 'diya@example.com'})
r = post(g2, '/landing/checkout/1/verify/send', {'channel': 'phone', 'name': 'Diya Patel', 'email': 'diya@example.com', 'phone': '9876500012'})
j = r.json()
check('a test buyer\'s e-mail: the code goes to that e-mail instead (simulated)', r.status_code == 200 and j['channel'] == 'email' and j['simulated'] is True and 'notice' in j, j)
r = post(g2, '/landing/checkout/1/verify/check', {'code': j['code']})
check('… and signs in to that account, nothing new created', r.status_code == 200 and r.json()['created'] is False and r.json()['user']['email'] == 'diya@example.com'
      and sql("select count(*) from users where phone_number = '+919876500012'") == '0', r.text)

# ------------------------------------------------------------------ per-page choice
r = put(adm, '/admin/api/landing/pages/1/checkout', {'requirePhone': True, 'requireTerms': True, 'showCoupon': True, 'showSummary': True, 'verifyWith': 'EMAIL'})
check('page set to e-mail code', r.status_code == 200 and r.json()['checkout']['verifyWith'] == 'EMAIL', r.text)
check('… bad value refused', put(adm, '/admin/api/landing/pages/1/checkout', {'verifyWith': 'POST'}).status_code == 422)
st = get(guest(), '/landing/checkout/1/state').json()
check('the page now asks for an e-mail code', st['verify']['mode'] == 'EMAIL' and st['verify']['channels'] == ['email'], st['verify'])
g3 = guest()
r = post(g3, '/landing/checkout/1/verify/send', {'channel': 'phone', 'name': 'Kabir Rao', 'email': 'kabir.rao@example.com', 'phone': ''})
j = r.json()
check('e-mail page: a new address gets a simulated e-mail code', r.status_code == 200 and j['channel'] == 'email' and j['simulated'] is True, j)
r = post(g3, '/landing/checkout/1/verify/check', {'code': j['code']})
check('… new account with a verified e-mail', r.status_code == 200 and r.json()['created'] is True
      and sql("select email_verified_at is not null from users where email = 'kabir.rao@example.com'") == '1', r.text)
check('admin list shows the page\'s own choice', any(p['id'] == 1 and p['verifyWith'] == 'EMAIL' and p['channels'] == ['email'] for p in get(adm, '/admin/api/landing/login').json()['pages']))
put(adm, '/admin/api/landing/pages/1/checkout', {'requirePhone': True, 'requireTerms': True, 'showCoupon': True, 'showSummary': True, 'verifyWith': 'SIGNIN'})
st = get(guest(), '/landing/checkout/1/state').json()
check('"sign in first": no code at Pay', st['verify']['mode'] == 'SIGNIN' and st['verify']['channels'] == [])
put(adm, '/admin/api/landing/pages/1/checkout', {'requirePhone': True, 'requireTerms': True, 'showCoupon': True, 'showSummary': True, 'verifyWith': 'DEFAULT'})

# ------------------------------------------------------------------ the main log-in page
l = requests.Session(); l.get(B + '/login')
props = l.get(B + '/login', headers={'X-Inertia': 'true', 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'text/html, application/xhtml+xml'}).json()['props']
check('log-in page: e-mail code offered, simulation flags', props.get('emailCode') is True and props.get('simulated') == {'phone': True, 'email': True}, {k: props.get(k) for k in ('emailCode', 'simulated')})
time.sleep(max(0, 31 - (time.time() - t_sent)))     # one code per number every 30 seconds
r = post(l, '/auth/otp/send', {'phone': '+919876500011'})
t_sent = time.time()
check('log in with the mobile: the simulated account gets a simulated code', r.status_code == 200 and r.json()['simulated'] is True and r.json().get('devCode'), r.text)
r = post(l, '/auth/otp/verify', {'phone': '+919876500011', 'code': r.json()['devCode'], 'remember': True})
check('… and is signed in', r.status_code == 200 and get(l, '/landing/checkout/1/state').json()['user']['email'] == 'meera.joshi@example.com', r.text)
l2 = requests.Session(); l2.get(B + '/login')
check('log in with a real account\'s mobile: refused, no code shown', post(l2, '/auth/otp/send', {'phone': '+919876500099'}).status_code == 409)
l3 = requests.Session(); l3.get(B + f'/login?return_to=/user/course/{o}')
r = post(l3, '/auth/email-code/send', {'email': 'meera.joshi@example.com'})
check('log in with an e-mail code (simulated account)', r.status_code == 200 and r.json()['simulated'] is True, r.text)
r = post(l3, '/auth/email-code/verify', {'email': 'meera.joshi@example.com', 'code': r.json()['devCode'], 'returnTo': True})
check('… then on to the course from the e-mail\'s button', r.status_code == 200 and r.json()['redirect'] == f'/user/course/{o}', r.text)
check('… the course opens', l3.get(B + f'/user/course/{o}', allow_redirects=False).status_code == 200)
l4 = requests.Session(); l4.get(B + '/login')
r = post(l4, '/auth/email-code/send', {'email': 'ishita.bose@example.com'})
check('a real account gets no simulated e-mail code', r.status_code == 409 and 'devCode' not in r.json(), r.text)

# ------------------------------------------------------------------ switching off
post(adm, '/admin/api/landing/test-payments/simulate-razorpay', {'everyone': False})
check('test buyers only: an account made with a simulated code may still pay', get(l3, '/landing/checkout/1/state').json()['paymentsClosed'] is False)
ish = requests.Session(); ish.get(B + '/login')
assert post(ish, '/login', {'email': 'ishita.bose@example.com', 'password': 'password'}).status_code == 200
check('… a real account is told payment opens soon', get(ish, '/landing/checkout/1/state').json()['paymentsClosed'] is True)
ishita_order = post(ish, '/landing/checkout/1/order', {'name': 'Ishita Bose', 'phone': '9876500099', 'terms': True}).json()['orderId']
check('… and can\'t pay', post(ish, '/landing/checkout/1/pay', {'orderId': ishita_order, 'method': 'upi'}).status_code == 503)
put(adm, '/admin/api/landing/login', {'simulatePhone': False, 'simulateEmail': False})
st = get(guest(), '/landing/checkout/1/state').json()
check('simulation off: back to signing in first, payment closed for guests', st['verify']['channels'] == [] and st['paymentsClosed'] is True, st['verify'])
time.sleep(max(0, 31 - (time.time() - t_sent)))
l5 = requests.Session(); l5.get(B + '/login')
r = post(l5, '/auth/otp/send', {'phone': '+919876500011'})
check('… the code is no longer shown on the screen', r.status_code == 200 and r.json()['simulated'] is False and 'devCode' not in r.json(), r.text)

print(f'\n{"ALL PASSED" if not fails else str(fails) + " FAILED"}')
raise SystemExit(1 if fails else 0)
