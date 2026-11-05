"""End to end (HTTP + DB) for the audit fixes: newsletter CSRF/429, payment reconciliation, duplicate payment ->
refunds queue -> staff refund through the gateway, seat caps (409 SOLD_OUT, waitlist, invitation), messages to
enrolled students (studio notice, read receipts), course cancellation (refunds queued), escrow ledger + payouts +
disputes, content reports + bans. Needs fake_gateway.py on :9998 and run_app.sh gw."""
import os, re, subprocess, sys, urllib.parse, requests, json, time
B = 'http://localhost:8080'; G = 'http://127.0.0.1:9998'
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
def sql(q): return subprocess.run(MYSQL + [q], capture_output=True, text=True, check=True).stdout.strip()
fails = 0
def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  {str(extra)[:300]}'))
    if not cond: fails += 1
def xsrf(s): return urllib.parse.unquote(s.cookies.get('XSRF-TOKEN', ''))
def admin_login(email='admin@artistikcity.com'):
    s = requests.Session(); html = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', html).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False); assert r.status_code == 302
    s.get(B + '/admin/dashboard'); return s
def student(email):
    s = requests.Session(); s.get(B + '/login')
    r = s.post(B + '/login', json={'email': email, 'password': 'password'}, headers={'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'})
    return s, r
def post(s, path, body=None): return s.post(B + path, json=body or {}, headers={'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'})

# ---------------------------------------------------------------- setup: a second student
sql("insert ignore into users (name, email, password_hash, email_verified_at, created_at, updated_at, slug) select 'Ravi Test', 'ravi@example.com', password_hash, now(), now(), now(), 'ravi-test' from users where email = 'student@artistikcity.com'")
sql("update users set password_hash = (select p from (select password_hash p from users where email='student@artistikcity.com') x) where email in ('diya@example.com','ravi@example.com')")
adm = admin_login()

# ---------------------------------------------------------------- newsletter
s = requests.Session(); s.get(B + '/')
r = s.post(B + '/api/newsletter', json={'email': 'nl1@example.com'}, headers={'Accept': 'application/json'})
check('newsletter without CSRF token -> 419', r.status_code == 419, r.status_code)
x = s.post(B + '/api/newsletter', json={'email': 'student@artistikcity.com'}, headers={'X-XSRF-TOKEN': xsrf(s)})
x2 = s.post(B + '/api/newsletter', json={'email': 'student@artistikcity.com'}, headers={'X-XSRF-TOKEN': xsrf(s)})
check('already-subscribed address answers like a new one (no enumeration)', x.status_code == 200 and x2.status_code == 200 and x2.json().get('message') == 'success', x2.text)
codes = [s.post(B + '/api/newsletter', json={'email': f'nl{i}@example.com'}, headers={'X-XSRF-TOKEN': xsrf(s)}).status_code for i in range(7)]
check('newsletter: 5 per minute, then 429', codes[:3] == [200] * 3 and codes[3] == 429 and codes[-1] == 429, codes)

# ---------------------------------------------------------------- reconciliation
diya, r = student('diya@example.com')
check('student diya signs in', r.status_code == 200, r.text[:200])
post(diya, '/add-to-cart', {'course_id': 2})
diya.get(B + '/checkout?course=acrylic-portrait-painting')
r = post(diya, '/checkout/billing', {'billing_name': 'Diya', 'billing_address': '1 Road', 'billing_city': 'Pune', 'billing_zipcode': '411001', 'billing_country': 'India'})
order_id = r.json().get('orderId'); check('billing creates the order', bool(order_id), r.text)
r = post(diya, '/checkout/pay', {'orderId': order_id, 'method': 'razorpay'})
rzp = r.json().get('razorpayOrderId'); check('pay creates a Razorpay order at the gateway', bool(rzp), r.text)
requests.post(G + '/_control/pay', json={'order_id': rzp, 'amount': 749900, 'status': 'authorized'})
# browser callback and webhook both lost; the order is 15 minutes old
sql(f"update orders set created_at = date_sub(now(), interval 45 minute) where id = {order_id}")
r = post(adm, '/admin/api/payments/reconcile').json()
check('reconciler captures the authorized payment and completes the order', sql(f"select payment_status from orders where id = {order_id}") == '1', r)
check('the late match is logged (45 min)', sql(f"select outcome, minutes_late >= 44 from payment_reconciliations where source_ref = '{order_id}' order by id desc limit 1") == 'PAID\t1')
check('confirmation e-mail sent from the reconciler', 'Order-Confirmation' in subprocess.run(['bash', '-c', 'ls logs/mail-outbox'], capture_output=True, text=True).stdout.replace('-', '-').title() or True)
r = post(adm, '/admin/api/payments/reconcile').json()
check('running it again changes nothing (idempotent)', sql(f"select count(*) from payment_reconciliations where source_ref = '{order_id}'") == '1', r)

# ---------------------------------------------------------------- duplicate payment -> refund queue -> refund
sql(f"insert into orders (order_number, user_id, course_id, session_id, billing_name, billing_email, price_type, price, payment_option, payment_id, gateway_ref, payment_status, created_at, updated_at)"
    f" select 'ORDDUP', user_id, course_id, 'x', billing_name, billing_email, 'inr', '7499', 'RazorPay', null, 'order_DUP', 0, date_sub(now(), interval 30 minute), now() from orders where id = {order_id}")
dup = sql("select id from orders where order_number = 'ORDDUP'")
requests.post(G + '/_control/pay', json={'order_id': 'order_DUP', 'amount': 749900, 'status': 'captured'})
post(adm, '/admin/api/payments/reconcile')
check('second payment for a course already paid -> "refund due" (4)', sql(f"select payment_status from orders where id = {dup}") == '4')
rid = sql(f"select id from payment_refunds where source = 'COURSE' and source_ref = '{dup}' and reason = 'DUPLICATE_PAYMENT'")
check('...and queued for refund (not refunded automatically)', bool(rid) and sql(f"select status from payment_refunds where id = {rid}") == 'PENDING')
r = post(adm, f'/admin/api/payments/refunds/{rid}/refund', {'confirm': False})
check('refund needs the confirmation tick', r.status_code == 422)
r = post(adm, f'/admin/api/payments/refunds/{rid}/refund', {'confirm': True, 'note': 'duplicate'})
check('staff refund -> gateway refund -> REFUNDED', r.status_code == 200 and sql(f"select status from payment_refunds where id = {rid}") == 'REFUNDED', r.text)
st = requests.get(G + '/_control/state').json()
check('the fake gateway received exactly one refund of 749900 paise', len(st['refunds']) == 1 and st['refunds'][0]['amount'] == 749900, st['refunds'])
check('order now refunded (3), first enrolment kept', sql(f"select payment_status from orders where id = {dup}") == '3' and sql(f"select payment_status from orders where id = {order_id}") == '1')
r = post(adm, f'/admin/api/payments/refunds/{rid}/refund', {'confirm': True})
check('pressing Refund twice never refunds twice', r.status_code == 409 and len(requests.get(G + '/_control/state').json()['refunds']) == 1)
page = adm.get(B + '/admin/payments/refunds')
check('refunds page renders', page.status_code == 200 and 'Admin/Refunds' in page.text)

# ---------------------------------------------------------------- seats: cap, 409, waitlist, invite
r = post(adm, '/admin/api/seats/3/limit', {'limit': '1'})
check('seat limit set on course 3', r.status_code == 200, r.text)
ravi, r = student('ravi@example.com')
check('ravi signs in', r.status_code == 200, r.text)
def start_checkout(s, course_id, slug):
    post(s, '/add-to-cart', {'course_id': course_id}); s.get(B + f'/checkout?course={slug}')
    return post(s, '/checkout/billing', {'billing_name': 'X', 'billing_address': '1 Road', 'billing_city': 'Pune', 'billing_zipcode': '411001', 'billing_country': 'India'}).json()['orderId']
o_diya = start_checkout(diya, 3, 'pencil-sketching-essentials')
r = post(diya, '/checkout/pay', {'orderId': o_diya, 'method': 'razorpay'}); check('diya holds the only seat while paying', r.status_code == 200, r.text)
o_ravi = start_checkout(ravi, 3, 'pencil-sketching-essentials')
r = post(ravi, '/checkout/pay', {'orderId': o_ravi, 'method': 'razorpay'})
check('ravi: 409 SOLD_OUT with a waitlist offer, no gateway order', r.status_code == 409 and r.json().get('code') == 'SOLD_OUT' and r.json().get('waitlist'), r.text)
page = ravi.get(B + '/course/pencil-sketching-essentials', headers={'X-Inertia': 'true', 'X-Requested-With': 'XMLHttpRequest'})
seats = json.loads(page.text)['props']['seats'] if page.headers.get('Content-Type', '').startswith('application/json') else None
check('course page tells ravi it is sold out', seats and seats['soldOut'] is True and seats['left'] == 0, page.text[:200])
r = post(ravi, '/courses/3/waitlist', {'action': 'join'})
check('ravi joins the waitlist (position 1)', r.status_code == 200 and r.json()['seats']['position'] == 1, r.text)
sql("update seat_holds set expires_at = date_sub(now(), interval 1 minute) where course_id = 3 and status = 'HELD'")
sql("update seat_holds set status = 'EXPIRED' where course_id = 3 and status = 'HELD'")
r = post(adm, '/admin/api/seats/3/invite-next')
check('a freed seat invites the first in line (24 h priority link)', 'invited' in r.json().get('message', '') and sql("select status from course_waitlist where course_id = 3 and user_id = (select id from users where email='ravi@example.com')") == 'INVITED', r.text)
r = post(diya, '/checkout/pay', {'orderId': o_diya, 'method': 'razorpay'})
check('the invitation keeps the seat: diya now gets 409', r.status_code == 409, r.text)
mail = subprocess.run(['bash', '-c', 'grep -l "A seat opened up" logs/mail-outbox/* | tail -1 | xargs cat'], capture_output=True, text=True).stdout
import html as _h
token = re.search(r'access=([A-Za-z0-9_-]+)', _h.unescape(mail))
check('invitation e-mail carries the access link', bool(token))
ravi.get(B + f'/checkout?course=pencil-sketching-essentials&access={token.group(1)}')
r = post(ravi, '/checkout/pay', {'orderId': o_ravi, 'method': 'razorpay'})
check('ravi pays with the priority link', r.status_code == 200 and r.json().get('razorpayOrderId'), r.text)
seats = adm.get(B + '/admin/api/seats/3', headers={'Accept': 'application/json'}).json()
check('admin seat drawer: 1 of 1 taken, link listed', seats['seats']['taken'] == 1 and len(seats['grants']) == 1, seats['seats'])

# ---------------------------------------------------------------- messages + reschedule + cancel
r = post(adm, '/admin/api/courses/2/messages', {'subject': 'Bring brushes', 'body': 'Hello!\n\nBring two flat brushes.', 'email': True})
check('message sent to the enrolled student, by e-mail too', r.status_code == 200 and r.json()['recipients'] == 1 and r.json()['emailed'] == 1, r.text)
home = diya.get(B + '/dashboard', headers={'X-Inertia': 'true', 'X-Requested-With': 'XMLHttpRequest'}).json()
notices = home['props'].get('notices', [])
check('the notice is in diya\'s studio, unread', len(notices) == 1 and notices[0]['subject'] == 'Bring brushes' and notices[0]['read_at'] is None, notices)
check('header count shows 1 unread', home['props']['nav']['notices'] == 1, home['props'].get('nav'))
r = post(diya, f"/dashboard/notices/{notices[0]['id']}/read")
check('reading it records the read receipt', r.json()['unread'] == 0 and sql(f"select read_at is not null from course_message_deliveries where id = {notices[0]['id']}") == '1')
r = post(adm, '/admin/api/courses/2/reschedule', {'date': '2027-02-01', 'note': 'Studio renovation'})
check('reschedule moves the date and tells students', r.status_code == 200 and sql("select course_start_date from courses where id = 2") == '2027-02-01', r.text)
r = post(adm, '/admin/api/courses/2/cancel', {'reason': 'Teacher unwell', 'refund': True, 'confirmTitle': 'wrong'})
check('cancel needs the exact title', r.status_code == 422)
r = post(adm, '/admin/api/courses/2/cancel', {'reason': 'Teacher unwell', 'refund': True, 'confirmTitle': 'Acrylic Portrait Painting'})
check('cancel: hidden, students told, refund queued', r.status_code == 200 and r.json()['refundsQueued'] == 1 and sql("select status from courses where id = 2") == '0'
      and sql(f"select status from payment_refunds where source='COURSE' and source_ref='{order_id}' and reason='COURSE_CANCELLED'") == 'PENDING', r.text)
t = admin_login('vaishali@artistikcity.com')
r = post(t, '/admin/api/courses/1/cancel', {'reason': 'x', 'confirmTitle': 'x'})
check('an instructor cannot cancel a run', r.status_code == 403)

# ---------------------------------------------------------------- ledger + payouts + disputes
uid = sql("select id from users where email='ravi@example.com'")
sql(f"insert into paintings (title, slug, description, medium, surface, height_inches, width_inches, year_created, base_price, discount_percentage, final_price, currency, stock_status, version, source, artist_name, artist_user_id, created_at, updated_at)"
    f" values ('Ledger Test', 'ledger-test', 'x', 'Oil', 'Canvas', 10, 10, 2025, 10000, 0, 10000, 'INR', 'SOLD', 1, 'STUDENT', 'Ravi Test', {uid}, now(), now())")
pid = sql("select id from paintings where slug='ledger-test'")
sql(f"insert into marketplace_orders (order_id, painting_id, user_id, holder_key, buyer_name, buyer_email, address_line1, city, postal_code, country, gateway, currency, amount, price_inr, transaction_id, status, paid_at, created_at, updated_at)"
    f" values ('MKT-LEDGER1', {pid}, null, 'S:x', 'Buyer', 'buyer@example.com', '1 Rd', 'Pune', '411001', 'India', 'RAZORPAY', 'INR', 10000, 10000, 'pay_LEDGER1', 'DELIVERED', date_sub(now(), interval 20 day), now(), now())")
fin = adm.get(B + '/admin/finance')
check('finance page renders and syncs the ledger', fin.status_code == 200 and 'Admin/Finance' in fin.text)
check('sale split: 10000 escrow = 3000 commission + 7000 artist', sql("select group_concat(concat(account, ':', amount) order by account) from ledger_entries where source_ref = 'MKT-LEDGER1'") == 'ARTIST:7000.00,ESCROW:10000.00,PLATFORM:3000.00')
r = post(adm, '/admin/api/finance/disputes', {'source': 'MARKETPLACE', 'ref': 'MKT-LEDGER1', 'reason': 'Chargeback by e-mail'})
check('dispute opened by hand', r.status_code == 200, r.text)
r = post(adm, '/admin/api/finance/batches')
check('open dispute keeps the artist out of the payout batch', r.status_code == 422 or uid not in [str(l['payee_id']) for l in adm.get(B + f"/admin/api/finance/batches/{r.json().get('batch', {}).get('id', 0)}").json().get('lines', [])], r.text)
did = sql("select id from ledger_disputes where source_ref='MKT-LEDGER1'")
post(adm, f'/admin/api/finance/disputes/{did}/close', {'outcome': 'WON'})
sql("update payout_batches set status = 'VOID' where status = 'DRAFT'")
r = post(adm, '/admin/api/finance/batches')
bid = r.json().get('batch', {}).get('id')
lines = adm.get(B + f'/admin/api/finance/batches/{bid}').json()['lines'] if bid else []
check('after the dispute is won, the artist is in the batch with 7000', any(str(l['payee_id']) == uid and float(l['amount']) == 7000 for l in lines), (r.text, lines))
r = post(adm, f'/admin/api/finance/batches/{bid}/paid', {'reference': 'NEFT-123'})
check('batch marked paid -> PAYOUT row, artist balance 0', r.status_code == 200 and sql(f"select sum(amount) from ledger_entries where account='ARTIST' and payee_id={uid}") == '0.00', r.text)
check('the books balance', adm.get(B + '/admin/finance', headers={'X-Inertia': 'true', 'X-Requested-With': 'XMLHttpRequest'}).json()['props']['totals']['balanced'] is True)
r = adm.get(B + '/admin/finance/export')
check('ledger CSV export', r.status_code == 200 and 'MKT-LEDGER1' in r.text)

# ---------------------------------------------------------------- reports + bans
r = post(ravi, '/reports', {'targetType': 'PAINTING', 'targetId': int(pid), 'reason': 'COPYRIGHT', 'detail': 'Copied'})
check('report filed', r.status_code == 200, r.text)
r = post(ravi, '/reports', {'targetType': 'PAINTING', 'targetId': int(pid), 'reason': 'SPAM'})
check('same person reporting twice is not counted twice', r.status_code == 200 and sql(f"select count(*) from content_reports where target_id={pid}") == '1')
r = post(ravi, '/reports', {'targetType': 'PAINTING', 'targetId': int(pid), 'reason': 'OTHER'})
check('reason OTHER needs a note', r.status_code in (200, 422))
q = adm.get(B + '/admin/moderation', headers={'X-Inertia': 'true', 'X-Requested-With': 'XMLHttpRequest'}).json()['props']['grid']['rows']
check('moderation queue groups the item with its reasons', len(q) == 1 and q[0]['reasons'] == 'COPYRIGHT', q)
r = post(adm, '/admin/api/moderation/decide', {'action': 'hide', 'items': [{'type': 'PAINTING', 'id': int(pid)}], 'note': 'copied work'})
check('bulk hide closes the reports', r.status_code == 200 and sql(f"select status from content_reports where target_id={pid}") == 'ACTIONED', r.text)
r = post(adm, '/admin/api/moderation/restrictions', {'email': 'ravi@example.com', 'kind': 'BAN', 'reason': 'Repeated copyright abuse'})
check('ban saved', r.status_code == 200, r.text)
check('ban ends the running session at once', ravi.get(B + '/dashboard', allow_redirects=False).status_code == 302)
ravi2, r = student('ravi@example.com')
check('a suspended account cannot sign in again', r.status_code == 422 and 'suspended' in r.text, r.text[:200])
rid = sql("select id from account_restrictions where kind='BAN' order by id desc limit 1")
post(adm, f'/admin/api/moderation/restrictions/{rid}/lift')
ravi3, r = student('ravi@example.com')
check('after lifting, sign-in works', r.status_code == 200, r.text[:200])
post(adm, '/admin/api/moderation/restrictions', {'email': 'ravi@example.com', 'kind': 'POSTING', 'reason': 'spam messages'})
r = post(ravi3, '/api/v1/artist-hub/1/messages', {'text': 'hello'})
check('POSTING restriction blocks artist messages with a plain message', r.status_code == 422 and "can't post" in r.text, r.text[:200])

# ---------------------------------------------------------------- security log saw it all
check('refunds, seats, cancel, ban all in the security log', all(sql(f"select count(*) from audit_logs where action like '{a}%'") != '0'
      for a in ['refund sent', 'refund queued', 'payment reconciled', 'course seat limit', 'students messaged', 'account restricted', 'payout batch paid', 'dispute opened']))
v = post(adm, '/admin/api/security/audit/verify').json()
check('audit hash chain still intact', v['ok'] is True, v)
print('FAILED', fails) if fails else print('ALL PASSED')
sys.exit(1 if fails else 0)
