"""Landing Page Management - regression suite. Every group sets up what it needs and can run on its own:

    python3 tests/system/test_landing_regression.py                 # all groups
    python3 tests/system/test_landing_regression.py sections phone   # some groups

Run against a server WITHOUT test mode, SMS provider, mail server or Razorpay keys, like the live site
(java -jar target/artistikcity-1.0.0.jar, no dev profile), from the repository root, on a fresh database
(schema.sql + seed.sql) or on a database upgraded with tests/system/test_landing_migrations.py --install. Nothing here
relies on fixed ids: pages are found by address, and the four test pages are made with Login & verification › Test pages.

Groups
  kit        test pages: permissions, create (published, kept out of search), idempotent, remove (archived), restore
  sections   every section type on its own (rendered, hidden when switched off, mobile hiding, every layout), info pages
  phone      guest -> details -> SMS code at Pay -> account -> simulated Razorpay -> congratulations -> e-mail -> My studio
  email      the same with an e-mail code
  either     the buyer picks the channel; both work
  signin     "sign in first": no code at Pay; password sign-in then pay
  payments   failure, retry on the same order, window closed, wrong method, price tampering, coupons, repricing, free
  security   CSRF, other people's orders, code replay / brute force, codes bound to the session, disabled accounts, redirects
  returning  a buyer who already owns the course; a returning buyer signs in by code; real accounts never get simulated codes
  analytics  views, checkout, pay start / fail events; test orders out of revenue and the funnel
  admin      Login & verification and Test payments APIs: permissions, validation, audit; orders list; undo a test order
"""
import glob, html, os, re, subprocess, sys, time, urllib.parse, uuid
import requests

B = os.environ.get('BASE_URL', 'http://localhost:8080')
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
OUTBOX = os.environ.get('MAIL_OUTBOX', 'logs/mail-outbox')
PAGES = {'phone': 'test-mobile-otp-razorpay', 'email': 'test-email-otp-razorpay', 'either': 'test-buyer-chooses-otp', 'signin': 'test-sign-in-first'}
fails = 0
group = '-'


def sql(q): return subprocess.run(MYSQL + [q], capture_output=True, text=True, check=True).stdout.strip()
def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + f'[{group}] ' + name + ('' if cond else f'  {str(extra)[:700]}'))
    if not cond: fails += 1
def xsrf(s): return urllib.parse.unquote(s.cookies.get('XSRF-TOKEN', ''))
def H(s): return {'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'}
def post(s, path, body=None): return s.post(B + path, json=body if body is not None else {}, headers=H(s))
def put(s, path, body=None): return s.put(B + path, json=body if body is not None else {}, headers=H(s))
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'})
def uniq(): return uuid.uuid4().hex[:8]
def phone():  # a fresh Indian mobile number
    return '9' + str(int(time.time() * 1000))[-5:] + str(uuid.uuid4().int)[-4:]


def staff(email='admin@artistikcity.com'):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False)
    assert r.status_code == 302, email
    s.get(B + '/admin/dashboard'); return s


def student(email, password='password'):
    s = requests.Session(); s.get(B + '/login')
    r = s.post(B + '/login', json={'email': email, 'password': password}, headers=H(s))
    assert r.status_code == 200, (email, r.status_code)
    return s


ADM = staff()
_kit_ready = False


def kit():
    """The four test pages, published, with simulated SMS / e-mail codes and Razorpay on; returns {key: page id}."""
    global _kit_ready
    if not _kit_ready:
        r = post(ADM, '/admin/api/landing/test-kit', {'simulate': True})
        assert r.status_code == 200, r.text
        post(ADM, '/admin/api/landing/test-payments/simulate-razorpay', {'everyone': False})
        put(ADM, '/admin/api/landing/login', {'checkoutVerify': 'PHONE', 'simulatePhone': True, 'simulateEmail': True})
        _kit_ready = True
    return {k: int(sql(f"select id from landing_pages where slug = '{v}'")) for k, v in PAGES.items()}


def visit(slug):
    s = requests.Session()
    r = s.get(B + '/landing/' + slug)
    assert r.status_code == 200, (slug, r.status_code)
    return s


def outbox_since(t0, needle):
    for f in sorted(glob.glob(os.path.join(OUTBOX, '*.html')), key=os.path.getmtime, reverse=True):
        if os.path.getmtime(f) >= t0 - 1:
            txt = html.unescape(open(f, encoding='utf-8').read())
            if needle in txt:
                return txt
    return ''


def send_code(s, pid, body):
    """POST verify/send like a buyer. The suite sends many codes from one address: the per-network hourly limit (rows in
    auth_otps / auth_email_codes) is cleared between sends, and the 5-per-minute burst limit is waited out."""
    for _ in range(3):
        sql("update auth_otps set ip_address = null where ip_address is not null")
        sql("update auth_email_codes set ip_address = null where ip_address is not null")
        r = post(s, f'/landing/checkout/{pid}/verify/send', body)
        if r.status_code == 429 and 'minute' in r.json().get('message', ''):
            time.sleep(min(int(r.json().get('retryAfter', 60)), 65) + 1)
            continue
        return r
    return r


def guest_buy(key, channel='phone', outcome='success', method='upi', name=None):
    """A brand-new visitor buys on a test page. Returns (session, page id, order id, details)."""
    pid = kit()[key]
    s = visit(PAGES[key])
    name = name or f'Guest {uniq().title()}'
    email = f'guest.{uniq()}@example.com'
    mob = phone()
    r = send_code(s, pid, {'channel': channel, 'name': name, 'email': email, 'phone': mob})
    assert r.status_code == 200, r.text
    j = r.json()
    r = post(s, f'/landing/checkout/{pid}/verify/check', {'code': j['code']})
    assert r.status_code == 200, r.text
    o = post(s, f'/landing/checkout/{pid}/order', {'name': name, 'phone': mob, 'terms': True}).json()['orderId']
    r = post(s, f'/landing/checkout/{pid}/pay', {'orderId': o, 'method': method})
    assert r.status_code == 200 and r.json()['gateway'] == 'razorpay-sim', r.text
    if outcome:
        r = post(s, f'/landing/checkout/{pid}/simulate', {'orderId': o, 'outcome': outcome})
        assert r.status_code == 200, r.text
    return s, pid, o, {'name': name, 'email': email, 'phone': mob, 'sent': j}


# =================================================================================================== groups

def g_kit():
    teacher = staff('vaishali@artistikcity.com')
    check('teacher can\'t create test pages', post(teacher, '/admin/api/landing/test-kit', {'simulate': True}).status_code == 403)
    check('teacher can\'t read them', get(teacher, '/admin/api/landing/test-kit').status_code == 403)
    r = post(ADM, '/admin/api/landing/test-kit', {'simulate': True})
    j = r.json()
    check('four test pages published', r.status_code == 200 and [p['status'] for p in j['pages']] == ['PUBLISHED'] * 4, j)
    check('simulations switched on with them', get(ADM, '/admin/api/landing/login').json()['settings']['simulatePhone'] is True
          and get(ADM, '/admin/api/landing/test-payments').json()['simulateRazorpay'] is True)
    ids = kit()
    for key, slug in PAGES.items():
        r = requests.get(B + '/landing/' + slug)
        check(f'{slug}: public, labelled, kept out of search', r.status_code == 200 and 'noindex' in r.text and 'Test page' in r.text, r.status_code)
        check(f'{slug}: its own verification', sql(f"select coalesce(verify_with, 'DEFAULT') from landing_checkout where landing_page_id = {ids[key]}") == {
            'phone': 'PHONE', 'email': 'EMAIL', 'either': 'EITHER', 'signin': 'SIGNIN'}[key])
    sm = requests.get(B + '/sitemap.xml').text
    check('not in the sitemap', not any(slug in sm for slug in PAGES.values()))
    n = sql("select count(*) from landing_pages where slug like 'test-%'")
    r = post(ADM, '/admin/api/landing/test-kit', {'simulate': True})
    check('second click: nothing duplicated', r.status_code == 200 and sql("select count(*) from landing_pages where slug like 'test-%'") == n
          and all(x['status'] == 'published' for x in r.json()['results']), r.text)
    check('audited', sql("select count(*) from admin_audit where details like 'Landing test pages:%'") != '0')
    r = post(ADM, '/admin/api/landing/test-kit/remove')
    check('remove: archived, off the site', r.status_code == 200 and all(p['status'] == 'ARCHIVED' for p in r.json()['pages'])
          and requests.get(B + '/landing/' + PAGES['phone']).status_code == 404, r.text)
    r = post(ADM, '/admin/api/landing/test-kit', {'simulate': True})
    check('create again: the archived pages come back (no copies)', all(p['status'] == 'PUBLISHED' for p in r.json()['pages'])
          and sql("select count(*) from landing_pages where slug like 'test-%'") == n, r.text)
    # an address used by somebody else's page is left alone
    sql("update landing_pages set name = 'Someone else''s page' where slug = 'test-sign-in-first'")
    r = post(ADM, '/admin/api/landing/test-kit', {'simulate': True})
    check('an address used by another page is never taken over', [x for x in r.json()['results'] if x['slug'] == 'test-sign-in-first'][0]['status'] == 'skipped')
    sql("update landing_pages set name = '[Test] Sign in first' where slug = 'test-sign-in-first'")


SECTION_CONTENT = {
    'intro': {'body': 'Intro body text', 'items': [{'title': 'Point one', 'text': 'Why'}]},
    'outcomes': {'items': [{'title': 'Outcome A', 'text': 'You can'}]},
    'curriculum': {},
    'artist': {'body': 'About the artist'},
    'gallery': {'source': 'custom', 'items': [{'title': 'Lake', 'text': '', 'image': '/assets/images/logo.png'}]},
    'benefits': {'items': [{'title': 'Benefit A', 'text': 'Included'}]},
    'video': {'video': 'https://www.youtube.com/watch?v=dQw4w9WgXcQ'},
    'testimonials': {'source': 'reviews'},
    'pricing': {'body': 'Enrol now'},
    'faq': {'items': [{'title': 'Question one?', 'text': 'Answer one.'}]},
    'related': {},
    'final_cta': {'body': 'Join us'},
    'custom': {'html': '<p>Custom <b>content</b></p>'},
    'cards': {'items': [{'title': 'Card A', 'text': 'Card text'}]},
    'steps': {'items': [{'title': 'Step A', 'text': 'First'}]},
    'courses': {},
    'artists': {},
    'commission_form': {},
    'commission_pricing': {},
}
LAYOUTS = {'hero': ['split', 'immersive', 'editorial', 'video', 'collage'], 'intro': ['columns', 'stacked'], 'outcomes': ['cards', 'list'], 'curriculum': ['accordion'], 'artist': ['portrait', 'wide'], 'gallery': ['masonry', 'strip'], 'benefits': ['grid', 'list'], 'video': ['wide', 'split'], 'testimonials': ['cards', 'quote'], 'pricing': ['card', 'split'], 'faq': ['accordion'], 'related': ['cards'], 'final_cta': ['band', 'artwork'], 'custom': ['narrow', 'wide'], 'cards': ['grid', 'list', 'tiles'], 'steps': ['timeline', 'row'], 'courses': ['cards'], 'artists': ['cards'], 'commission_form': ['cards'], 'commission_pricing': ['table']}     # LandingCatalog.SECTIONS
NEEDS_DATA = {'testimonials', 'related', 'artists', 'courses', 'curriculum'}     # empty when the catalogue has nothing to show


def g_sections():
    course = sql("select course_id from landing_pages where slug = 'test-mobile-otp-razorpay'") if kit() else '1'
    r = post(ADM, '/admin/api/landing/pages', {'name': f'Sections {uniq()}', 'courseId': int(course), 'template': 'watercolour-wash'})
    assert r.status_code == 200, r.text
    P = r.json()['id']
    base = get(ADM, f'/admin/api/landing/pages/{P}').json()
    hero = [s for s in base['config']['sections'] if s['type'] == 'hero'][0]
    layouts = LAYOUTS
    saved_at = base['page']['draft_saved_at']

    def save(sections):
        nonlocal saved_at
        cfg = dict(base['config']); cfg['sections'] = sections
        r = put(ADM, f'/admin/api/landing/pages/{P}/draft', {'config': cfg, 'savedAt': saved_at, 'force': True})
        if r.status_code == 200:
            saved_at = r.json().get('savedAt', saved_at)
        return r

    for t, extra in SECTION_CONTENT.items():
        if t in ('commission_form', 'commission_pricing'):
            continue        # information-page sections, tested below
        sid = f'{t.replace("_", "-")}-{uniq()[:6]}'
        mark = f'SEC {t} {uniq()}'
        content = {'kicker': '', 'heading': mark}
        content.update(extra)
        sec = {'id': sid, 'type': t, 'enabled': True, 'layout': LAYOUTS[t][0], 'background': 'plain', 'hideOnMobile': False, 'content': content}
        r = save([hero, sec])
        check(f'{t}: saved on its own after the hero', r.status_code == 200, r.text[:300])
        page = ADM.get(B + f'/admin/landing/preview/{P}').text
        shown = f'id="{sid}"' in page
        if t in NEEDS_DATA and not shown:
            check(f'{t}: nothing to show yet, so the section is left out cleanly', page.count('<section id=') == 1, page.count('<section id='))
        else:
            check(f'{t}: rendered with its heading', shown and (mark in page or t in ('pricing', 'curriculum', 'video')), (shown, mark in page))
        sec['enabled'] = False
        save([hero, sec])
        check(f'{t}: switched off -> not on the page', f'id="{sid}"' not in ADM.get(B + f'/admin/landing/preview/{P}').text)
        sec['enabled'] = True
        sec['hideOnMobile'] = True
        save([hero, sec])
        pg = ADM.get(B + f'/admin/landing/preview/{P}').text
        if f'id="{sid}"' in pg:
            check(f'{t}: "hide on phones" adds the class', re.search(rf'id="{sid}" class="[^"]*lp-hide-m', pg) is not None)
        for lay in layouts.get(t, []):
            sec['layout'] = lay
            sec['hideOnMobile'] = False
            r = save([hero, sec])
            pg = ADM.get(B + f'/admin/landing/preview/{P}').text
            check(f'{t}: layout {lay}', r.status_code == 200 and (f'id="{sid}"' not in pg or re.search(rf'id="{sid}" class="lp-s lp-s--{lay}', pg) is not None), r.text[:200])
    # hero layouts
    for lay in [s for s in (layouts.get('hero') or [])]:
        h = dict(hero); h['layout'] = lay
        r = save([h])
        check(f'hero: layout {lay}', r.status_code == 200 and f'lp-s--{lay}' in ADM.get(B + f'/admin/landing/preview/{P}').text, r.text[:200])
    # rules
    r = save([{'id': 'faq-x1', 'type': 'faq', 'enabled': True, 'layout': 'accordion', 'background': 'plain', 'hideOnMobile': False, 'content': {'heading': 'x', 'items': []}}, hero])
    check('the hero must come first', r.status_code == 422)
    r = save([hero, {'id': 'zz-1', 'type': 'marquee', 'enabled': True, 'layout': 'x', 'background': 'plain', 'hideOnMobile': False, 'content': {}}])
    check('an unknown section type is refused', r.status_code == 422)
    r = save([hero, {'id': 'cu-1', 'type': 'custom', 'enabled': True, 'layout': 'narrow', 'background': 'plain', 'hideOnMobile': False,
                     'content': {'heading': 'x', 'html': '<p>ok</p><script>alert(1)</script><a href="javascript:alert(1)">x</a>'}}])
    pg = ADM.get(B + f'/admin/landing/preview/{P}').text
    check('custom HTML is cleaned', r.status_code == 200 and '<script>alert' not in pg and 'javascript:alert' not in pg, r.text[:200])
    r = save([hero, {'id': 'g-1', 'type': 'gallery', 'enabled': True, 'layout': 'masonry', 'background': 'plain', 'hideOnMobile': False,
                     'content': {'heading': 'x', 'source': 'custom', 'items': [{'title': 'x', 'image': 'javascript:alert(1)'}]}}])
    check('an unsafe image address is refused', r.status_code == 422)
    # information page: commission sections allowed, course-only refused
    r = post(ADM, '/admin/api/landing/pages', {'kind': 'SITE', 'name': f'Info {uniq()}', 'template': 'platform-showcase'})
    if r.status_code == 200:
        S = r.json()['id']
        sd = get(ADM, f'/admin/api/landing/pages/{S}').json()
        shero = [s for s in sd['config']['sections'] if s['type'] == 'hero'][0]
        for t in ('commission_form', 'commission_pricing', 'cards', 'steps'):
            sid = f'{t.replace("_", "-")}-{uniq()[:6]}'
            cfg = dict(sd['config']); cfg['sections'] = [shero, {'id': sid, 'type': t, 'enabled': True, 'layout': LAYOUTS[t][0], 'background': 'plain', 'hideOnMobile': False,
                                                                 'content': dict({'kicker': '', 'heading': 'Info ' + t}, **SECTION_CONTENT[t])}]
            r2 = put(ADM, f'/admin/api/landing/pages/{S}/draft', {'config': cfg, 'force': True})
            check(f'information page: {t} saved and shown', r2.status_code == 200 and f'id="{sid}"' in ADM.get(B + f'/admin/landing/preview/{S}').text, r2.text[:200])
        cfg = dict(sd['config']); cfg['sections'] = [shero, {'id': 'pr-1', 'type': 'pricing', 'enabled': True, 'layout': 'card', 'background': 'plain', 'hideOnMobile': False, 'content': {'heading': 'x'}}]
        check('information page: a price section is refused', put(ADM, f'/admin/api/landing/pages/{S}/draft', {'config': cfg, 'force': True}).status_code == 422)
        post(ADM, f'/admin/api/landing/pages/{S}/archive')
    else:
        check('information page created', False, r.text)
    post(ADM, f'/admin/api/landing/pages/{P}/archive')


def g_phone():
    t0 = time.time()
    ids = kit()
    pid = ids['phone']
    s = visit(PAGES['phone'])
    st = get(s, f'/landing/checkout/{pid}/state').json()
    check('guest sees the checkout, no account needed', st['user'] is None and st['verify']['mode'] == 'PHONE' and st['verify']['channels'] == ['phone']
          and st['paymentsClosed'] is False and st['choices'], st.get('verify'))
    check('the enrol address opens the page\'s checkout', s.get(B + f'/landing/{PAGES["phone"]}/enroll', allow_redirects=False).headers['Location'].endswith('#enroll'))
    name, email, mob = 'Anika Rao', f'anika.{uniq()}@example.com', phone()
    r = send_code(s, pid, {'channel': 'phone', 'name': name, 'email': email, 'phone': mob})
    j = r.json()
    check('SMS code simulated and shown, number masked', r.status_code == 200 and j['channel'] == 'phone' and j['simulated'] and len(j['code']) == 6 and mob not in j['masked'], j)
    r = post(s, f'/landing/checkout/{pid}/verify/check', {'code': j['code']})
    check('code accepted: signed in, new account', r.status_code == 200 and r.json()['created'] and r.json()['user']['email'] == email, r.text)
    uid = sql(f"select id from users where email = '{email}'")
    check('account: verified mobile, unverified e-mail, real name', sql(f"select concat(phone_verified_at is not null, email_verified_at is null, name) from users where id = {uid}") == f'11{name}')
    o = post(s, f'/landing/checkout/{pid}/order', {'name': name, 'phone': mob, 'terms': True}).json()['orderId']
    r = post(s, f'/landing/checkout/{pid}/pay', {'orderId': o, 'method': 'card'})
    check('simulated Razorpay window for the new buyer', r.status_code == 200 and r.json()['gateway'] == 'razorpay-sim' and r.json()['method'] == 'card', r.text)
    r = post(s, f'/landing/checkout/{pid}/simulate', {'orderId': o, 'outcome': 'success'})
    check('paid through the normal completion (test order)', r.status_code == 200 and sql(f"select concat(payment_status, is_test, payment_provider, payment_method) from orders where id = {o}") == '11razorpaycard')
    res = get(s, f'/landing/checkout/{pid}/state?order={o}').json()['result']
    check('congratulations data', res['state'] == 'success' and res['name'] == name and res['continueUrl'].startswith('/auth/continue?t=') and res['classroom'] == f'/user/course/{o}', res)
    mail = outbox_since(t0, f'/user/course%2F{o}') or outbox_since(t0, f'%2Fuser%2Fcourse%2F{o}')
    check('thank-you e-mail: logo, name, course button to the log-in page', 'assets/images/logo.png' in mail and 'Congratulations, Anika!' in mail and 'Go to my course' in mail
          and '/login?return_to=' in mail, mail[:200])
    check('thank-you e-mail: absolute links', re.search(r'href="https?://[^"]+/login\?return_to=', mail) is not None)
    r = s.get(B + res['continueUrl'], allow_redirects=False)
    check('"Log in to My studio" opens My studio', r.status_code == 302 and r.headers['Location'] == '/dashboard')
    check('the course opens', s.get(B + f'/user/course/{o}', allow_redirects=False).status_code == 200)


def g_email():
    t0 = time.time()
    pid = kit()['email']
    s = visit(PAGES['email'])
    st = get(s, f'/landing/checkout/{pid}/state').json()
    check('e-mail page: e-mail code only', st['verify']['mode'] == 'EMAIL' and st['verify']['channels'] == ['email'], st['verify'])
    email = f'emma.{uniq()}@example.com'
    r = send_code(s, pid, {'channel': 'phone', 'name': 'Emma Das', 'email': email, 'phone': ''})
    j = r.json()
    check('asked for phone, given e-mail (the page decides); no phone needed', r.status_code == 200 and j['channel'] == 'email' and j['simulated'], j)
    check('the address is masked', email not in j['masked'] and j['masked'].endswith('@example.com'))
    check('logged as a simulated e-mail code', sql("select count(*) from auth_events where event = 'email_code_simulated'") != '0')
    r = post(s, f'/landing/checkout/{pid}/verify/check', {'code': j['code']})
    check('signed in; the e-mail is verified', r.status_code == 200 and sql(f"select email_verified_at is not null from users where email = '{email}'") == '1', r.text)
    o = post(s, f'/landing/checkout/{pid}/order', {'name': 'Emma Das', 'phone': '9876512345', 'terms': True}).json()['orderId']
    post(s, f'/landing/checkout/{pid}/pay', {'orderId': o, 'method': 'netbanking'})
    r = post(s, f'/landing/checkout/{pid}/simulate', {'orderId': o, 'outcome': 'success'})
    check('paid', r.status_code == 200 and sql(f"select payment_status from orders where id = {o}") == '1')
    check('thank-you e-mail sent', 'Congratulations, Emma!' in outbox_since(t0, 'Congratulations, Emma!'))


def g_either():
    pid = kit()['either']
    s = visit(PAGES['either'])
    st = get(s, f'/landing/checkout/{pid}/state').json()
    check('both channels offered', st['verify']['channels'] == ['phone', 'email'], st['verify'])
    j = send_code(s, pid, {'channel': 'email', 'name': 'Ira Sen', 'email': f'ira.{uniq()}@example.com', 'phone': ''}).json()
    check('the buyer\'s choice (e-mail) is used', j.get('channel') == 'email', j)
    s2 = visit(PAGES['either'])
    j2 = send_code(s2, pid, {'channel': 'phone', 'name': 'Om Jain', 'email': f'om.{uniq()}@example.com', 'phone': phone()}).json()
    check('… or the mobile', j2.get('channel') == 'phone', j2)
    j3 = send_code(visit(PAGES['either']), pid, {'channel': 'fax', 'name': 'Al B', 'email': f'al.{uniq()}@example.com', 'phone': phone()}).json()
    check('an unknown channel falls back to the first one', j3.get('channel') == 'phone', j3)
    check('a mobile number is required for the SMS channel', post(visit(PAGES['either']), f'/landing/checkout/{pid}/verify/send',
          {'channel': 'phone', 'name': 'Al B', 'email': f'al.{uniq()}@example.com', 'phone': ''}).status_code == 422)


def g_signin():
    pid = kit()['signin']
    s = visit(PAGES['signin'])
    st = get(s, f'/landing/checkout/{pid}/state').json()
    check('"sign in first": no code at Pay, sign-in methods offered', st['verify']['mode'] == 'SIGNIN' and st['verify']['channels'] == [] and st['signIn']['password'] is True, st['verify'])
    r = send_code(s, pid, {'channel': 'phone', 'name': 'X Y', 'email': f'x.{uniq()}@example.com', 'phone': phone()})
    check('codes at Pay refused, sign in instead', r.status_code == 409 and r.json().get('signIn') is True, r.text)
    check('ordering as a guest refused', post(s, f'/landing/checkout/{pid}/order', {'name': 'X', 'terms': True}).status_code == 401)
    email = f'pw.{uniq()}@example.com'
    r = s.post(B + '/register', json={'name': 'Pia Wali', 'email': email, 'password': 'Watercolour#2026', 'password_confirmation': 'Watercolour#2026'}, headers=H(s), allow_redirects=False)
    check('account with a password from the checkout', r.status_code in (200, 302, 409) and get(s, f'/landing/checkout/{pid}/state').json()['user']['email'] == email, r.status_code)
    st = get(s, f'/landing/checkout/{pid}/state').json()
    check('a password account (not a test identity) is told payment opens soon', st['paymentsClosed'] is True)
    post(ADM, '/admin/api/landing/test-payments/simulate-razorpay', {'everyone': True})
    o = post(s, f'/landing/checkout/{pid}/order', {'name': 'Pia Wali', 'phone': phone(), 'terms': True}).json()['orderId']
    r = post(s, f'/landing/checkout/{pid}/pay', {'orderId': o, 'method': 'upi'})
    check('with "every visitor" it can pay', r.status_code == 200 and r.json()['gateway'] == 'razorpay-sim', r.text)
    check('… and completes', post(s, f'/landing/checkout/{pid}/simulate', {'orderId': o, 'outcome': 'success'}).status_code == 200)
    post(ADM, '/admin/api/landing/test-payments/simulate-razorpay', {'everyone': False})


def g_payments():
    s, pid, o, d = guest_buy('phone', outcome='failed')
    check('declined: order failed, no access', sql(f"select payment_status from orders where id = {o}") == '2'
          and get(s, f'/landing/checkout/{pid}/state?order={o}').json()['result']['state'] == 'failed')
    o2 = post(s, f'/landing/checkout/{pid}/order', {'name': d['name'], 'phone': d['phone'], 'terms': True}).json()['orderId']
    check('try again reuses the same order (no duplicates)', o2 == o)
    post(s, f'/landing/checkout/{pid}/pay', {'orderId': o, 'method': 'upi'})
    check('window closed: recorded, order still payable', post(s, f'/landing/checkout/{pid}/failed', {'orderId': o, 'reason': 'closed'}).status_code == 200
          and sql(f"select payment_status from orders where id = {o}") == '0')
    check('a method the page doesn\'t offer is refused', post(s, f'/landing/checkout/{pid}/pay', {'orderId': o, 'method': 'bitcoin'}).status_code == 422)
    check('"test" simulation is only for test buyers', post(s, f'/landing/checkout/{pid}/pay', {'orderId': o, 'method': 'test'}).status_code == 403)
    expected = sql(f"select price from orders where id = {o}")
    post(s, f'/landing/checkout/{pid}/order', {'name': d['name'], 'phone': d['phone'], 'terms': True, 'price': 1, 'total': 1, 'amount': 1})
    check('a price sent by the browser is ignored', sql(f"select price from orders where id = {o}") == expected)
    post(s, f'/landing/checkout/{pid}/pay', {'orderId': o, 'method': 'upi'})
    r = post(s, f'/landing/checkout/{pid}/simulate', {'orderId': o, 'outcome': 'success'})
    check('second try succeeds', r.status_code == 200 and sql(f"select payment_status from orders where id = {o}") == '1')
    check('paid twice? the same order can\'t be simulated again', post(s, f'/landing/checkout/{pid}/simulate', {'orderId': o, 'outcome': 'success'}).status_code == 410)
    check('already enrolled: a new order is refused', post(s, f'/landing/checkout/{pid}/order', {'name': d['name'], 'phone': d['phone'], 'terms': True}).status_code == 409)
    # details checked at order time
    s2 = guest_buy('phone', outcome=None)[0]
    r = post(s2, f'/landing/checkout/{pid}/order', {'name': '', 'phone': 'abc', 'terms': False})
    check('order details validated (name, phone, terms)', r.status_code == 422 and {'name', 'phone', 'terms'} <= set(r.json()['errors']), r.text)
    # coupons
    code = 'REG' + uniq().upper()[:6]
    cr = post(ADM, '/admin/api/landing/coupons', {'code': code, 'description': 'regression', 'kind': 'PERCENT', 'value': '10', 'allPages': True, 'pages': [], 'courses': [],
                                                  'minAmount': '', 'maxUses': '', 'perUserLimit': '1', 'active': True})
    if cr.status_code == 200:
        g = visit(PAGES['phone'])
        q = post(g, f'/landing/checkout/{pid}/quote', {'coupon': code}).json()
        check('coupon previews for a guest', q['quote'].get('coupon') == code and float(q['quote']['discount']) > 0, q)
        check('an unknown coupon is explained', post(g, f'/landing/checkout/{pid}/quote', {'coupon': 'NOPE123'}).json().get('couponError'))
        sb, _, _, db_ = guest_buy('phone', outcome=None)
        oc = post(sb, f'/landing/checkout/{pid}/order', {'name': db_['name'], 'phone': db_['phone'], 'terms': True, 'coupon': code}).json()
        check('coupon applied to the order price', sql(f"select coupon_code from orders where id = {oc['orderId']}") == code
              and float(sql(f"select discount_amount from orders where id = {oc['orderId']}")) > 0, oc)
        post(ADM, f'/admin/api/landing/coupons/{cr.json().get("id", 0)}', {'active': False}) if cr.json().get('id') else None
    else:
        check('coupon created', False, cr.text)


def g_security():
    pid = kit()['phone']
    s = visit(PAGES['phone'])
    r = s.post(B + f'/landing/checkout/{pid}/verify/send', json={'channel': 'phone', 'name': 'No Csrf', 'email': f'n.{uniq()}@example.com', 'phone': phone()},
               headers={'Accept': 'application/json'})
    check('CSRF token required to send a code', r.status_code == 419, r.status_code)
    mob = phone()
    j = send_code(s, pid, {'channel': 'phone', 'name': 'Brute Force', 'email': f'b.{uniq()}@example.com', 'phone': mob}).json()
    wrong = ['000000', '111111', '222222', '333333', '444444']
    codes = [post(s, f'/landing/checkout/{pid}/verify/check', {'code': w if w != j['code'] else '555555'}).status_code for w in wrong]
    check('5 wrong codes lock the code', codes[-1] == 429 and codes[0] == 422, codes)
    check('… even the right one no longer works', post(s, f'/landing/checkout/{pid}/verify/check', {'code': j['code']}).status_code in (410, 429))
    other = visit(PAGES['phone'])
    j2 = send_code(s, pid, {'channel': 'email', 'name': 'Bound Code', 'email': f'bc.{uniq()}@example.com', 'phone': phone()}).json()
    check('a code works only in the browser that asked for it', post(other, f'/landing/checkout/{pid}/verify/check', {'code': j2.get('code', '')}).status_code == 410)
    check('a code for another page is refused', post(s, f'/landing/checkout/{kit()["email"]}/verify/check', {'code': j2.get('code', '')}).status_code == 410)
    # other people's orders
    a, _, oa, _ = guest_buy('phone', outcome=None)
    b, _, ob, _ = guest_buy('phone', outcome=None)
    check('can\'t pay someone else\'s order', post(b, f'/landing/checkout/{pid}/pay', {'orderId': oa, 'method': 'upi'}).status_code == 410)
    check('can\'t simulate someone else\'s order', post(b, f'/landing/checkout/{pid}/simulate', {'orderId': oa, 'outcome': 'success'}).status_code in (403, 410))
    check('can\'t read someone else\'s order status', get(b, f'/landing/checkout/order/{oa}/status').status_code == 404)
    check('another order\'s result isn\'t shown', get(b, f'/landing/checkout/{pid}/state?order={oa}').json().get('result') is None)
    check('still unpaid', sql(f"select payment_status from orders where id = {oa}") == '0')
    # disabled account
    em = f'dis.{uniq()}@example.com'
    sx = visit(PAGES['email'])
    jx = send_code(sx, kit()["email"], {'channel': 'email', 'name': 'Dis Abled', 'email': em, 'phone': ''}).json()
    post(sx, f'/landing/checkout/{kit()["email"]}/verify/check', {'code': jx['code']})
    sql(f"update users set status = 0 where email = '{em}'")
    sy = visit(PAGES['email'])
    time.sleep(31)
    jy = send_code(sy, kit()["email"], {'channel': 'email', 'name': 'Dis Abled', 'email': em, 'phone': ''}).json()
    r = post(sy, f'/landing/checkout/{kit()["email"]}/verify/check', {'code': jy.get('code', '')})
    check('a disabled account can\'t sign in by code', r.status_code == 403, r.text)
    # redirects
    check('/auth/continue with a made-up token goes to log in', requests.get(B + '/auth/continue?t=abc', allow_redirects=False).headers['Location'].startswith('/login'))
    check('log-in return_to never leaves the site', a.get(B + '/login?return_to=https://evil.example', allow_redirects=False).headers['Location'] == '/dashboard')
    check('… nor protocol-relative', a.get(B + '/login?return_to=//evil.example', allow_redirects=False).headers['Location'] == '/dashboard')
    check('the admin API refuses a buyer', get(a, '/admin/api/landing/login').status_code in (401, 403))


def g_returning():
    s, pid, o, d = guest_buy('phone')
    st = get(s, f'/landing/checkout/{pid}/state').json()
    check('owner sees "already enrolled" with a link to the course', st['enrolled'] and st['enrolled']['classroom'] == f'/user/course/{o}', st.get('enrolled'))
    g = visit(PAGES['phone'])
    time.sleep(31)
    j = send_code(g, pid, {'channel': 'phone', 'name': d['name'], 'email': d['email'], 'phone': d['phone']}).json()
    check('a returning buyer (simulated account) gets a code again', j.get('simulated') is True, j)
    r = post(g, f'/landing/checkout/{pid}/verify/check', {'code': j['code']})
    check('… signs in to the same account (nothing new)', r.status_code == 200 and r.json()['created'] is False, r.text)
    check('… and is told they are enrolled', get(g, f'/landing/checkout/{pid}/state').json()['enrolled'] is not None)
    # a real account's number / e-mail never gets a simulated code
    real_phone = phone()
    sql(f"update users set phone_number = '+91{real_phone}', phone_verified_at = '2026-01-01 00:00:00' where email = 'farhan.siddiqui@example.com'")
    r = send_code(visit(PAGES['phone']), pid, {'channel': 'phone', 'name': 'Somebody', 'email': f's.{uniq()}@example.com', 'phone': real_phone})
    check('real account\'s mobile: no simulated code', r.status_code == 409 and 'code' not in r.json() or r.json().get('code') == 'CODE_UNAVAILABLE', r.text)
    r = send_code(visit(PAGES['email']), kit()["email"], {'channel': 'email', 'name': 'Somebody', 'email': 'farhan.siddiqui@example.com', 'phone': ''})
    check('real account\'s e-mail: no simulated code', r.status_code == 409 and r.json().get('code') == 'CODE_UNAVAILABLE', r.text)
    r = send_code(visit(PAGES['phone']), pid, {'channel': 'phone', 'name': 'Somebody', 'email': 'farhan.siddiqui@example.com', 'phone': phone()})
    check('new mobile + a real account\'s e-mail: never attached, log in instead', r.status_code == 409 and r.json().get('code') in ('EMAIL_HAS_ACCOUNT', 'CODE_UNAVAILABLE'), r.text)
    sql("update users set phone_number = null, phone_verified_at = null where email = 'farhan.siddiqui@example.com'")


def g_analytics():
    pid = kit()['phone']
    events = lambda e: int(sql(f"select count(*) from landing_events where landing_page_id = {pid} and event = '{e}'"))
    v0, c0, p0, f0, b0 = events('VIEW'), events('CHECKOUT'), events('PAY_START'), events('PAY_FAIL'), events('PURCHASE')
    s, _, o, _ = guest_buy('phone', outcome='failed')
    check('a page view is counted', events('VIEW') > v0)
    get(s, f'/landing/checkout/{pid}/state')
    check('opening the checkout is counted', events('CHECKOUT') > c0)
    check('pay start and failure are counted', events('PAY_START') > p0 and events('PAY_FAIL') > f0)
    post(s, f'/landing/checkout/{pid}/pay', {'orderId': o, 'method': 'upi'})
    post(s, f'/landing/checkout/{pid}/simulate', {'orderId': o, 'outcome': 'success'})
    check('a test purchase stays out of the funnel', events('PURCHASE') == b0)
    check('test orders stay out of revenue', sql(f"select count(*) from orders where landing_page_id = {pid} and payment_status = 1 and is_test = 0") == '0')
    check('bots aren\'t counted', (lambda n: (requests.get(B + '/landing/' + PAGES['phone'], headers={'User-Agent': 'Googlebot/2.1'}), events('VIEW') == n)[1])(events('VIEW')))
    check('admins aren\'t counted', (lambda n: (ADM.get(B + '/landing/' + PAGES['phone']), events('VIEW') == n)[1])(events('VIEW')))
    r = get(ADM, '/admin/api/landing/overview')
    check('admin overview answers', r.status_code == 200, r.status_code)


def g_admin():
    kit()
    teacher = staff('vaishali@artistikcity.com')
    for path in ('/admin/api/landing/login', '/admin/api/landing/test-payments', '/admin/api/landing/signin', '/admin/api/landing/test-kit'):
        check(f'teacher refused: {path}', get(teacher, path).status_code in (401, 403))     # repeated refusals sign the staff session out
    check('guest refused (401)', requests.get(B + '/admin/api/landing/login', headers={'Accept': 'application/json'}).status_code == 401)
    check('validation: unknown mode', put(ADM, '/admin/api/landing/login', {'checkoutVerify': 'CARRIER_PIGEON'}).status_code == 422)
    r = put(ADM, '/admin/api/landing/login', {'checkoutVerify': 'EITHER'})
    check('default changed and audited', r.status_code == 200 and sql("select count(*) from admin_audit where details like 'Login & verification: verification PHONE -> EITHER%'") != '0', r.text[:200])
    put(ADM, '/admin/api/landing/login', {'checkoutVerify': 'PHONE'})
    v = get(ADM, '/admin/api/landing/login').json()
    check('every test page listed with its own choice', {p['slug']: p['verifyWith'] for p in v['pages'] if p['slug'] in PAGES.values()}
          == {PAGES['phone']: 'PHONE', PAGES['email']: 'EMAIL', PAGES['either']: 'EITHER', PAGES['signin']: 'SIGNIN'})
    check('the admin tab and editor step load', ADM.get(B + '/admin/landing/login').status_code == 200 and ADM.get(B + f'/admin/landing/pages/{kit()["phone"]}/checkout').status_code == 200)
    s, pid, o, _ = guest_buy('phone')
    r = get(ADM, '/admin/api/landing/orders')
    check('the orders list answers', r.status_code == 200, r.status_code)
    r = post(ADM, f'/admin/api/landing/test-payments/orders/{o}/undo')
    check('undo a simulated order: refunded, access removed', r.status_code == 200 and sql(f"select payment_status from orders where id = {o}") == '3', r.text[:200])
    check('… the course no longer opens', s.get(B + f'/user/course/{o}', allow_redirects=False).status_code != 200)
    check('a real order can\'t be undone here', post(ADM, f'/admin/api/landing/test-payments/orders/{sql("select id from orders where coalesce(is_test, 0) = 0 limit 1") or 0}/undo').status_code == 404)


GROUPS = {'kit': g_kit, 'sections': g_sections, 'phone': g_phone, 'email': g_email, 'either': g_either, 'signin': g_signin, 'payments': g_payments,
          'security': g_security, 'returning': g_returning, 'analytics': g_analytics, 'admin': g_admin}
chosen = [a for a in sys.argv[1:] if a in GROUPS] or list(GROUPS)
for name in chosen:
    group = name
    t = time.time()
    try:
        GROUPS[name]()
    except Exception as e:  # a crash in one group fails it but the others still run
        import traceback
        check('group finished', False, traceback.format_exc()[-800:])
    print(f'-- {name}: {time.time() - t:.1f}s')

print(f'\n{"ALL PASSED" if not fails else str(fails) + " FAILED"}')
raise SystemExit(1 if fails else 0)
