"""SEO & Layout Controls page tabs (Our vision, Instructor, Customer support, FAQ) and shop checkout test payments.

* /artistikcity-vision, /instructor, /customer-support and /faq are React pages (Info/*) with the defaults on a fresh DB:
  no placeholder text, no dead "#" links, every link they carry answers, the FAQ shows the faqs table.
* POST /admin/api/setup/site-pages/{page}: 401 guest/student, 403 teacher; bad links / unknown staff / bad JSON = 422 and
  nothing saved; rich text is cleaned (no <script>); saved values show on the public page at once; empty = default again;
  every save is in the security log. /admin/faq redirects to the FAQ tab.
* The site-settings page carries the tab data (pages, faqs, staff).
* Shop checkout "Simulate payment" (TestPayments.offerShopSimulation): PUT /admin/console/api/shop/test-payments
  validates and is super-admin only; in a profile without test mode a plain student does not get the option (403 on
  simulate) unless the mode allows it (staff signed in to the admin in the same browser, or everyone); simulated orders
  are is_test = 1. The copyright line reads 2021.

Fresh DB (schema + seed), dev or production profile:  python3 tests/system/test_site_pages.py
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
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'}, allow_redirects=False)
def page(s, path):
    r = s.get(B + path)
    m = re.search(r'data-page="([^"]+)"', r.text)
    d = json.loads(html.unescape(m.group(1))) if m else {}
    return d.get('component'), d.get('props', {}), r
def admin_login(email='admin@artistikcity.com', s=None):
    s = s or requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False)
    assert r.status_code == 302, (email, r.status_code)
    s.get(B + '/admin/dashboard'); return s
def student(email='student@artistikcity.com'):
    s = requests.Session(); s.get(B + '/login')
    r = s.post(B + '/login', json={'email': email, 'password': 'password'}, headers=H(s))
    assert r.status_code == 200, r.text[:200]
    return s

sql("delete from settings where meta_tag like 'page\\_%' or (meta_tag = 'shop' and meta_key = 'simulate_payments')")
guest = requests.Session(); guest.get(B + '/')
adm = admin_login()
for _p in ('vision', 'instructor', 'support', 'faq'): post(adm, f'/admin/api/setup/site-pages/{_p}', {})   # drops the settings cache
stu = student()

# ======================================================================== public pages with the defaults
COMP = {'/artistikcity-vision': 'Info/Vision', '/instructor': 'Info/Instructor', '/customer-support': 'Info/Support', '/faq': 'Info/Faq'}
links = set()
for path, comp in COMP.items():
    c, p, r = page(guest, path)
    check(f'{path} renders {comp}', r.status_code == 200 and c == comp, (r.status_code, c))
    body = r.text.lower()
    check(f'{path}: no placeholder text', 'lorem' not in body and 'title here' not in body and 'vero eos' not in body)
    blob = json.dumps(p)
    check(f'{path}: no "#" links in the props', '"href": "#"' not in blob and "href='#'" not in blob)
    for h in re.findall(r'"(?:href|cta_link)": "(/[^"#]*)"', blob): links.add(h)
    check(f'{path}: SEO title from the page settings', re.search(r'<title[^>]*>[^<]*ArtistikCity', r.text) is not None)
c, p, _ = page(guest, '/instructor')
people = p.get('people', [])
check('instructor: the default instructor is shown with real social links', len(people) == 1 and people[0]['links'].get('linkedin', '').startswith('https://')
      and all(v.startswith('https://') for v in people[0]['links'].values()), people)
check('instructor: no private fields', 'email' not in json.dumps(people) and 'mfa' not in json.dumps(people))
c, p, _ = page(guest, '/faq')
n_faq = int(sql("select count(*) from faqs where status = 1"))
check('faq: every visible question from the faqs table', sum(len(g['items']) for g in p['groups']) == n_faq, (n_faq, p['groups']))
c, p, _ = page(guest, '/customer-support')
check('support: topics and the first questions', len(p['page']['topics']) == 6 and len(p.get('faqs', [])) == min(5, n_faq), p.get('faqs'))
for h in sorted(links):
    r = guest.get(B + h, allow_redirects=False)
    check(f'link {h} answers (guest: page or sign-in redirect)', r.status_code in (200, 302), r.status_code)
for alias, to in (('/help', 'Info/Support'), ('/faqs', 'Info/Faq'), ('/vision', 'Info/Vision'), ('/instructors', 'Info/Instructor')):
    check(f'{alias} also works', page(guest, alias)[0] == to)

# ======================================================================== who may save
# (a student session that calls the admin API is signed out by SecurityWatch, so use a throwaway one)
for who, s, code in (('guest', guest, 401), ('student', student(), 401)):
    r = post(s, '/admin/api/setup/site-pages/vision', {'heading': 'x'})
    check(f'{who}: save -> {code}', r.status_code == code, r.status_code)
tea = admin_login('rajat@artistikcity.com')
check('teacher: save -> 403', post(tea, '/admin/api/setup/site-pages/vision', {'heading': 'x'}).status_code == 403)
check('unknown page -> 404', post(adm, '/admin/api/setup/site-pages/nope', {'heading': 'x'}).status_code == 404)

# ======================================================================== validation
r = post(adm, '/admin/api/setup/site-pages/vision', {'cta_link': 'javascript:alert(1)', 'heading': 'Fine'})
check('vision: javascript link -> 422 and nothing saved', r.status_code == 422 and 'cta_link' in r.json()['errors']
      and sql("select count(*) from settings where meta_tag = 'page_vision'") == '0', r.text[:200])
r = post(adm, '/admin/api/setup/site-pages/support', {'topics': json.dumps([{'title': 'Orders', 'href': 'ftp://x'}])})
check('support: bad topic link -> 422', r.status_code == 422 and 'topics' in r.json()['errors'])
r = post(adm, '/admin/api/setup/site-pages/support', {'topics': json.dumps([{'title': '', 'href': '/faq'}])})
check('support: topic without title -> 422', r.status_code == 422)
r = post(adm, '/admin/api/setup/site-pages/support', {'topics': 'not json'})
check('support: unreadable list -> 422', r.status_code == 422)
r = post(adm, '/admin/api/setup/site-pages/support', {'faq_count': '99'})
check('support: faq_count out of range -> 422', r.status_code == 422)
r = post(adm, '/admin/api/setup/site-pages/instructor', {'people': json.dumps([{'id': 99999}])})
check('instructor: unknown staff -> 422', r.status_code == 422)
r = post(adm, '/admin/api/setup/site-pages/instructor', {'people': json.dumps([{'id': 2, 'facebook': 'http://insecure.example'}])})
check('instructor: non-https link -> 422', r.status_code == 422)
r = post(adm, '/admin/api/setup/site-pages/instructor', {'people': json.dumps([{'id': 2}, {'id': 2}])})
check('instructor: same person twice -> 422', r.status_code == 422)

# ======================================================================== save -> public page
r = post(adm, '/admin/api/setup/site-pages/vision', {'heading': 'Where we are going', 'intro': 'Short intro <b>no tags</b>',
                                                       'body': '<h2>Plan</h2><p>Paint daily.</p><script>alert(1)</script>',
                                                       'highlights': json.dumps([{'icon': 'fa-star-o', 'title': 'One', 'text': 't', 'label': 'Go', 'href': '/courses?type=all'}]),
                                                       'cta_label': 'Start', 'cta_link': '/courses?type=all'})
check('vision: valid save -> 200', r.status_code == 200, r.text[:200])
c, p, _ = page(guest, '/artistikcity-vision')
pg = p['page']
check('vision: heading, cards and button live at once', pg['heading'] == 'Where we are going' and len(pg['highlights']) == 1 and pg['cta_label'] == 'Start')
check('vision: script removed, tags stripped from the intro', '<script' not in pg['body'] and 'Paint daily' in pg['body'] and '<' not in pg['intro'])
check('vision: saved in settings page_vision', sql("select meta_value from settings where meta_tag = 'page_vision' and meta_key = 'heading'") == 'Where we are going')
check('vision: change is in the audit trail', int(sql("select count(*) from admin_audit where details = 'page_vision'")) >= 1)
r = post(adm, '/admin/api/setup/site-pages/vision', {'heading': ''})
check('vision: empty heading = the default again', page(guest, '/artistikcity-vision')[1]['page']['heading'] == 'Our vision')

r = post(adm, '/admin/api/setup/site-pages/instructor', {'heading': 'Our teachers', 'show_courses': '0',
                                                           'people': json.dumps([{'id': 4, 'website': 'https://example.org/rajat'}, {'id': 2, 'linkedin': ''}])})
check('instructor: valid save -> 200', r.status_code == 200, r.text[:200])
p = page(guest, '/instructor')[1]
check('instructor: people in the chosen order, only filled links, courses off', [x['id'] for x in p['people']] == [4, 2]
      and p['people'][0]['links'] == {'website': 'https://example.org/rajat'} and p['people'][1]['links'] == {} and 'courses' not in p['people'][0], p['people'])

r = post(adm, '/admin/api/setup/site-pages/support', {'show_faq': '0', 'show_contact': '0', 'hours': 'Mon-Fri 10-6'})
p = page(guest, '/customer-support')[1]
check('support: questions and contact band switched off, hours shown', 'faqs' not in p and p['page']['show_contact'] == '0' and p['page']['hours'] == 'Mon-Fri 10-6')
r = post(adm, '/admin/api/setup/site-pages/faq', {'heading': 'Questions', 'show_search': '0'})
p = page(guest, '/faq')[1]
check('faq: heading and search switch saved', p['page']['heading'] == 'Questions' and p['page']['show_search'] == '0')

# ======================================================================== admin page
r = adm.get(B + '/admin/faq', allow_redirects=False)
check('/admin/faq -> SEO & Layout Controls, FAQ tab', r.status_code == 302 and r.headers['Location'].endswith('/admin/setup/site-settings#faq'))
c, p, _ = page(adm, '/admin/setup/site-settings')
check('site settings page carries the tab data', c == 'Admin/Setup/SiteSettings' and set(p['pages']['values']) == {'vision', 'instructor', 'support', 'faq'}
      and len(p['faqs']) >= n_faq and any(x['slug'] == 'vaishali-j-goswami' for x in p['staff']), list(p))
check('FAQ API still works from the tab', post(adm, '/admin/api/faqs', {'question': 'Test question?', 'answer': '<p>Yes.</p>', 'category': 'Test', 'status': True}).status_code == 200)
check('new question on /faq', any(i['question'] == 'Test question?' for g in page(guest, '/faq')[1]['groups'] for i in g['items']))

# ======================================================================== copyright
for path in ('/', '/contact', '/privacy-policy', '/terms-conditions'):
    t = guest.get(B + path).text
    check(f'{path}: no current-year copyright in the HTML', '2026 ArtistikCity' not in t)
check('footer bundle says 2021', '2021 ArtistikCity' in open('src/main/resources/static/js/app.js', encoding='utf-8').read())

# ======================================================================== shop checkout test payments
_r = put(tea, '/admin/console/api/shop/test-payments', {'mode': 'everyone'})
check('test-payments: teacher -> 403', _r.status_code in (401, 403) and sql("select count(*) from settings where meta_tag = 'shop' and meta_key = 'simulate_payments'") == '0', (_r.status_code, _r.text[:200]))
check('test-payments: bad mode -> 422', put(adm, '/admin/console/api/shop/test-payments', {'mode': 'all'}).status_code == 422)
check('test-payments: default is staff', page(adm, '/admin/dashboard/marketplace')[1].get('shop', {}).get('testPayments', {}).get('mode') in ('staff', None))
sql("update users set test_buyer = 0 where email = 'student@artistikcity.com'")
sql("delete from shop_cart_items"); sql("delete from shop_carts")
r = post(stu, '/api/v1/cart/items', {'paintingId': 1, 'format': 'POSTER', 'size': 'M', 'finish': 'MATTE', 'frame': 'NONE', 'qty': 1})
check('cart line added', r.status_code in (200, 201), r.text[:200])
BUYER = {'name': 'Aarav Sharma', 'email': 'student@artistikcity.com', 'phone': '9876543210', 'line1': '12 Marine Drive', 'city': 'Mumbai',
         'state': 'Maharashtra', 'postal': '400002', 'country': 'India', 'gateway': 'RAZORPAY'}
_c, _p, _r = page(stu, '/purchase/checkout'); gw = _p.get('gateways', {})
check('checkout page opens with the cart', _c == 'Purchase/Checkout', (_c, _r.url, _r.status_code))
if not gw.get('testMode'):
    put(adm, '/admin/console/api/shop/test-payments', {'mode': 'test_buyers'})
    check('test_buyers mode: plain student has no Simulate option', page(stu, '/purchase/checkout')[1]['gateways']['simulate'] is False)
    check('test_buyers mode: simulate -> 403', post(stu, '/api/v1/purchase/checkout', {**BUYER, 'simulate': True}).status_code == 403)
    put(adm, '/admin/console/api/shop/test-payments', {'mode': 'staff'})
    check('staff mode: plain student still none', page(stu, '/purchase/checkout')[1]['gateways']['simulate'] is False)
    admin_login(s=stu)                                    # the same browser signs in to the admin
    check('staff mode: student + admin in the same browser gets it', page(stu, '/purchase/checkout')[1]['gateways']['simulate'] is True)
    stu2 = student()
    put(adm, '/admin/console/api/shop/test-payments', {'mode': 'everyone'})
    check('everyone mode: any signed-in buyer gets it', page(stu2, '/purchase/checkout')[1]['gateways']['simulate'] is True)
    r = post(stu2, '/api/v1/purchase/checkout', {**BUYER, 'simulate': True})
    check('simulated checkout starts a TEST order', r.status_code == 200 and r.json().get('provider') == 'test', r.text[:200])
    oid = r.json().get('orderId')
    c = post(stu2, '/api/v1/purchase/confirm', {'orderId': oid, 'provider': 'test'})
    check('simulated payment completes', c.status_code == 200, c.text[:200])
    check('the order is a test order', sql(f"select is_test from shop_orders where order_id = '{oid}'") == '1')
    put(adm, '/admin/console/api/shop/test-payments', {'mode': 'staff'})
else:
    put(adm, '/admin/console/api/shop/test-payments', {'mode': 'everyone'})
    check('dev: everyone mode -> simulate offered', page(stu, '/purchase/checkout')[1]['gateways']['simulate'] is True)
    r = post(stu, '/api/v1/purchase/checkout', {**BUYER, 'simulate': True})
    oid = r.json().get('orderId')
    c = post(stu, '/api/v1/purchase/confirm', {'orderId': oid, 'provider': 'test'})
    check('dev: simulated order completes as a test order', c.status_code == 200 and sql(f"select is_test from shop_orders where order_id = '{oid}'") == '1', c.text[:200])
    put(adm, '/admin/console/api/shop/test-payments', {'mode': 'staff'})
check('mode change is audited', int(sql("select count(*) from admin_audit where details like 'Shop checkout simulated payments%'")) >= 1)

print('\n' + ('ALL PASS' if fails == 0 else f'{fails} FAILED'))
raise SystemExit(1 if fails else 0)
