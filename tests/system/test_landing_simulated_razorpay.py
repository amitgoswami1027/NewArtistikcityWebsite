"""Simulated Razorpay for landing pages (no Razorpay account or keys). Run against a server WITHOUT test mode and WITHOUT
Razorpay keys, like a fresh live site:   java -jar target/artistikcity-1.0.0.jar   (no dev profile, no PAYMENT_TEST_MODE)
on a fresh database (schema.sql + seed.sql), then   python3 tests/system/test_landing_simulated_razorpay.py

Covers: without keys nobody can pay; Admin › Payments & checkout › Test payments › "Simulate Razorpay" makes Razorpay's
methods available; a new page named like an existing one gets its own address; it can be published; a test buyer pays in
the simulated Razorpay window (success and failure) and the order is a test order; everybody else is told payment opens
soon and can't pay or simulate; the admin simulator; switching it off closes it again; permissions and audit;
sign-in at checkout without any setup (e-mail and password), the admin's sign-in switches and what each method needs.
"""
import os, re, subprocess, urllib.parse
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
def put(s, path, body=None): return s.put(B + path, json=body or {}, headers=H(s))
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

adm = staff()
st = get(requests.Session(), '/landing/checkout/1/state').json()
if st.get('testMode') or st.get('razorpaySimulated'):
    print('This test needs a server without test mode (no dev profile / PAYMENT_TEST_MODE) and with the switch off.')
check('without Razorpay keys nobody can pay', st['choices'] == [] and st['testMode'] is False, st.get('choices'))
check('the simulator has no payment option either', [m for p in get(adm, '/admin/api/landing/test-payments').json()['pages'] if p['id'] == 1 for m in p['methods']] == [])

teacher = staff('vaishali@artistikcity.com')
check('teacher can\'t switch the simulation on', post(teacher, '/admin/api/landing/test-payments/simulate-razorpay', {'on': True}).status_code == 403)
r = post(adm, '/admin/api/landing/test-payments/simulate-razorpay', {'on': True})
check('switched on', r.status_code == 200 and r.json()['razorpaySimulated'] is True and r.json()['gateways']['razorpay'] == 'simulated', r.text)
check('audited', sql("select count(*) from admin_audit where details = 'Simulated Razorpay switched on'") == '1')
st = get(requests.Session(), '/landing/checkout/1/state').json()
check('Razorpay\'s methods offered, PayPal still off', [c['key'] for c in st['choices']] == ['upi', 'card', 'netbanking'] and st['razorpaySimulated'] is True, [c['key'] for c in st['choices']])
check('a guest is told payment opens soon', st['paymentsClosed'] is True)
pay = next(p for p in get(adm, '/admin/api/landing/payments').json()['pages'] if p['id'] == 1)
check('page payment overview shows the methods', 'UPI' in pay['choices'])

# a new page with the same name as an existing one
r = post(adm, '/admin/api/landing/pages', {'name': 'Watercolour Landscapes for Beginners', 'courseId': 1, 'template': 'watercolour-wash'})
check('new page: its own address (a number is added)', r.status_code == 200 and r.json().get('slug') == 'watercolour-landscapes-for-beginners-2', r.text)
P = r.json()['id']; SLUG = r.json()['slug']
r = post(adm, f'/admin/api/landing/pages/{P}/publish', {'note': 'Launch for testing'})
check('it can be published (Razorpay counts as connected)', r.status_code == 200 and sql(f"select status from landing_pages where id = {P}") == 'PUBLISHED', r.text)
check('its public address works', requests.get(B + '/landing/' + SLUG).status_code == 200)

# a normal buyer
ishita = student('ishita.bose@example.com')
check('normal buyer: payment opens soon', get(ishita, f'/landing/checkout/{P}/state').json()['paymentsClosed'] is True)
o = post(ishita, f'/landing/checkout/{P}/order', {'name': 'Ishita Bose', 'phone': '9876500001', 'terms': True}).json()['orderId']
r = post(ishita, f'/landing/checkout/{P}/pay', {'orderId': o, 'method': 'upi'})
check('normal buyer: paying refused with a friendly message, nothing simulated', r.status_code == 503 and 'opens soon' in r.json()['message']
      and sql(f"select concat(payment_status, '/', coalesce(is_test, 0)) from orders where id = {o}") == '0/0', r.text)
check('normal buyer: the simulated window can\'t be used', post(ishita, f'/landing/checkout/{P}/simulate', {'orderId': o, 'outcome': 'success'}).status_code == 403)

# the test buyer
post(adm, '/admin/api/landing/test-payments/buyers', {'email': 'diya@example.com'})
diya = student('diya@example.com')
st = get(diya, f'/landing/checkout/{P}/state').json()
check('test buyer: payment open', st['paymentsClosed'] is False and st['razorpaySimulated'] is True)
o1 = post(diya, f'/landing/checkout/{P}/order', {'name': 'Diya Patel', 'phone': '9876543210', 'terms': True}).json()['orderId']
r = post(diya, f'/landing/checkout/{P}/pay', {'orderId': o1, 'method': 'card'})
j = r.json()
check('test buyer: the simulated Razorpay window opens with the order', r.status_code == 200 and j['gateway'] == 'razorpay-sim' and j['method'] == 'card'
      and j['razorpayOrderId'] == f'order_SIM{o1}' and j['amountLabel'], j)
r = post(diya, f'/landing/checkout/{P}/simulate', {'orderId': o1, 'outcome': 'failed'})
check('failure: order failed, no access', r.status_code == 200 and sql(f"select payment_status from orders where id = {o1}") == '2')
o1 = post(diya, f'/landing/checkout/{P}/order', {'name': 'Diya Patel', 'phone': '9876543210', 'terms': True}).json()['orderId']
post(diya, f'/landing/checkout/{P}/pay', {'orderId': o1, 'method': 'upi'})
r = post(diya, f'/landing/checkout/{P}/simulate', {'orderId': o1, 'outcome': 'success'})
check('success: paid through the normal completion, marked as a test', r.status_code == 200
      and sql(f"select concat(payment_status, '/', is_test, '/', payment_provider, '/', payment_method, '/', payment_option, '/', payment_id like 'pay_SIM%') from orders where id = {o1}")
      == '1/1/razorpay/upi/Simulated (test)/1', sql(f"select concat(payment_status, '/', is_test, '/', payment_provider, '/', payment_method, '/', payment_option) from orders where id = {o1}"))
res = get(diya, f'/landing/checkout/{P}/state?order={o1}').json()
check('result in the page: success, course open', res['result']['state'] == 'success' and res['enrolled'], res.get('result'))
check('the same order can\'t be simulated twice', post(diya, f'/landing/checkout/{P}/simulate', {'orderId': o1, 'outcome': 'success'}).status_code == 410)
check('test orders stay out of the landing revenue', sql(f"select count(*) from orders where landing_page_id = {P} and payment_status = 1 and coalesce(is_test, 0) = 0") == '0')

# the admin simulator, then switching it off
r = post(adm, '/admin/api/landing/test-payments/buyers', {'email': 'sim.tester@example.com'})
tb = int(sql("select id from users where email = 'sim.tester@example.com'"))
r = post(adm, '/admin/api/landing/test-payments/run', {'pageId': P, 'buyerId': tb, 'method': 'netbanking', 'outcome': 'success'})
check('admin simulator: paid on the new page', r.status_code == 200 and r.json()['ok'] and r.json()['order']['status'] == 1, r.text)
r = post(adm, '/admin/api/landing/test-payments/simulate-razorpay', {'on': False})
check('switched off: nobody can pay again', r.json()['razorpaySimulated'] is False and get(diya, f'/landing/checkout/{P}/state').json()['choices'] == [])
check('switched off: the simulated window is closed', post(diya, f'/landing/checkout/{P}/simulate', {'orderId': o1, 'outcome': 'success'}).status_code == 403)

# sign-in at checkout on a server without a mail server, SMS provider or Google client id
g = requests.Session(); g.get(B + '/landing/watercolour-landscapes-for-beginners')
o = get(g, '/landing/checkout/1/state').json()['signIn']
check('no setup: e-mail and password is offered, the code methods are not', o['password'] is True and o['email'] is False and o['phone'] is False and o['google'] is None, o)
check('teacher can\'t read the sign-in settings', get(teacher, '/admin/api/landing/signin').status_code == 403)
v = get(adm, '/admin/api/landing/signin').json()
check('admin: what each method needs', v['readiness']['google']['ready'] is False and 'Configuration Panel' in v['readiness']['google']['detail']
      and v['readiness']['email']['ready'] is False and 'MAIL_HOST' in v['readiness']['email']['detail'] and v['readiness']['password']['ready'] is True
      and v['offered']['password'] is True and v['offered']['email'] is False, v)
check('every method can\'t be switched off', put(adm, '/admin/api/landing/signin', {'google': False, 'email': False, 'phone': False, 'passkey': False, 'password': False}).status_code == 422)
r = put(adm, '/admin/api/landing/signin', {'passkey': False})
check('passkey switched off: no longer offered, audited', r.status_code == 200 and get(g, '/landing/checkout/1/state').json()['signIn']['passkey'] is False
      and sql("select count(*) from admin_audit where details = 'Sign-in at checkout'") == '1', r.text)
r = put(adm, '/admin/api/landing/signin', {'password': False, 'passkey': True})
check('password switched off but nothing else works here: still offered so buyers can sign in', get(g, '/landing/checkout/1/state').json()['signIn']['password'] is True)
put(adm, '/admin/api/landing/signin', {'password': True})
r = g.post(B + '/register', json={'name': 'Riya Sen', 'email': 'riya.sen@example.com', 'password': 'Watercolour#2026', 'password_confirmation': 'Watercolour#2026'},
           headers=H(g), allow_redirects=False)
check('create an account with a password from the checkout', r.status_code in (200, 302, 409) and get(g, '/landing/checkout/1/state').json()['user']['email'] == 'riya.sen@example.com', r.status_code)

print(f'\n{"ALL PASSED" if not fails else str(fails) + " FAILED"}')
raise SystemExit(1 if fails else 0)
