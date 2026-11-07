"""Cart, custom print sizes and "Make an offer", end to end (HTTP + DB). Dev profile (test payments), fresh database:
    run_app.sh dev   then   python3 tests/system/test_cart_offers.py

Covers
* cart: guests may add prints (not originals: 401 SIGN_IN), the guest cart merges at sign-in, quantities, save for later
  (wish list), remove, the 50-line cap, the /cart page;
* custom print sizes: priced from the presets by area (equal to a preset at a preset's size), limits refused, admin switch;
* Buy now pays for one line only and leaves the rest of the cart; "Checkout" from the cart pays for everything;
* an original sold while it sits in a cart blocks checkout for that line (409 CART_CHANGED);
* offers: rules (sign in, agree, lowest offer, not at the price, one live offer, daily limit, paused, per painting),
  counter / reoffer / accept, the 48-hour hold at the agreed price, paying it through the normal checkout (offer -> PAID),
  withdraw, expiry of unanswered and unpaid offers (hold released), student sellers, staff permissions, and two
  simultaneous acceptances on one painting (exactly one wins).
"""
import html, json, os, re, subprocess, threading, time, urllib.parse
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
def post(s, path, body=None): return s.post(B + path, json=body or {}, headers=H(s))
def put(s, path, body=None): return s.put(B + path, json=body or {}, headers=H(s))
def patch(s, path, body=None): return s.patch(B + path, json=body or {}, headers=H(s))
def delete(s, path): return s.delete(B + path, headers=H(s))
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'})
def page(s, path):
    r = s.get(B + path)
    m = re.search(r'data-page="([^"]+)"', r.text)
    d = json.loads(html.unescape(m.group(1))) if m else {}
    return d.get('component'), d.get('props', {}), r
def user(email, s=None):
    s = s or requests.Session(); s.get(B + '/login')
    r = s.post(B + '/login', json={'email': email, 'password': 'password'}, headers=H(s))
    assert r.status_code == 200, (email, r.text[:200])
    return s
def admin_login(email='admin@artistikcity.com'):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False); assert r.status_code == 302
    s.get(B + '/admin/dashboard'); return s
BUYER = {'name': 'Aarav Sharma', 'email': 'student@artistikcity.com', 'phone': '9876543210', 'line1': '12 Marine Drive', 'city': 'Mumbai',
         'state': 'Maharashtra', 'postal': '400002', 'country': 'India', 'gateway': 'RAZORPAY'}
def pay(s):
    r = post(s, '/api/v1/purchase/checkout', BUYER)
    assert r.status_code == 200, r.text[:300]
    oid = r.json()['orderId']
    c = post(s, '/api/v1/purchase/confirm', {'orderId': oid, 'provider': 'test'})
    return oid, c
def ids(s): return sorted((l['paintingId'], l['format'], l['size']) for l in get(s, '/api/v1/cart').json()['lines'])

adm = admin_login()
sql("delete from shop_cart_items"); sql("delete from shop_carts")

# ================================================================ cart
g = requests.Session(); g.get(B + '/marketplace')
r = post(g, '/api/v1/cart/items', {'paintingId': 1, 'format': 'POSTER', 'size': 'M', 'finish': 'MATTE', 'frame': 'NONE', 'qty': 2})
check('guest adds a poster to the cart', r.status_code == 200 and r.json()['count'] == 2, r.text)
r = post(g, '/api/v1/cart/items', {'paintingId': 6, 'format': 'ORIGINAL', 'frame': 'NONE', 'qty': 1})
check('guest adding an original -> 401 SIGN_IN back to the artwork', r.status_code == 401 and r.json().get('code') == 'SIGN_IN'
      and 'copper-kettle' in urllib.parse.unquote(r.json().get('redirect', '')), r.text)
check('no hold taken for the guest', sql("select count(*) from cart_reservations where painting_id = 6") == '0')
comp, props, _ = page(g, '/cart')
check('/cart renders the cart page for a guest', comp == 'Shop/Cart' and len(props['cart']['lines']) == 1, comp)
stu = user('student@artistikcity.com', g)
check('signing in merges the guest cart into the account', ids(stu) == [(1, 'POSTER', 'M')], ids(stu))

# custom sizes
opts = {r_[0]: (float(r_[1]), float(r_[2])) for r_ in (x.split('\t') for x in sql("select code, price_inr, extra_inr from shop_price_options where kind = 'CANVAS'").splitlines())}
r = post(stu, '/api/v1/cart/items', {'paintingId': 2, 'format': 'CANVAS', 'size': 'C24', 'finish': 'STRETCHED', 'frame': 'NONE', 'qty': 1})
line = next((l for l in r.json().get('lines', []) if l['size'] == 'C24'), None)
check('custom canvas at a preset long side costs exactly the preset', line and float(line['unitPrice']) == opts['M'][0] + opts['M'][1], (line, opts['M']))
check('custom line is labelled custom', line and '(custom)' in line['label'] and line['customSize'] is True, line)
r = post(stu, '/api/v1/cart/items', {'paintingId': 2, 'format': 'CANVAS', 'size': 'C30', 'finish': 'STRETCHED', 'frame': 'NONE', 'qty': 1})
l30 = next((l for l in r.json().get('lines', []) if l['size'] == 'C30'), None)
check('custom 30 in sits between the 24 and 36 in presets and ends in 99', l30 and opts['M'][0] + opts['M'][1] < float(l30['unitPrice']) < opts['L'][0] + opts['L'][1]
      and int(float(l30['unitPrice'])) % 100 == 99, l30)
check('custom 30 in on a square painting is 30 x 30 in', l30 and float(l30['width']) == 30 and float(l30['height']) == 30, l30)
r = post(stu, '/api/v1/cart/items', {'paintingId': 2, 'format': 'CANVAS', 'size': 'C70', 'finish': 'STRETCHED', 'frame': 'NONE', 'qty': 1})
check('custom long side over the limit -> 422 with the range', r.status_code == 422 and '60' in r.json().get('message', ''), r.text)
r = post(stu, '/api/v1/cart/items', {'paintingId': 2, 'format': 'DIGITAL', 'size': 'C30', 'qty': 1})
check('custom size makes no sense for a download -> refused', r.status_code in (409, 422), r.status_code)
r = put(adm, '/admin/console/api/shop/custom-size', {'enabled': True, 'min': 50, 'max': 20})
check('admin: custom range with max < min -> 422', r.status_code == 422 and 'max' in r.json().get('errors', {}), r.text)
r = put(adm, '/admin/console/api/shop/custom-size', {'enabled': False, 'min': 8, 'max': 60})
check('admin switches custom sizes off', r.status_code == 200, r.text)
cart = get(stu, '/api/v1/cart').json()
check('custom lines in a cart become unavailable when switched off', all(l['status'] != 'ok' for l in cart['lines'] if l['customSize']) and cart['totals']['blocked'], cart['lines'])
put(adm, '/admin/console/api/shop/custom-size', {'enabled': True, 'min': 8, 'max': 60})
check('custom sizes back on -> lines buyable again', not get(stu, '/api/v1/cart').json()['totals']['blocked'])
comp, props, _ = page(stu, '/marketplace/the-old-mill-at-dawn')
canvas = next(f for f in props.get('offer', {}).get('formats', []) if f['format'] == 'CANVAS') if props.get('offer') else None
check('the artwork page offers the custom range', canvas and canvas.get('custom') == {'min': 8, 'max': 60}, canvas and canvas.get('custom'))

# quantities, save for later, remove
poster = next(l for l in get(stu, '/api/v1/cart').json()['lines'] if l['format'] == 'POSTER')
r = patch(stu, f"/api/v1/cart/items/{poster['id']}", {'qty': 4})
check('change a print quantity', r.status_code == 200 and next(l for l in r.json()['lines'] if l['id'] == poster['id'])['qty'] == 4, r.text)
c30 = next(l for l in get(stu, '/api/v1/cart').json()['lines'] if l['size'] == 'C30')
r = post(stu, f"/api/v1/cart/items/{c30['id']}/save")
check('save for later moves the line to the wish list', r.status_code == 200 and all(l['id'] != c30['id'] for l in r.json()['lines'])
      and sql("select count(*) from wishlist_items where user_id = 1 and item_type = 'painting' and item_id = 2") == '1', r.text)
check('save for later needs an account', post(requests.Session(), f"/api/v1/cart/items/{poster['id']}/save").status_code in (401, 403))

# the 50-line cap
cid = sql("select id from shop_carts where owner_key = 'U:1'")
sql(f"insert into shop_cart_items (cart_id, painting_id, format, size_code, finish, frame, quantity, created_at, updated_at)"
    f" select {cid}, 1, 'POSTER', concat('Z', n), 'MATTE', 'NONE', 1, now(), now() from (select 1 n union select 2 union select 3 union select 4 union select 5 union select 6"
    f" union select 7 union select 8 union select 9 union select 10 union select 11 union select 12 union select 13 union select 14 union select 15 union select 16"
    f" union select 17 union select 18 union select 19 union select 20 union select 21 union select 22 union select 23 union select 24 union select 25 union select 26"
    f" union select 27 union select 28 union select 29 union select 30 union select 31 union select 32 union select 33 union select 34 union select 35 union select 36"
    f" union select 37 union select 38 union select 39 union select 40 union select 41 union select 42 union select 43 union select 44 union select 45 union select 46"
    f" union select 47 union select 48) t")
r = post(stu, '/api/v1/cart/items', {'paintingId': 3, 'format': 'POSTER', 'size': 'S', 'finish': 'MATTE', 'frame': 'NONE', 'qty': 1})
check('a cart holds at most 50 different lines (409 CART_FULL)', r.status_code == 409 and r.json().get('code') == 'CART_FULL', r.text)
sql(f"delete from shop_cart_items where cart_id = {cid} and size_code like 'Z%'")

# ================================================================ Buy now pays for one line, the cart stays
before = ids(stu)
r = post(stu, '/api/v1/purchase/draft', {'paintingId': 3, 'format': 'POSTER', 'size': 'L', 'finish': 'GLOSSY', 'frame': 'NONE', 'qty': 1})
check('buy-now draft saved', r.status_code == 200, r.text)
r = stu.get(B + '/purchase/buy-now', allow_redirects=False)
check('Buy now -> checkout', r.status_code in (302, 303) and r.headers['Location'].endswith('/purchase/checkout'), (r.status_code, r.headers.get('Location')))
comp, props, _ = page(stu, '/purchase/checkout')
check('checkout shows only the Buy now line', comp == 'Purchase/Checkout' and [l['paintingId'] for l in props['cart']['lines']] == [3], props.get('cart', {}).get('lines'))
check('...and says the rest stays in the cart', props.get('only') and props['only']['otherCount'] == len(before), props.get('only'))
check('the checkout API view agrees (?checkout=1)', [l['paintingId'] for l in get(stu, '/api/v1/cart?checkout=1').json()['lines']] == [3])
oid, c = pay(stu)
check('paying the Buy now order', c.status_code == 200, c.text)
check('the order has only that line', sql(f"select group_concat(painting_id) from shop_order_items where order_id = '{oid}'") == '3')
check('the rest of the cart is still there', ids(stu) == before, (ids(stu), before))
comp, props, _ = page(stu, '/purchase/checkout?all=1')
check('Checkout from the cart pays for everything', len(props['cart']['lines']) == len(before) and not props.get('only'), props.get('cart', {}).get('lines'))

# ================================================================ an original sold while in a cart
far = user('farhan.siddiqui@example.com')
r = post(far, '/api/v1/cart/items', {'paintingId': 6, 'format': 'ORIGINAL', 'frame': 'NONE', 'qty': 1})
check('collector adds an original (held for them)', r.status_code == 200 and sql("select session_or_user_id from cart_reservations where painting_id = 6") == 'U:4', r.text)
sql("delete from cart_reservations where painting_id = 6"); sql("update paintings set stock_status = 'SOLD', sold_at = now() where id = 6")
cart = get(far, '/api/v1/cart').json()
bad = next(l for l in cart['lines'] if l['paintingId'] == 6)
check('the sold original is marked in the cart', bad['status'] != 'ok' and bad['message'], bad)
r = post(far, '/api/v1/purchase/checkout', {**BUYER, 'name': 'Farhan Siddiqui', 'email': 'farhan.siddiqui@example.com'})
check('checkout refuses until it is removed (409 CART_CHANGED)', r.status_code == 409 and r.json().get('code') == 'CART_CHANGED', r.text)
delete(far, f"/api/v1/cart/items/{bad['id']}")
sql("update paintings set stock_status = 'AVAILABLE', sold_at = null where id = 6")

# ================================================================ offers: rules
r = post(requests.Session(), '/api/v1/offers', {'paintingId': 3, 'amount': 30000, 'agree': True})
check('guest offer -> 401 SIGN_IN', r.status_code in (401, 403), r.status_code)
t = get(stu, '/api/v1/offers/painting/3').json()
check('terms: painting 3 takes offers from 70%', t['open'] and float(t['minAmount']) == 29400 and t['minPct'] == 70, t)
r = post(stu, '/api/v1/offers', {'paintingId': 3, 'amount': 30000})
check('offer without the payment promise -> 422 agree', r.status_code == 422 and 'agree' in r.json().get('errors', {}), r.text)
r = post(stu, '/api/v1/offers', {'paintingId': 3, 'amount': 20000, 'agree': True})
check('offer under the lowest -> 422 TOO_LOW naming the minimum', r.status_code == 422 and r.json().get('code') == 'TOO_LOW' and '29,400' in r.json()['message'], r.text)
r = post(stu, '/api/v1/offers', {'paintingId': 3, 'amount': 42000, 'agree': True})
check('offer at the price -> 422 AT_PRICE (use Buy now)', r.status_code == 422 and r.json().get('code') == 'AT_PRICE', r.text)
r = post(stu, '/api/v1/offers', {'paintingId': 3, 'amount': 30500.5, 'agree': True})
check('offer in paise -> 422', r.status_code == 422, r.text)
r = post(stu, '/api/v1/offers', {'paintingId': 3, 'amount': 30000, 'agree': True, 'message': 'For my parents’ anniversary.'})
check('valid offer -> OPEN', r.status_code == 200 and r.json()['offer']['status'] == 'OPEN', r.text)
off = r.json()['offer']['id']
check('one live offer per buyer and painting (409 ALREADY)', post(stu, '/api/v1/offers', {'paintingId': 3, 'amount': 31000, 'agree': True}).json().get('code') == 'ALREADY')
check('the artwork page now shows "your offer"', get(stu, '/api/v1/offers/painting/3').json()['mine']['id'] == off)
check('students cannot offer on their own painting', post(user('tanvi.kulkarni@example.com'), '/api/v1/offers', {'paintingId': 11, 'amount': 3500, 'agree': True}).json().get('code') == 'OWN_PIECE')
diy = user('diya@example.com')
check('student listings default to no offers (painting 7)', get(diy, '/api/v1/offers/painting/7').json()['open'] is False)

# ================================================================ counter, reoffer, accept -> hold at the agreed price
r = post(stu, f'/api/v1/offers/{off}', {'action': 'accept'})
check('buyer cannot accept their own open offer (409 STALE)', r.status_code == 409, r.text)
r = post(adm, f'/admin/console/api/offers/{off}', {'action': 'counter', 'amount': 29000})
check('counter below the offer -> 422', r.status_code == 422, r.text)
r = post(adm, f'/admin/console/api/offers/{off}', {'action': 'counter', 'amount': 36000, 'note': 'Meet me in the middle?'})
check('studio counters', r.status_code == 200 and r.json()['offer']['status'] == 'COUNTERED', r.text)
r = post(stu, f'/api/v1/offers/{off}', {'action': 'reoffer', 'amount': 37000})
check('re-offer above the counter -> 422 (accept it instead)', r.status_code == 422, r.text)
r = post(stu, f'/api/v1/offers/{off}', {'action': 'reoffer', 'amount': 33000})
check('buyer offers again (round 2)', r.status_code == 200 and r.json()['offer']['status'] == 'OPEN' and r.json()['offer']['round'] == 2, r.text)
r = post(adm, f'/admin/console/api/offers/{off}', {'action': 'accept'})
check('studio accepts 33,000', r.status_code == 200 and r.json()['offer']['status'] == 'ACCEPTED' and float(r.json()['offer']['agreed']) == 33000, r.text)
hold = sql("select session_or_user_id, timestampdiff(hour, now(), expires_at) from cart_reservations where painting_id = 3").split('\t')
check('the piece is held for the buyer for ~48 hours', hold[0] == 'U:1' and int(hold[1]) >= 47, hold)
check('the painting shows as reserved', sql("select stock_status from paintings where id = 3") == 'RESERVED')
r = post(far, '/api/v1/marketplace/paintings/3/reserve')
check('nobody else can hold it (409)', r.status_code == 409, r.text)
check('nobody else can offer on it while held (409)', post(far, '/api/v1/offers', {'paintingId': 3, 'amount': 35000, 'agree': True}).status_code == 409)
check('a released cart line does not give the offer hold back', delete(stu, '/api/v1/marketplace/reservations/3').status_code in (200, 404, 409)
      and sql("select count(*) from cart_reservations where painting_id = 3") == '1')
r = post(stu, f'/api/v1/offers/{off}/pay')
check('Pay -> checkout', r.status_code == 200 and r.json()['redirect'] == '/purchase/checkout', r.text)
comp, props, _ = page(stu, '/purchase/checkout')
ln = props['cart']['lines'][0] if props.get('cart', {}).get('lines') else {}
check('checkout charges the agreed price for the original only', len(props['cart']['lines']) == 1 and ln.get('format') == 'ORIGINAL'
      and float(ln.get('unitPrice', 0)) == 33000 and ln.get('agreed') is True, props.get('cart'))
oid, c = pay(stu)
check('paying the accepted offer', c.status_code == 200, c.text)
check('order line at 33,000', sql(f"select unit_price_inr from shop_order_items where order_id = '{oid}'") == '33000.00')
check('the offer is PAID with the order', sql(f"select concat(status, ' ', order_id) from painting_offers where id = {off}") == f'PAID {oid}')
check('the painting is sold', sql("select stock_status from paintings where id = 3") == 'SOLD')

# ================================================================ student seller, buyer accepts a counter, withdraw
tan = user('tanvi.kulkarni@example.com')
r = get(tan, '/api/studio/sell/offers')
seeded = next((o for o in r.json()['offers'] if o['status'] == 'COUNTERED'), None)
check('the student sees offers on their painting', r.status_code == 200 and seeded and seeded['buyer'] == 'Farhan Siddiqui', r.text[:300])
check('a student cannot answer an offer on someone else\'s painting', post(tan, '/api/studio/sell/offers/1', {'action': 'accept'}).status_code == 404)
r = post(far, f"/api/v1/offers/{seeded['id']}", {'action': 'accept'})
check('buyer accepts the student\'s counter-offer (3,800)', r.status_code == 200 and r.json()['offer']['status'] == 'ACCEPTED' and float(r.json()['offer']['agreed']) == 3800, r.text)
check('student painting held for the buyer', sql("select session_or_user_id from cart_reservations where painting_id = 11") == 'U:4')
r = post(far, f"/api/v1/offers/{seeded['id']}", {'action': 'withdraw'})
check('buyer backs out of an accepted offer', r.status_code == 200 and r.json()['offer']['status'] == 'WITHDRAWN', r.text)
check('...and the hold is given back', sql("select count(*) from cart_reservations where painting_id = 11") == '0' and sql("select stock_status from paintings where id = 11") == 'AVAILABLE')
r = post(tan, '/api/studio/sell/listings/11/offers', {'enabled': False})
check('the student switches offers off for the listing', r.status_code == 200 and r.json()['effective'] is False and get(diy, '/api/v1/offers/painting/11').json()['open'] is False, r.text)
post(tan, '/api/studio/sell/listings/11/offers', {'enabled': True, 'minPct': 80})
check('...and back on from 80%', float(get(diy, '/api/v1/offers/painting/11').json()['minAmount']) == 3360)
d1 = sql("select id from painting_offers where user_id = 2 and painting_id = 2 and status = 'OPEN'")
r = post(diy, f'/api/v1/offers/{d1}', {'action': 'withdraw'})
check('buyer withdraws an open offer', r.status_code == 200 and r.json()['offer']['status'] == 'WITHDRAWN', r.text)

# ================================================================ permissions and settings
# (a student calling the admin API is signed out by the security watch, so use a throwaway session)
check('students can\'t use the staff API (401/403)', get(user('ishita.bose@example.com'), '/admin/console/api/offers').status_code in (401, 403))
mod = admin_login('moderator@artistikcity.com')
check('moderator (marketplace curation) sees offers but can\'t change the rules', get(mod, '/admin/console/api/offers').status_code == 200
      and put(mod, '/admin/console/api/offers/settings', {'enabled': True, 'minPct': 70, 'expiryHours': 72, 'payHours': 48, 'maxRounds': 3, 'dailyLimit': 5}).status_code == 403)
raj = admin_login('rajat@artistikcity.com')
check('a teacher only sees offers on their own pieces', get(raj, '/admin/console/api/offers').json()['offers'] == [])
check('...and can\'t answer others (404)', post(raj, '/admin/console/api/offers/1', {'action': 'decline'}).status_code == 404)
r = put(adm, '/admin/console/api/offers/settings', {'enabled': True, 'minPct': 5, 'expiryHours': 72, 'payHours': 48, 'maxRounds': 3, 'dailyLimit': 5})
check('settings: lowest offer under 10% -> 422', r.status_code == 422 and 'minPct' in r.json()['errors'], r.text)
r = put(adm, '/admin/console/api/offers/paintings/1', {'enabled': False})
check('per painting: offers off for painting 1', r.status_code == 200 and get(diy, '/api/v1/offers/painting/1').json()['open'] is False, r.text)
check('...so an offer is refused (409 NO_OFFERS)', post(diy, '/api/v1/offers', {'paintingId': 1, 'amount': 12000, 'agree': True}).json().get('code') == 'NO_OFFERS')
put(adm, '/admin/console/api/offers/paintings/1', {'enabled': None})
r = put(adm, '/admin/console/api/offers/settings', {'enabled': True, 'minPct': 70, 'expiryHours': 72, 'payHours': 48, 'maxRounds': 3, 'dailyLimit': 1})
check('settings saved (1 offer a day)', r.status_code == 200, r.text)
r = post(diy, '/api/v1/offers', {'paintingId': 1, 'amount': 12000, 'agree': True})
check('first offer of the day', r.status_code == 200, r.text)
r = post(diy, '/api/v1/offers', {'paintingId': 6, 'amount': 7000, 'agree': True})
check('second offer within 24 h -> 429 RATE_LIMIT', r.status_code == 429 and r.json().get('code') == 'RATE_LIMIT', r.text)
put(adm, '/admin/console/api/offers/settings', {'enabled': False, 'minPct': 70, 'expiryHours': 72, 'payHours': 48, 'maxRounds': 3, 'dailyLimit': 5})
check('offers paused -> no new offers', get(far, '/api/v1/offers/painting/6').json()['open'] is False)
put(adm, '/admin/console/api/offers/settings', {'enabled': True, 'minPct': 70, 'expiryHours': 72, 'payHours': 48, 'maxRounds': 3, 'dailyLimit': 5})
check('settings changes are in the security log', sql("select count(*) from audit_logs where summary like '%Offer rules%' or action like '%offer_settings%'") != '0')

# ================================================================ two acceptances at once: one wins
sql("delete from painting_offer_events where offer_id in (select id from painting_offers where painting_id = 6)"); sql("delete from painting_offers where painting_id = 6")
a = post(far, '/api/v1/offers', {'paintingId': 6, 'amount': 7000, 'agree': True}).json()['offer']['id']
ish = user('ishita.bose@example.com')
b2 = post(ish, '/api/v1/offers', {'paintingId': 6, 'amount': 7500, 'agree': True})
b = b2.json().get('offer', {}).get('id')
check('two buyers have open offers on one painting', a and b, b2.text)
res = {}
def acc(i, oid):
    s = admin_login(); res[i] = post(s, f'/admin/console/api/offers/{oid}', {'action': 'accept'}).status_code
ts = [threading.Thread(target=acc, args=(1, a)), threading.Thread(target=acc, args=(2, b))]
[t.start() for t in ts]; [t.join() for t in ts]
st = sql(f"select group_concat(status order by id) from painting_offers where id in ({a}, {b})")
check('exactly one acceptance wins, the other offer closes', sorted(res.values()) == [200, 409] and sorted(st.split(',')) == ['ACCEPTED', 'CLOSED'], (res, st))
check('one hold on the painting', sql("select count(*) from cart_reservations where painting_id = 6") == '1')

# ================================================================ expiry (the sweep runs every minute at :30)
winner = sql("select id from painting_offers where painting_id = 6 and status = 'ACCEPTED'")
# (the app keeps its own clock, maybe in another time zone than MySQL's NOW(): use a date long gone)
sql(f"update painting_offers set pay_by = '2000-01-01 00:00:00' where id = {winner}")
sql("update painting_offers set expires_at = '2000-01-01 00:00:00' where user_id = 2 and painting_id = 1 and status = 'OPEN'")
deadline = time.time() + 75
while time.time() < deadline:
    if sql(f"select status from painting_offers where id = {winner}") == 'EXPIRED' and sql("select status from painting_offers where user_id = 2 and painting_id = 1 order by id desc limit 1") == 'EXPIRED':
        break
    time.sleep(3)
check('an unpaid accepted offer expires', sql(f"select status from painting_offers where id = {winner}") == 'EXPIRED')
check('...and its hold is released (painting back on sale)', sql("select count(*) from cart_reservations where painting_id = 6") == '0' and sql("select stock_status from paintings where id = 6") == 'AVAILABLE')
check('an unanswered offer expires', sql("select status from painting_offers where user_id = 2 and painting_id = 1 order by id desc limit 1") == 'EXPIRED')

# ================================================================ pages
comp, props, _ = page(stu, '/user/offers')
check('Your offers page lists the buyer\'s offers', comp == 'Account/Offers' and any(o['id'] == off and o['status'] == 'PAID' for o in props['offers']), comp)
check('the nav counts offers waiting for the buyer', 'offers' in (props.get('nav') or {}), props.get('nav'))
check('admin marketplace console has the offers tab data', page(adm, '/admin/dashboard/marketplace?tab=offers')[1].get('shop', {}).get('offersOpen') is not None)

print('\n' + ('ALL PASSED' if fails == 0 else f'{fails} FAILED'))
raise SystemExit(1 if fails else 0)
