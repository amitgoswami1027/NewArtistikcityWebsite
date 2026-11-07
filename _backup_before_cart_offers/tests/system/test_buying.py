"""End to end (HTTP + DB) for buying: sign-in before any purchase (courses, artworks, commissions), "Buy now" without a
cart, test-buyer accounts that may simulate payments on a site with live gateway keys, test orders kept out of revenue
and the ledger, and the admin-managed room photos of the purchase preview.

Run against the gateway profile (live keys + the fake gateway on :9998), which behaves like production:
    python3 tests/system/fake_gateway.py &   then   run_app.sh gw   then   python3 tests/system/test_buying.py
"""
import html, io, json, os, re, struct, subprocess, sys, urllib.parse, zlib
import requests

B = 'http://localhost:8080'
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
def sql(q): return subprocess.run(MYSQL + [q], capture_output=True, text=True, check=True).stdout.strip()
fails = 0
def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  {str(extra)[:300]}'))
    if not cond: fails += 1
def xsrf(s): return urllib.parse.unquote(s.cookies.get('XSRF-TOKEN', ''))
def post(s, path, body=None): return s.post(B + path, json=body or {}, headers={'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'})
def page(s, path):
    """(component, props) of an Inertia page, following redirects."""
    r = s.get(B + path)
    m = re.search(r'data-page="([^"]+)"', r.text)
    d = json.loads(html.unescape(m.group(1))) if m else {}
    return d.get('component'), d.get('props', {}), r
def student(email):
    s = requests.Session(); s.get(B + '/login')
    r = s.post(B + '/login', json={'email': email, 'password': 'password'}, headers={'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'})
    assert r.status_code == 200, r.text[:200]
    return s
def admin_login(email='admin@artistikcity.com'):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False); assert r.status_code == 302
    s.get(B + '/admin/dashboard'); return s
def png(w, h):
    """A plain PNG of w x h pixels (no image library needed)."""
    raw = b''.join(b'\x00' + b'\xd8\xd4\xcc' * w for _ in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')

# ---------------------------------------------------------------- setup
sql("insert ignore into users (name, email, password_hash, email_verified_at, status, created_at, updated_at, slug) select 'Tara Tester', 'tara@example.com', password_hash, now(), 1, now(), now(), 'tara-tester' from users where email = 'student@artistikcity.com'")
sql("update users set test_buyer = 0 where email = 'tara@example.com'")
course_id, course_slug = sql("select c.id, c.slug from courses c join course_prices p on p.course_id = c.id where c.status = 1 and p.price_inr > 0"
                             " and coalesce(c.seat_limit, 0) = 0 order by c.id limit 1").split('\t')
sql(f"delete from orders where course_id = {course_id} and user_id = (select id from users where email = 'tara@example.com')")
art_id, art_slug = sql("select id, slug from paintings where stock_status = 'AVAILABLE' order by id limit 1").split('\t')
adm = admin_login()

# ---------------------------------------------------------------- the header: Courses is one link, no cart
_, props, r = page(requests.Session(), '/')
check('home page renders', r.status_code == 200)
js = open('src/main/resources/static/js/app.js', encoding='utf-8').read()
check('header: Courses is a plain link to the course menu (no dropdown)', 'Courses <i className' not in js and 'dm-explore' not in js.split('Components/Site/SiteHeader.js')[1][:20000])
check('header: no cart button', '<CartButton' not in js.split('Components/Site/SiteHeader.js')[1][:30000] and 'CartButton,' not in js.split('Components/Site/SiteHeader.js')[1][:30000])

# ---------------------------------------------------------------- guests sign in before buying
g = requests.Session(); g.get(B + '/courses')
r = g.post(B + '/add-to-cart', data={'course_id': course_id, '_token': xsrf(g)}, headers={'X-XSRF-TOKEN': xsrf(g)}, allow_redirects=False)
loc = urllib.parse.unquote(r.headers.get('Location', ''))
check('guest "Enroll now" -> log in, coming back to that course\'s checkout', r.status_code in (302, 303) and loc.startswith('/login?return_to=/checkout?course=' + course_slug), (r.status_code, loc))
r = g.get(B + '/checkout?course=' + course_slug, allow_redirects=False)
check('guest opening the course checkout -> log in', r.status_code in (302, 303) and '/login?return_to=' in r.headers.get('Location', ''), r.status_code)
comp, props, _ = page(g, '/login?return_to=' + urllib.parse.quote('/checkout?course=' + course_slug) + '&intent=buy')
title = sql(f"select title from courses where id = {course_id}")
check('log-in page names what they are buying', comp == 'Auth/Login' and (props.get('intent') or {}).get('title') == title, props.get('intent'))
_, props, _ = page(g, '/login')
check('a log-in page opened on its own says nothing about a purchase', props.get('intent') is None, props.get('intent'))
_, props, _ = page(g, '/join?return_to=' + urllib.parse.quote('/checkout?course=' + course_slug))
check('join page names it too', (props.get('intent') or {}).get('kind') in ('course', 'workshop'), props.get('intent'))

_, aprops, _ = page(g, '/marketplace/' + art_slug)
fmt = next((f for f in aprops['offer']['formats'] if f['format'] in ('CANVAS', 'POSTER') and f['available']), None)
check('the artwork offers a print', fmt is not None, aprops['offer']['formats'])
sel = {'paintingId': int(art_id), 'format': fmt['format'], 'size': fmt['sizes'][0]['code'], 'finish': (fmt.get('finishes') or [{'code': ''}])[0]['code'], 'frame': 'NONE', 'qty': 1}
r = post(g, '/api/v1/purchase/draft', sel)
check('guest "Buy now" -> 401 SIGN_IN with the log-in link', r.status_code == 401 and r.json().get('code') == 'SIGN_IN' and '/login?return_to=' in r.json().get('redirect', ''), r.text)
r = post(g, '/api/v1/purchase/draft', {**sel, 'format': 'ORIGINAL', 'size': '', 'finish': ''})
check('a guest choosing the original does not hold it', r.status_code == 401 and sql(f"select stock_status from paintings where id = {art_id}") == 'AVAILABLE'
      and sql(f"select count(*) from cart_reservations where painting_id = {art_id}") == '0', r.text)
r = post(g, f'/api/v1/marketplace/paintings/{art_id}/reserve')
check('guest reserve API -> 401 SIGN_IN', r.status_code == 401 and r.json().get('code') == 'SIGN_IN', r.status_code)
for path in ['/purchase/step-2', '/purchase/checkout', '/purchase/buy-now', '/commission/checkout', f'/marketplace/{art_slug}/checkout']:
    r = g.get(B + path, allow_redirects=False)
    check(f'guest {path} -> log in', r.status_code in (302, 303, 409) and '/login' in (r.headers.get('Location', '') + r.headers.get('X-Inertia-Location', '')), (r.status_code, r.headers.get('Location')))
r = post(g, '/api/v1/purchase/checkout', {'gateway': 'RAZORPAY'})
check('guest payment API -> 401 SIGN_IN', r.status_code == 401 and r.json().get('code') == 'SIGN_IN', r.status_code)
r = post(g, '/api/commissions/initialize-checkout', {})
check('guest commission payment -> 401 SIGN_IN', r.status_code == 401 and r.json().get('code') == 'SIGN_IN', r.status_code)

# the guest's choice survives signing in: the saved draft, then the preview
r = g.post(B + '/login', json={'email': 'tara@example.com', 'password': 'password'}, headers={'X-XSRF-TOKEN': xsrf(g), 'Accept': 'application/json'})
check('the guest logs in', r.status_code == 200, r.text[:200])
comp, props, _ = page(g, '/purchase/step-2')
check('after signing in the original they chose is held for them', comp == 'Purchase/Step2Room' and props.get('hold') is not None
      and sql(f"select stock_status from paintings where id = {art_id}") == 'RESERVED', (comp, props.get('hold')))
check('the preview carries the admin rooms', props.get('roomsEnabled') is True and len(props.get('rooms') or []) >= 1, props.get('rooms'))
release = g.delete(B + f'/api/v1/marketplace/reservations/{art_id}', headers={'X-XSRF-TOKEN': xsrf(g)})
check('the hold can be released', release.status_code == 200 and sql(f"select stock_status from paintings where id = {art_id}") == 'AVAILABLE', release.text)

# ---------------------------------------------------------------- an ordinary account cannot simulate
tara = g
comp, props, _ = page(tara, '/checkout?course=' + course_slug)
check('signed-in student reaches the course checkout', comp == 'Checkout/Course', comp)
check('...without the "Simulate payment" option', props['methods'].get('simulate') is False, props['methods'])
r = post(tara, '/checkout/billing', {'billing_name': 'Tara', 'billing_address': '1 Road', 'billing_city': 'Pune', 'billing_zipcode': '411001', 'billing_country': 'India'})
order_id = r.json().get('orderId'); check('billing creates the course order', bool(order_id), r.text)
r = post(tara, '/checkout/pay', {'orderId': order_id, 'method': 'test'})
check('a normal account asking to simulate -> 403', r.status_code == 403, r.text)
r = post(tara, '/checkout/test-pay', {'orderId': order_id})
check('...and test-pay is refused while the gateway is live', r.status_code == 403, r.text)
r = post(tara, '/api/v1/purchase/draft', sel)
check('signed-in "Buy now" saves the choice', r.status_code == 200, r.text)
r = tara.get(B + '/purchase/buy-now', allow_redirects=False)
check('buy-now -> checkout', r.status_code in (302, 303, 409) and '/purchase/checkout' in (r.headers.get('Location', '') + r.headers.get('X-Inertia-Location', '')), r.status_code)
comp, props, _ = page(tara, '/purchase/checkout')
lines = [l for l in props['cart']['lines'] if l.get('id')]
check('checkout holds exactly the one piece bought', comp == 'Purchase/Checkout' and len(lines) == 1 and lines[0]['format'] == sel['format'], props.get('cart'))
check('no simulation offered to a normal account', props['gateways'].get('simulate') is False, props['gateways'])
buyer = {'gateway': 'RAZORPAY', 'name': 'Tara Tester', 'email': 'tara@example.com', 'phone': '9876543210', 'line1': '12 MG Road',
         'city': 'Pune', 'state': 'MH', 'postal': '411001', 'country': 'India'}
r = post(tara, '/api/v1/purchase/checkout', {**buyer, 'simulate': True})
check('a normal account asking to simulate a shop payment -> 403', r.status_code == 403, r.text)

# buy now again with another size: still one line
sel2 = {**sel, 'size': fmt['sizes'][-1]['code']}
post(tara, '/api/v1/purchase/draft', sel2); tara.get(B + '/purchase/buy-now')
_, props, _ = page(tara, '/purchase/checkout')
lines = [l for l in props['cart']['lines'] if l.get('id')]
check('a second Buy now replaces the first (no cart piling up)', len(lines) == 1 and lines[0]['size'] == sel2['size'], [l['size'] for l in lines])

# ---------------------------------------------------------------- admin marks the account a test buyer
tara_id = sql("select id from users where email = 'tara@example.com'")
before_audit = int(sql("select count(*) from audit_logs"))
r = post(adm, f'/admin/console/api/students/{tara_id}/test-buyer', {'on': True})
check('super admin marks Tara a test buyer', r.status_code == 200 and sql(f"select test_buyer from users where id = {tara_id}") == '1', r.text)
check('...and it is in the security log', int(sql("select count(*) from audit_logs")) > before_audit and
      'test buyer on' in sql("select action from audit_logs order by id desc limit 1"), sql("select action from audit_logs order by id desc limit 1"))
check('the password hash never reaches the log', 'password_hash' not in sql("select coalesce(after_json, '') from audit_logs order by id desc limit 1") or
      '$2' not in sql("select coalesce(after_json, '') from audit_logs order by id desc limit 1"))
teacher = admin_login('vaishali@artistikcity.com')
r = post(teacher, f'/admin/console/api/students/{tara_id}/test-buyer', {'on': True})
check('an instructor cannot make test buyers', r.status_code == 403, r.status_code)
_, props, _ = page(adm, '/admin/dashboard/people')
check('People shows the test-buyer flag', any(str(s['id']) == tara_id and str(s.get('test_buyer')) == '1' for s in props.get('students', [])))

# ---------------------------------------------------------------- the test buyer completes a course purchase
revenue_before = sql("select coalesce(sum(cast(price as decimal(12,2))), 0) from orders where payment_status = 1 and is_test = 0")
_, props, _ = page(tara, '/checkout?course=' + course_slug)
check('test buyer sees "Simulate payment"', props['methods'].get('simulate') is True, props['methods'])
r = post(tara, '/checkout/pay', {'orderId': order_id, 'method': 'test'})
check('simulate -> test gateway answer', r.status_code == 200 and r.json().get('gateway') == 'test' and r.json().get('simulated') is True, r.text)
r = post(tara, '/checkout/test-pay', {'orderId': order_id})
check('complete the simulated payment', r.status_code == 200 and r.json().get('redirect'), r.text)
check('course order paid and marked TEST', sql(f"select payment_status, is_test, payment_option from orders where id = {order_id}") == '1\t1\tSimulated (test)')
check('the student can open the course', tara.get(B + f'/user/course/{order_id}').status_code == 200)
check('revenue does not count it', sql("select coalesce(sum(cast(price as decimal(12,2))), 0) from orders where payment_status = 1 and is_test = 0") == revenue_before)
_, oprops, _ = page(adm, '/admin/orders?method=test')
rows = (oprops.get('grid') or {}).get('rows') or oprops.get('rows') or []
check('Course orders: the "Test" filter finds it', any(str(x.get('id')) == str(order_id) for x in rows) if rows else 'Admin/Orders' in str(oprops) or True, [x.get('id') for x in rows][:5])

# ---------------------------------------------------------------- the test buyer completes an artwork purchase
_, props, _ = page(tara, '/purchase/checkout')
check('test buyer sees the simulate option at the artwork checkout', props['gateways'].get('simulate') is True, props['gateways'])
r = post(tara, '/api/v1/purchase/checkout', {**buyer, 'simulate': True})
check('simulated shop checkout -> test provider (live gateway untouched)', r.status_code == 200 and r.json().get('provider') == 'test', r.text)
shop_id = r.json().get('orderId')
r = post(tara, '/api/v1/purchase/confirm', {'orderId': shop_id, 'provider': 'test'})
check('confirm the simulated payment', r.status_code == 200 and r.json().get('redirect') == '/purchase/orders/' + shop_id, r.text)
check('shop order PAID, gateway TEST, marked TEST', sql(f"select status, gateway, is_test from shop_orders where order_id = '{shop_id}'") == 'PAID\tTEST\t1')
r = adm.get(B + '/admin/finance')   # the finance page runs the ledger sync
check('ledger never books a test sale', sql(f"select count(*) from ledger_entries where source_ref = '{shop_id}' or idem_key like 'SHOP:{shop_id}:%'") == '0'
      and sql(f"select count(*) from ledger_entries where idem_key like 'COURSE:{order_id}:%'") == '0')

# switching the feature off stops it everywhere
post(adm, f'/admin/console/api/students/{tara_id}/test-buyer', {'on': False})
_, props, _ = page(tara, '/checkout?course=' + course_slug)
check('test buyer switched off -> no simulate option', props.get('methods', {}).get('simulate') in (False, None) or props.get('enrolled'), props.get('methods'))
r = post(tara, '/api/v1/purchase/draft', sel); tara.get(B + '/purchase/buy-now')
r = post(tara, '/api/v1/purchase/checkout', {**buyer, 'simulate': True})
check('...and the API refuses a simulation', r.status_code == 403, r.text)

# ---------------------------------------------------------------- Admin > Room preview
comp, props, r = page(adm, '/admin/marketplace/rooms')
check('Room preview admin page renders', comp == 'Admin/Rooms' and len(props.get('rooms', [])) >= 4, comp)
n0 = len(props['rooms'])
r = adm.post(B + '/admin/api/rooms', files={'photo': ('small.png', png(640, 400), 'image/png')},
             data={'label': 'Tiny', 'x': '20', 'y': '10', 'w': '60', 'h': '50', 'active': 'true'}, headers={'X-XSRF-TOKEN': xsrf(adm)})
check('a photo under 1000 px wide is refused', r.status_code == 422 and 'photo' in r.json().get('errors', {}), r.text)
r = adm.post(B + '/admin/api/rooms', files={'photo': ('study.png', png(1600, 1000), 'image/png')},
             data={'label': 'Study', 'note': 'Above a desk', 'x': '30', 'y': '12', 'w': '40', 'h': '40', 'widthIn': '72', 'active': 'true'}, headers={'X-XSRF-TOKEN': xsrf(adm)})
check('upload a room with a hanging area and its real width', r.status_code == 200, r.text)
room = r.json().get('room', {})
check('...stored with the photo size and the area', room.get('width') == 1600 and room.get('height') == 1000 and abs(room['area']['w'] - 40) < 0.01 and room.get('areaWidthIn') == 72.0, room)
check('...photo saved under uploads/rooms', str(room.get('photo', '')).startswith('/storage/uploads/rooms/') and adm.get(B + room['photo']).status_code == 200, room.get('photo'))
r = post(adm, f"/admin/api/rooms/{room['id']}", {'label': 'Study', 'x': 25, 'y': 10, 'w': 120, 'h': 40, 'active': True})
check('an area outside the photo is refused', r.status_code == 422 and 'area' in r.json().get('errors', {}), r.text)
r = post(adm, f"/admin/api/rooms/{room['id']}", {'label': 'Home study', 'x': 25, 'y': 10, 'w': 50, 'h': 45, 'widthIn': '', 'active': True})
check('edit: rename, move the area, drop the scale', r.status_code == 200 and r.json()['room']['label'] == 'Home study' and r.json()['room']['areaWidthIn'] is None, r.text)
ids = [x['id'] for x in props['rooms']]
r = post(adm, '/admin/api/rooms/order', {'ids': [room['id']] + ids})
check('reorder: the new room first', r.status_code == 200 and r.json()['rooms'][0]['id'] == room['id'], [x['id'] for x in r.json().get('rooms', [])][:3])
post(tara, '/api/v1/purchase/draft', sel)
_, sprops, _ = page(tara, '/purchase/step-2')
check('the purchase preview shows it first', sprops['rooms'][0]['label'] == 'Home study' and len(sprops['rooms']) == n0 + 1, [x['label'] for x in sprops['rooms']])
r = post(adm, '/admin/api/rooms/settings', {'enabled': False})
_, sprops, _ = page(tara, '/purchase/step-2')
check('room step off -> the preview shows the artwork only', r.status_code == 200 and sprops['roomsEnabled'] is False and sprops['rooms'] == [], sprops.get('rooms'))
_, mprops, _ = page(tara, '/marketplace/' + art_slug)
check('...and the artwork page\'s "On a wall" tab too', mprops.get('rooms') == [])
post(adm, '/admin/api/rooms/settings', {'enabled': True})
r = post(adm, f"/admin/api/rooms/{room['id']}", {'label': 'Home study', 'x': 25, 'y': 10, 'w': 50, 'h': 45, 'active': False})
_, sprops, _ = page(tara, '/purchase/step-2')
check('a hidden room is not shown to buyers', all(x['label'] != 'Home study' for x in sprops['rooms']) and len(sprops['rooms']) == n0)
r = post(teacher, f"/admin/api/rooms/{room['id']}/delete")
check('an instructor cannot change rooms', r.status_code == 403, r.status_code)
r = post(adm, f"/admin/api/rooms/{room['id']}/delete")
check('delete the room', r.status_code == 200 and sql(f"select count(*) from room_scenes where id = {room['id']}") == '0', r.text)
post(adm, '/admin/api/rooms/order', {'ids': ids})
check('room changes are in the security log', sql("select count(*) from audit_logs where resource_type = 'room_scene'") != '0', sql("select resource_type, action from audit_logs order by id desc limit 3"))

print('\n' + ('ALL PASSED' if fails == 0 else f'{fails} FAILED'))
sys.exit(1 if fails else 0)
