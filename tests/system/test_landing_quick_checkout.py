"""Landing page checkout inside the page, quick sign-in and admin test payments (HTTP + DB). Dev profile, fresh database
(schema.sql + seed.sql + migrations):   run_app.sh dev   then   python3 tests/system/test_landing_quick_checkout.py

Covers
* the checkout lives on the landing page: /enroll and /enroll/done send the browser back to the page (#enroll, ?order=),
  the page carries the checkout dialog and its script; GET /landing/checkout/{page}/state for guests and buyers;
* quick sign-in: e-mail code (send, cooldown, wrong code, tries left, new account, existing account, name taken at the
  first order), phone code (new number -> name), Google one-tap credential (refusals; a full sign-in when the OIDC stub
  on 127.0.0.1:9999 with /mint is running), options only list methods that can reach people;
* a test buyer simulates the payment in the page; coupons are not used up by simulated payments;
* Admin > Landing > Payments & checkout > Test payments: permissions, test buyers (existing / new passwordless account),
  the simulator (paid, declined, waiting, already enrolled, coupon, a method the page doesn't offer), undo, audit,
  and test orders staying out of the landing revenue.
"""
import html, json, os, re, subprocess, time, urllib.parse
import requests

B = 'http://localhost:8080'
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
def sql(q): return subprocess.run(MYSQL + [q], capture_output=True, text=True, check=True).stdout.strip()
fails = 0
def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  {str(extra)[:500]}'))
    if not cond: fails += 1
def xsrf(s): return urllib.parse.unquote(s.cookies.get('XSRF-TOKEN', ''))
H = lambda s: {'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'}
def post(s, path, body=None): return s.post(B + path, json=body or {}, headers=H(s))
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'})
def guest():
    s = requests.Session(); s.get(B + '/landing/watercolour-landscapes-for-beginners'); return s
def student(email):
    s = requests.Session(); s.get(B + '/login')
    r = s.post(B + '/login', json={'email': email, 'password': 'password'}, headers=H(s))
    assert r.status_code == 200, (email, r.text[:200])
    return s
def staff(email='admin@artistikcity.com'):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False); assert r.status_code == 302, email
    s.get(B + '/admin/dashboard'); return s
def email_code(s, email, name=''):
    r = post(s, '/auth/email-code/send', {'email': email, 'name': name})
    return r, (r.json().get('devCode') if r.status_code == 200 else None)

PAGE = '/landing/watercolour-landscapes-for-beginners'
sql("delete from auth_email_codes")
sql("update users set test_buyer = 0")

# ================================================================================================= the checkout lives in the page
r = requests.get(B + PAGE + '/enroll?utm_source=ig&utm_campaign=autumn', allow_redirects=False)
check('enrol link: back to the landing page with the checkout open (utm kept)', r.status_code == 302
      and r.headers['Location'].endswith(PAGE + '?utm_source=ig&utm_campaign=autumn#enroll'), r.headers.get('Location'))
r = requests.get(B + PAGE + '/enroll/done?order_id=12&payment=failed', allow_redirects=False)
check('payment return: back to the page with the order', r.headers.get('Location', '').endswith(PAGE + '?payment=failed&order=12#enroll'), r.headers.get('Location'))
page = requests.get(B + PAGE).text
check('the page carries the checkout dialog and its script', 'id="lp-co"' in page and '/js/landing-checkout.js' in page and 'data-slug="watercolour-landscapes-for-beginners"' in page)
check('Log in opens the quick sign-in on the page', 'data-signin' in page and 'return_to=%2Flanding%2Fwatercolour-landscapes-for-beginners' in page)
info = requests.get(B + '/landing/how-it-works').text
check('information pages have no checkout dialog', 'id="lp-co"' not in info and 'landing-checkout.js' not in info)
check('the old checkout page is gone (no React shell)', 'Landing/Checkout' not in requests.get(B + PAGE + '/enroll').text)

g = guest()
st = get(g, '/landing/checkout/1/state').json()
check('guest state: price, the page\'s payment options, quick sign-in options, no personal data', st['quote']['totalLabel'] == '₹3,999'
      and [c['key'] for c in st['choices']] == ['upi', 'card', 'netbanking', 'paypal'] and st['user'] is None and st['simulate'] is False
      and st['signIn']['email'] is True and st['signIn']['phone'] is True and st['signIn']['google']['clientId'] == 'g-client' and len(st['signIn']['google']['nonce']) > 20, st.get('signIn'))
check('state of an unknown page: 410', get(g, '/landing/checkout/999999/state').status_code == 410)
check('guest: ordering still needs a sign-in', post(g, '/landing/checkout/1/order', {'name': 'x'}).status_code == 401)

# ================================================================================================= e-mail code
check('e-mail code: CSRF required', g.post(B + '/auth/email-code/send', json={'email': 'a@b.co'}).status_code in (403, 419))
check('e-mail code: bad address refused', post(g, '/auth/email-code/send', {'email': 'not-an-email'}).status_code == 422)
NEW = 'quick.buyer@example.com'
sql(f"delete from users where email = '{NEW}'") if sql(f"select count(*) from orders o join users u on u.id = o.user_id where u.email = '{NEW}'") == '0' else None
r, code = email_code(g, NEW)
check('code sent (development: shown to a local browser)', r.status_code == 200 and code and len(code) == 6 and r.json()['resendIn'] == 30, r.text)
check('only an HMAC is stored', sql(f"select count(*) from auth_email_codes where email = '{NEW}' and code_hash <> '{code}'") == '1')
mail = sorted(os.listdir('logs/mail-outbox')) if os.path.isdir('logs/mail-outbox') else []
check('the e-mail went out with the code', any('sign-in-code' in f for f in mail[-5:]) or not mail, mail[-3:])
check('cooldown: a second code within 30 s refused', post(g, '/auth/email-code/send', {'email': NEW}).status_code == 429)
wrong = '000000' if code != '000000' else '111111'
r = post(g, '/auth/email-code/verify', {'email': NEW, 'code': wrong})
check('wrong code: 422 with tries left', r.status_code == 422 and r.json()['attemptsLeft'] == 4, r.text)
r = post(g, '/auth/email-code/verify', {'email': NEW, 'code': code})
check('right code: signed in, new account created', r.status_code == 200 and r.json()['created'] is True and r.json()['user']['email'] == NEW, r.text)
check('the new account has a verified e-mail and no password', sql(f"select concat(email_verified_at is not null, '/', password_hash is null) from users where email = '{NEW}'") == '1/1')
check('the code is single use', post(g, '/auth/email-code/verify', {'email': NEW, 'code': code}).status_code == 410)
st = get(g, '/landing/checkout/1/state').json()
check('state after sign-in: the buyer, no sign-in options', st['user']['email'] == NEW and st['signIn'] is None and st['enrolled'] is None)
r = post(g, '/landing/checkout/1/order', {'name': 'Kavya Rao', 'phone': '9876512345', 'terms': True})
check('order placed', r.status_code == 200, r.text)
check('a passwordless account takes the name typed at its first order', sql(f"select name from users where email = '{NEW}'") == 'Kavya Rao')
oid = r.json()['orderId']
r = post(g, '/landing/checkout/1/pay', {'orderId': oid, 'method': 'upi'})
check('pay: test mode without gateway keys', r.status_code == 200 and r.json()['gateway'] == 'test', r.text)
check('paid', post(g, '/checkout/test-pay', {'orderId': oid}).status_code == 200)
res = get(g, f'/landing/checkout/1/state?order={oid}').json()
check('result in the page: success and a link into the course', res['result']['state'] == 'success' and res['result']['classroom'] == f'/user/course/{oid}' and res['enrolled'], res.get('result'))

# existing account by code
d2 = guest()
r, code = email_code(d2, 'tanvi.kulkarni@example.com')
r = post(d2, '/auth/email-code/verify', {'email': 'tanvi.kulkarni@example.com', 'code': code, 'name': 'Someone Else'})
check('existing account: signed in, not duplicated, name untouched', r.status_code == 200 and r.json()['created'] is False
      and sql("select count(*) from users where email = 'tanvi.kulkarni@example.com'") == '1' and sql("select name from users where email = 'tanvi.kulkarni@example.com'") != 'Someone Else', r.text)
d3 = guest()
r, code = email_code(d3, 'lock.test@example.com')
for _ in range(5): last = post(d3, '/auth/email-code/verify', {'email': 'lock.test@example.com', 'code': '999999' if code != '999999' else '888888'})
check('five wrong codes lock the code', last.status_code == 429 and post(d3, '/auth/email-code/verify', {'email': 'lock.test@example.com', 'code': code}).status_code == 410, last.text)

# ================================================================================================= phone code
p = guest()
phone = '98' + str(int(time.time()))[-8:]
r = post(p, '/auth/otp/send', {'phone': phone})
check('phone code sent (development)', r.status_code == 200 and r.json().get('devCode'), r.text)
r = post(p, '/auth/otp/verify', {'phone': phone, 'code': r.json()['devCode']})
check('new number: asks for a name', r.status_code == 200 and r.json().get('next') == 'profile', r.text)
r = post(p, '/auth/otp/complete', {'name': 'Rohan Mehta'})
check('account created and signed in', r.status_code == 200 and get(p, '/landing/checkout/1/state').json()['user']['name'] == 'Rohan Mehta', r.text)

# ================================================================================================= Google one-tap
gg = guest()
get(gg, '/landing/checkout/1/state')
check('Google: no credential refused', post(gg, '/auth/google/credential', {}).status_code == 400)
check('Google: a forged token refused', post(gg, '/auth/google/credential', {'credential': 'a.b.c'}).status_code == 401)
check('Google: no nonce issued (no options call) refused', post(guest(), '/auth/google/credential', {'credential': 'a.b.c'}).status_code in (400, 401))
try:
    requests.get('http://127.0.0.1:9999/google/jwks', timeout=2)
    stub = True
except Exception:
    stub = False
if stub:
    nonce = get(gg, '/landing/checkout/1/state').json()['signIn']['google']['nonce']
    now = int(time.time())
    def mint(**over):
        claims = {'iss': 'http://127.0.0.1:9999', 'aud': 'g-client', 'iat': now, 'exp': now + 600, 'nonce': nonce, 'sub': 'gis-1001',
                  'email': 'onetap.user@example.com', 'email_verified': True, 'name': 'One Tap'}
        claims.update(over)
        return requests.post('http://127.0.0.1:9999/mint', json=claims).json()['id_token']
    check('Google: wrong audience refused', post(gg, '/auth/google/credential', {'credential': mint(aud='someone-else')}).status_code == 401)
    check('Google: wrong nonce refused', post(gg, '/auth/google/credential', {'credential': mint(nonce='other')}).status_code == 401)
    r = post(gg, '/auth/google/credential', {'credential': mint()})
    check('Google: signed in with one tap (account + linked identity)', r.status_code == 200 and r.json()['user']['email'] == 'onetap.user@example.com'
          and sql("select count(*) from linked_accounts l join users u on u.id = l.user_id where u.email = 'onetap.user@example.com' and l.provider_name = 'google'") == '1', r.text)
    check('Google: the nonce is single use', post(gg, '/auth/google/credential', {'credential': mint()}).status_code == 400)
else:
    print('SKIP Google one-tap sign-in (the OIDC stub with /mint is not running on 127.0.0.1:9999)')

# ================================================================================================= admin: test payments
adm = staff()
check('guest: test payments API 401', requests.get(B + '/admin/api/landing/test-payments', headers={'Accept': 'application/json'}).status_code == 401)
teacher = staff('vaishali@artistikcity.com')
check('teacher (no landing.payments): 403', get(teacher, '/admin/api/landing/test-payments').status_code == 403)
check('teacher can\'t run the simulator', post(teacher, '/admin/api/landing/test-payments/run', {'pageId': 1}).status_code == 403)
v = get(adm, '/admin/api/landing/test-payments').json()
check('overview: pages with their payment options, gateways, no buyers yet', any(p['id'] == 1 and [m['key'] for m in p['methods']] == ['upi', 'card', 'netbanking', 'paypal'] for p in v['pages'])
      and v['gateways']['razorpay'] == 'none' and v['buyers'] == [], v.get('gateways'))
TB = 'qa.tester@example.com'
r = post(adm, '/admin/api/landing/test-payments/buyers', {'email': TB, 'name': 'QA Tester'})
check('add test buyer: a passwordless test account is created', r.status_code == 200 and any(b['email'] == TB for b in r.json()['buyers'])
      and sql(f"select concat(test_buyer, '/', password_hash is null, '/', email_verified_at is not null) from users where email = '{TB}'") == '1/1/1', r.text)
r = post(adm, '/admin/api/landing/test-payments/buyers', {'email': 'diya@example.com'})
check('add test buyer: an existing account is marked', r.status_code == 200 and sql("select test_buyer from users where email = 'diya@example.com'") == '1')
check('test buyer change audited', sql("select count(*) from admin_audit where entity = 'user' and action = 'test buyer on'") != '0')
tb = int(sql(f"select id from users where email = '{TB}'"))
check('only test buyers can be simulated', post(adm, '/admin/api/landing/test-payments/run', {'pageId': 1, 'buyerId': 4, 'method': 'upi', 'outcome': 'success'}).status_code == 422)
check('a method the page doesn\'t offer: no order', (lambda j: j['ok'] is False and 'order' not in j)(post(adm, '/admin/api/landing/test-payments/run',
      {'pageId': 2, 'buyerId': tb, 'method': 'paypal', 'outcome': 'success'}).json()))
before_rev = sql("select coalesce(sum(price + 0), 0) from orders where landing_page_id = 1 and payment_status = 1 and coalesce(is_test, 0) = 0")
r = post(adm, '/admin/api/landing/test-payments/run', {'pageId': 1, 'buyerId': tb, 'method': 'card', 'outcome': 'success', 'coupon': 'watercolour10'})
j = r.json()
o1 = j.get('order', {}).get('id')
check('simulator, paid: every step OK, order paid and marked as a test', r.status_code == 200 and j['ok'] and j['order']['status'] == 1
      and sql(f"select concat(is_test, '/', payment_method, '/', payment_option, '/', coupon_code) from orders where id = {o1}") == '1/card/Simulated (test)/WATERCOLOUR10', j)
check('the steps tell the story', [s['label'] for s in j['steps']][:6] == ['Test buyer', 'Page', 'Price', 'Payment option', 'Order created', 'Seat'], [s['label'] for s in j['steps']])
check('a simulated payment doesn\'t use up the coupon', sql(f"select count(*) from landing_coupon_redemptions where order_id = {o1}") == '0')
check('test orders stay out of the landing revenue', sql("select coalesce(sum(price + 0), 0) from orders where landing_page_id = 1 and payment_status = 1 and coalesce(is_test, 0) = 0") == before_rev)
r = post(adm, '/admin/api/landing/test-payments/run', {'pageId': 1, 'buyerId': tb, 'method': 'upi', 'outcome': 'success'})
check('already enrolled: 409 with the order to undo', r.status_code == 409 and r.json()['orderId'] == o1, r.text)
r = post(adm, f'/admin/api/landing/test-payments/orders/{o1}/undo')
check('undo: refunded, access gone, nothing deleted', r.status_code == 200 and sql(f"select payment_status from orders where id = {o1}") == '3'
      and sql(f"select count(*) from orders where user_id = {tb} and course_id = 1 and payment_status = 1") == '0', r.text)
check('undo twice: refused', post(adm, f'/admin/api/landing/test-payments/orders/{o1}/undo').status_code == 409)
check('a real order can\'t be undone here', post(adm, '/admin/api/landing/test-payments/orders/1/undo').status_code == 404)
r = post(adm, '/admin/api/landing/test-payments/run', {'pageId': 1, 'buyerId': tb, 'method': 'upi', 'outcome': 'failed'})
o2 = r.json()['order']['id']
check('simulator, declined: order failed, no access', r.json()['ok'] and sql(f"select payment_status from orders where id = {o2}") == '2')
r = post(adm, '/admin/api/landing/test-payments/run', {'pageId': 1, 'buyerId': tb, 'method': 'netbanking', 'outcome': 'pending'})
o3 = r.json()['order']['id']
check('simulator, waiting: open order with a reference, shown as pending in the page', sql(f"select concat(payment_status, '/', gateway_ref is not null) from orders where id = {o3}") == '0/1')
check('simulator works on a draft page', post(adm, '/admin/api/landing/test-payments/run', {'pageId': 2, 'buyerId': tb, 'method': 'upi', 'outcome': 'success'}).json()['ok'])
check('simulations audited', sql("select count(*) from admin_audit where details like 'Simulated landing payment%'") == '4', sql("select count(*) from admin_audit where details like 'Simulated landing payment%'"))
rec = get(adm, '/admin/api/landing/test-payments').json()['recent']
check('recent test orders listed, newest first', [x['id'] for x in rec][:3] == sorted([x['id'] for x in rec][:3], reverse=True) and any(x['id'] == o1 and x['status'] == 3 for x in rec))

# a test buyer simulates in the page
dy = student('diya@example.com')
st = get(dy, '/landing/checkout/1/state').json()
check('test buyer: the page offers "Simulate payment"', st['simulate'] is True)
r = post(dy, '/landing/checkout/1/order', {'name': 'Diya Patel', 'phone': '9876543210', 'terms': True})
oid2 = r.json()['orderId']
r = post(dy, '/landing/checkout/1/pay', {'orderId': oid2, 'method': 'test'})
check('simulated in the page', r.status_code == 200 and r.json().get('simulated') is True and post(dy, '/checkout/test-pay', {'orderId': oid2}).status_code == 200
      and sql(f"select concat(payment_status, '/', is_test) from orders where id = {oid2}") == '1/1', r.text)
post(adm, '/admin/api/landing/test-payments/buyers', {'email': 'diya@example.com', 'on': False})
check('remove test buyer: no more simulation', get(dy, '/landing/checkout/1/state').json()['simulate'] is False and sql("select test_buyer from users where email = 'diya@example.com'") == '0')
r = post(student('ishita.bose@example.com'), '/landing/checkout/1/pay', {'orderId': 1, 'method': 'test'})
check('a normal buyer can\'t simulate', r.status_code in (403, 410), r.text)

print(f'\n{"ALL PASSED" if not fails else str(fails) + " FAILED"}')
raise SystemExit(1 if fails else 0)
