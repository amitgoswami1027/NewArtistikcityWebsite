"""Landing Page Management end to end (HTTP + DB). Dev profile (test payments, webhook secret test-wh-secret), fresh database:
    run_app.sh dev   then   python3 tests/system/test_landing.py

Covers
* permissions: guests, students, instructors (own courses only), moderators, per-person grants (Team access) and who may grant;
* pages: create (address rules), draft validation and cleaning, two editors saving at once, preview vs public, publish,
  own /landing/{slug} addresses (generated, unique; never replacing a site page), versions and rollback, unpublish, schedule + the publishing job, duplicate, archive, delete;
* pricing per page: validation, offers with a real end date that end by themselves, one quote for page, checkout and gateway;
* payments per page: custom vs global defaults, only supported / connected providers and methods, checkout shows only what
  the page allows, a method the page doesn't allow is refused, no publishing when nobody could pay, secrets never sent;
* checkout: required fields per page, server-side price (the browser's number is ignored), coupons (page / course / campaign,
  expiry, limits, first-time), paying, enrolment, coupon redemption, already enrolled, failed and retried payments;
* the Razorpay webhook for a landing order: wrong amount refused, right amount paid once (replays don't enrol twice);
* orders & refunds, analytics events and overview, SEO (sitemap, noindex), audit log.
"""
import hashlib, hmac, html, json, os, re, subprocess, time, urllib.parse
from datetime import datetime, timedelta
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
def put(s, path, body=None): return s.put(B + path, json=body or {}, headers=H(s))
def delete(s, path): return s.delete(B + path, headers=H(s))
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'})
def inertia(s, path):
    r = s.get(B + path)
    m = re.search(r'data-page="(\{[^"]+)"', r.text)
    d = json.loads(html.unescape(m.group(1))) if m else {}
    return d.get('component'), d.get('props', {}), r
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
def is_landing(r): return r.status_code == 200 and 'lp-' in r.text and '&quot;component&quot;' not in r.text
APP_NOW = lambda: datetime.strptime(sql("select date_format(date_add(utc_timestamp(), interval 330 minute), '%Y-%m-%d %H:%i:%s')"), '%Y-%m-%d %H:%M:%S')
def ist(minutes): return (APP_NOW() + timedelta(minutes=minutes)).strftime('%Y-%m-%dT%H:%M')    # the app's clock is India time

# fresh state for the parts this test owns
sql("delete from landing_coupon_redemptions"); sql("update landing_coupons set used_count = 0")
sql("update admins set extra_capabilities = null")
adm = staff()

# ================================================================================================= permissions
check('guest: admin API is 401', requests.get(B + '/admin/api/landing/pages', headers={'Accept': 'application/json'}).status_code == 401)
throwaway = student('diya@example.com')          # the security watch signs out a student who calls the admin API
check('student: admin API refused', get(throwaway, '/admin/api/landing/overview').status_code in (401, 403))
check('guest: admin preview refused', requests.get(B + '/admin/landing/preview/1', allow_redirects=False).status_code in (302, 401, 403))

rajat = staff('rajat@artistikcity.com')           # instructor of course 5 only
r = get(rajat, '/admin/api/landing/pages')
check('instructor sees only pages of own courses', r.status_code == 200 and all(x.get('course_id') in (5, None) or x.get('courseId') in (5, None) for x in r.json()['rows']), r.text[:300])
check("instructor can't open another teacher's page", get(rajat, '/admin/api/landing/pages/1').status_code == 403)
check("instructor can't see orders (no grant)", get(rajat, '/admin/api/landing/orders').status_code == 403)
mod = staff('moderator@artistikcity.com')
check('moderator can open pages', get(mod, '/admin/api/landing/pages/1').status_code == 200)
check("moderator can't change payment settings", put(mod, '/admin/api/landing/pages/1/payments', {'mode': 'GLOBAL'}).status_code == 403)
check("moderator can't publish", post(mod, '/admin/api/landing/pages/1/publish').status_code == 403)

# team access (per-person grants)
r = get(adm, '/admin/api/landing/team')
check('team list: personas, grants and roles', r.status_code == 200 and r.json()['canEdit'] and {'content', 'marketing', 'finance', 'publisher'} <= set(r.json()['roles']), r.text[:300])
mod2 = staff('pooja@artistikcity.com')
check('only a super admin can grant', put(mod2, '/admin/api/landing/team/4', {'capabilities': ['landing.orders']}).status_code == 403)
check('unknown permission refused', put(adm, '/admin/api/landing/team/4', {'capabilities': ['setup']}).status_code == 422)
check("super admins can't be changed (they have everything)", put(adm, '/admin/api/landing/team/1', {'capabilities': []}).status_code == 409)
r = put(adm, '/admin/api/landing/team/4', {'capabilities': ['landing.orders', 'landing.refunds']})
check('grant finance to the instructor', r.status_code == 200 and sql("select extra_capabilities from admins where id = 4") == 'landing,landing.orders,landing.refunds', r.text)
check('the grant applies at once (orders open), still own courses only', get(rajat, '/admin/api/landing/orders').status_code == 200)
check('grant is audited', sql("select count(*) from admin_audit where entity = 'admin' and entity_id = 4 and details like 'Landing page permissions%'") != '0')
put(adm, '/admin/api/landing/team/4', {'capabilities': []})
check('grant removed', sql("select coalesce(extra_capabilities, '-') from admins where id = 4") == '-' and get(rajat, '/admin/api/landing/orders').status_code == 403)

# ========================================================================================== create + validation
sql("delete from landing_pages where slug like 'lp-test-%'")
r = post(adm, '/admin/api/landing/pages', {'name': 'x', 'courseId': 1, 'slug': 'watercolour-landscapes-for-beginners', 'template': 'artist-showcase'})
check('address already used by a landing page refused', r.status_code == 422 and 'slug' in r.json()['errors'], r.text)
r = post(adm, '/admin/api/landing/pages', {'name': 'Pencil Sketching Essentials', 'courseId': 3, 'template': 'artist-showcase'})
check("no address typed: generated from the name, under /landing/", r.status_code == 200 and r.json().get('slug') == 'pencil-sketching-essentials', r.text)
gen = r.json().get('id')
r = post(adm, '/admin/api/landing/pages', {'name': 'Pencil Sketching Essentials', 'courseId': 3, 'template': 'artist-showcase'})
check("same name again: the generated address is made unique", r.status_code == 200 and r.json().get('slug') == 'pencil-sketching-essentials-2', r.text)
for x in (gen, r.json().get('id')): adm.delete(B + f'/admin/api/landing/pages/{x}', headers=H(adm))
r = post(adm, '/admin/api/landing/pages', {'name': 'x', 'courseId': 1, 'slug': 'enroll', 'template': 'artist-showcase'})
check('reserved address refused', r.status_code == 422 and 'slug' in r.json()['errors'], r.text)
r = post(adm, '/admin/api/landing/pages', {'name': '', 'courseId': '', 'slug': 'Bad Slug!', 'template': 'nope'})
check('missing name / course / template, bad slug: one message each', r.status_code == 422 and {'name', 'courseId', 'slug', 'template'} <= set(r.json()['errors']), r.text)
check('instructor may not create pages', post(rajat, '/admin/api/landing/pages', {'name': 'x', 'courseId': 5, 'slug': 'lp-test-x', 'template': 'artist-showcase'}).status_code == 403)

r = post(adm, '/admin/api/landing/pages', {'name': 'Diwali campaign', 'courseId': 1, 'slug': 'lp-test-diwali', 'campaign': 'Diwali 2026', 'template': 'conversion-sales'})
check('campaign page created as a draft', r.status_code == 200, r.text)
P = r.json()['id']
d = get(adm, f'/admin/api/landing/pages/{P}').json()
cfg = d['config']
check('new page: hero first, template sections, design and SEO defaults', cfg['sections'][0]['type'] == 'hero' and len(cfg['sections']) >= 8 and cfg['design']['accent'] == '#2F6B4F'
      and cfg['seo']['index'] is True, [s['type'] for s in cfg['sections']])
check('the page has its own address, shown in the editor', d['page']['publicUrl'] == '/landing/lp-test-diwali' and d['page']['fullUrl'].endswith('/landing/lp-test-diwali') and d['page']['status'] == 'DRAFT')
check('draft is not public', requests.get(B + '/landing/lp-test-diwali').status_code == 404)
check('admin preview renders the draft', is_landing(adm.get(B + f'/admin/landing/preview/{P}')))

bad = json.loads(json.dumps(cfg))
bad['design']['accent'] = 'red'
bad['sections'][0], bad['sections'][1] = bad['sections'][1], bad['sections'][0]
bad['seo']['canonical'] = 'javascript:alert(1)'
bad['marketing']['ga4'] = 'UA-123'
r = put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': bad, 'savedAt': d['page']['draft_saved_at']})
e = r.json().get('errors', {})
check('draft validation: colour, hero first, link, tracking id', r.status_code == 422 and {'design.accent', 'sections', 'seo.canonical', 'marketing.ga4'} <= set(e), e)

good = json.loads(json.dumps(cfg))
good['sections'][0]['content']['heading'] = 'Paint your Diwali lights'
good['sections'].append({'id': 'custom-zz1', 'type': 'custom', 'enabled': True, 'layout': 'single', 'background': 'plain', 'hideOnMobile': False,
                         'content': {'heading': 'A note', 'html': '<p>Hello <b>there</b></p><script>alert(1)</script><img src=x onerror=alert(1)>'}})
good['sections'].append({'id': 'faq-zz2', 'type': 'faq', 'enabled': True, 'layout': 'accordion', 'background': 'plain', 'hideOnMobile': False, 'content': {'heading': 'More', 'items': []}})
r = put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': good, 'savedAt': d['page']['draft_saved_at']})
check('a second FAQ section is refused (some sections only once)', r.status_code == 422 and any(k.endswith('.type') for k in r.json().get('errors', {})), r.text[:300])
good['sections'].pop()
r = put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': good, 'savedAt': d['page']['draft_saved_at']})
check('draft saved', r.status_code == 200, r.text[:300])
saved_at = r.json().get('savedAt')
custom = [s for s in r.json()['config']['sections'] if s['type'] == 'custom'][0]['content']['html']
check('custom HTML is cleaned (no script, no event handlers)', '<script' not in custom and 'onerror' not in custom and '<b>there</b>' in custom, custom)
r = put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': good, 'savedAt': '2000-01-01 00:00:00'})
check('someone else saved meanwhile: 409 with their name', r.status_code == 409 and 'Site Admin' in r.json().get('message', ''), r.text)
r = put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': good, 'savedAt': '2000-01-01 00:00:00', 'force': True})
check('"save anyway" works', r.status_code == 200)
r = put(adm, f'/admin/api/landing/pages/{P}/template', {'template': 'premium-masterclass'})
d2 = r.json().get('data', {})
check('switching template keeps the texts', r.status_code == 200 and d2['page']['template_key'] == 'premium-masterclass'
      and d2['config']['sections'][0]['content']['heading'] == 'Paint your Diwali lights' and d2['config']['design']['font'] == 'serif', r.text[:300])

# ============================================================================================== payments per page
r = put(adm, f'/admin/api/landing/pages/{P}/payments', {'mode': 'CUSTOM', 'defaultProvider': 'razorpay',
        'providers': [{'key': 'razorpay', 'enabled': True, 'priority': 1}, {'key': 'paypal', 'enabled': False, 'priority': 2}],
        'methods': [{'key': 'upi', 'enabled': True}, {'key': 'paypal', 'enabled': True}]})
check('a method no switched-on provider supports is refused', r.status_code == 422 and 'methods.paypal' in r.json()['errors'], r.text)
r = put(adm, f'/admin/api/landing/pages/{P}/payments', {'mode': 'CUSTOM', 'defaultProvider': 'razorpay',
        'providers': [{'key': 'razorpay', 'enabled': True, 'priority': 1}, {'key': 'cashfree', 'enabled': True, 'priority': 2}], 'methods': [{'key': 'upi', 'enabled': True}]})
check("a provider that isn't connected can't be switched on", r.status_code == 422 and 'providers.cashfree' in r.json()['errors'], r.text)
r = put(adm, f'/admin/api/landing/pages/{P}/payments', {'mode': 'CUSTOM', 'defaultProvider': 'razorpay',
        'providers': [{'key': 'razorpay', 'enabled': True, 'priority': 1}], 'methods': [{'key': 'upi', 'enabled': False}]})
check('custom settings without any way to pay are refused', r.status_code == 422 and 'methods' in r.json()['errors'], r.text[:300])
sql(f"update landing_payment_settings set mode = 'CUSTOM' where landing_page_id = {P}")
sql(f"update landing_payment_methods set enabled = 0 where landing_page_id = {P}")          # e.g. left from an older release
r = post(adm, f'/admin/api/landing/pages/{P}/publish')
check('a page nobody could pay on is not published (server check)', r.status_code == 422 and 'payments' in r.json()['errors'], r.text[:300])
check('...nor scheduled', post(adm, f'/admin/api/landing/pages/{P}/schedule', {'publishAt': ist(60)}).status_code == 422)
r = put(adm, f'/admin/api/landing/pages/{P}/payments', {'mode': 'CUSTOM', 'defaultProvider': 'razorpay',
        'providers': [{'key': 'razorpay', 'enabled': True, 'priority': 1}], 'methods': [{'key': 'upi', 'enabled': True}, {'key': 'upi_qr', 'enabled': True}]})
check('custom: UPI only on the campaign page', r.status_code == 200 and [m['key'] for m in r.json()['payments']['methods'] if m['live']] == ['upi', 'upi_qr'], r.text[:300])
check('page 1 is unaffected', sql("select group_concat(method order by method) from landing_payment_methods where landing_page_id = 1 and enabled = 1")
      == 'credit_card,debit_card,gpay,intl_cards,netbanking,paypal,paytm,phonepe,upi,upi_intent,upi_qr')
check('payment settings change is audited', sql(f"select count(*) from admin_audit where entity = 'landing_page' and entity_id = {P} and details like 'Payment settings%'") != '0')
ov = get(adm, '/admin/api/landing/payments')
check('payments overview: no secret ever sent', ov.status_code == 200 and 'test-wh-secret' not in ov.text and 'fake_secret' not in ov.text and 'secret' not in json.dumps(ov.json().get('defaults', {})).lower(), ov.text[:200])

# ============================================================================================ publish lifecycle
r = post(adm, f'/admin/api/landing/pages/{P}/publish', {'note': 'Launch'})
check('published', r.status_code == 200 and r.json()['data']['page']['status'] == 'PUBLISHED', r.text[:300])
pub = requests.get(B + '/landing/lp-test-diwali')
check('public page live with the draft heading', is_landing(pub) and 'Paint your Diwali lights' in pub.text)
check('version 1 kept with its note', sql(f"select concat(version_no, ':', note) from landing_page_versions where landing_page_id = {P}") == '1:Launch')
check('sitemap lists the campaign page', '/landing/lp-test-diwali<' in requests.get(B + '/sitemap.xml').text)

g2 = get(adm, f'/admin/api/landing/pages/{P}').json()
c2 = g2['config']; c2['sections'][0]['content']['heading'] = 'Second headline'
r = put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': c2, 'savedAt': g2['page']['draft_saved_at']})
check('editing the draft does not change the live page', r.status_code == 200 and r.json()['hasUnpublishedChanges'] and 'Paint your Diwali lights' in requests.get(B + '/landing/lp-test-diwali').text)
post(adm, f'/admin/api/landing/pages/{P}/publish')
check('publishing again makes v2 live', 'Second headline' in requests.get(B + '/landing/lp-test-diwali').text)
v1 = sql(f"select id from landing_page_versions where landing_page_id = {P} and version_no = 1")
r = post(adm, f'/admin/api/landing/pages/{P}/versions/{v1}/restore')
check('roll back to v1 = published as v3', r.status_code == 200 and 'Paint your Diwali lights' in requests.get(B + '/landing/lp-test-diwali').text
      and sql(f"select max(version_no) from landing_page_versions where landing_page_id = {P}") == '3')
check('admin can preview any version', 'Second headline' in adm.get(B + f'/admin/landing/preview/{P}?version=' + sql(f"select id from landing_page_versions where landing_page_id = {P} and version_no = 2")).text)

g3 = get(adm, f'/admin/api/landing/pages/{P}').json(); c3 = g3['config']; c3['seo']['index'] = False
put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': c3, 'savedAt': g3['page']['draft_saved_at']}); post(adm, f'/admin/api/landing/pages/{P}/publish')
pub = requests.get(B + '/landing/lp-test-diwali')
check('"hide from search": noindex and out of the sitemap', 'noindex' in pub.text and '/landing/lp-test-diwali<' not in requests.get(B + '/sitemap.xml').text)

# optional lead capture: off by default; on = the newsletter box with consent, tagged with the page
check('lead box only when switched on', 'id="lp-lead"' not in pub.text)
g4 = get(adm, f'/admin/api/landing/pages/{P}').json(); c4 = g4['config']; c4['marketing']['leadCapture'] = True; c4['seo']['index'] = True
put(adm, f'/admin/api/landing/pages/{P}/draft', {'config': c4, 'savedAt': g4['page']['draft_saved_at']}); post(adm, f'/admin/api/landing/pages/{P}/publish')
v0 = requests.Session(); pub = v0.get(B + '/landing/lp-test-diwali')
check('lead box shown with a consent tick', 'id="lp-lead"' in pub.text and 'name="consent"' in pub.text)
tok = re.search(r'name="csrf-token" content="([^"]+)"', pub.text)
r = v0.post(B + '/api/newsletter', json={'email': 'lead.test@example.com', 'source': f'landing:{P}'},
            headers={'X-CSRF-TOKEN': tok.group(1) if tok else '', 'Accept': 'application/json', 'X-XSRF-TOKEN': xsrf(v0)})
check('sign-up stored, tagged with the page', r.status_code == 200 and sql("select source from newsletters where email = 'lead.test@example.com'") == f'landing:{P}', r.text)
r = v0.post(B + '/api/newsletter', json={'email': 'lead.other@example.com', 'source': "landing:1' or 1=1"}, headers={'X-CSRF-TOKEN': tok.group(1) if tok else '', 'Accept': 'application/json', 'X-XSRF-TOKEN': xsrf(v0)})
check('a made-up source falls back to "footer"', sql("select source from newsletters where email = 'lead.other@example.com'") == 'footer')

# landing pages never replace the site's pages: page 1 lives at /landing/<slug>, the course page stays as it is
check('page 1 is at /landing/watercolour-landscapes-for-beginners', is_landing(requests.get(B + '/landing/watercolour-landscapes-for-beginners')))
comp, _, _ = inertia(requests.Session(), '/course/watercolour-landscapes-for-beginners')
check('the regular course page is untouched', comp == 'CourseDetails', comp)
post(adm, '/admin/api/landing/pages/1/unpublish')
check('unpublished: its address is a 404', requests.get(B + '/landing/watercolour-landscapes-for-beginners').status_code == 404)
post(adm, '/admin/api/landing/pages/1/publish', {'note': 'Back'})
check('published again', is_landing(requests.get(B + '/landing/watercolour-landscapes-for-beginners')))
r = requests.get(B + '/course/watercolour-landscapes-for-beginners/enroll', allow_redirects=False)
check('old checkout links go to the landing checkout', r.status_code in (301, 302) and r.headers.get('Location', '').endswith('/landing/watercolour-landscapes-for-beginners/enroll'), r.headers.get('Location'))

# duplicate, archive, restore, delete
r = post(adm, f'/admin/api/landing/pages/{P}/duplicate')
D = r.json().get('id')
dd = get(adm, f'/admin/api/landing/pages/{D}').json()
check('duplicate: a draft copy with its own address and the same settings', r.status_code == 200 and dd['page']['status'] == 'DRAFT' and dd['page']['slug'] != 'lp-test-diwali'
      and [m['key'] for m in dd['payments']['methods'] if m['enabled']] == ['upi', 'upi_qr'], dd['page'])
check('schedule in the past refused', post(adm, f'/admin/api/landing/pages/{D}/schedule', {'publishAt': ist(-5)}).status_code == 422)
r = post(adm, f'/admin/api/landing/pages/{D}/schedule', {'publishAt': ist(30)})
check('scheduled', r.status_code == 200 and sql(f"select status from landing_pages where id = {D}") == 'SCHEDULED', r.text)
sql(f"update landing_pages set publish_at = '2000-01-01 00:00:00' where id = {D}")
for _ in range(40):
    if sql(f"select status from landing_pages where id = {D}") == 'PUBLISHED': break
    time.sleep(2)
check('the publishing job puts a due page live (with a version)', sql(f"select status from landing_pages where id = {D}") == 'PUBLISHED'
      and sql(f"select count(*) from landing_page_versions where landing_page_id = {D}") == '1')
check("a live page can't be deleted", delete(adm, f'/admin/api/landing/pages/{D}').status_code == 409)
post(adm, f'/admin/api/landing/pages/{D}/unpublish'); post(adm, f'/admin/api/landing/pages/{D}/archive')
check('archived: off the site', sql(f"select status from landing_pages where id = {D}") == 'ARCHIVED' and requests.get(B + '/landing/' + dd['page']['slug']).status_code == 404)
check('archived page must be restored before publishing', post(adm, f'/admin/api/landing/pages/{D}/publish').status_code == 409)
post(adm, f'/admin/api/landing/pages/{D}/restore')
check('a page nobody bought through can be deleted', delete(adm, f'/admin/api/landing/pages/{D}').status_code == 200 and sql(f"select count(*) from landing_pages where id = {D}") == '0')

# ================================================================================================= pricing
check('original below the price refused', put(adm, f'/admin/api/landing/pages/{P}/pricing', {'price': 3500, 'listPrice': 3000}).status_code == 422)
r = put(adm, f'/admin/api/landing/pages/{P}/pricing', {'price': 3500, 'promoPrice': 2999})
check('an offer without an end date refused (no endless countdowns)', r.status_code == 422 and 'promoEndsAt' in r.json()['errors'])
check('offer above the price refused', 'promoPrice' in put(adm, f'/admin/api/landing/pages/{P}/pricing', {'price': 3500, 'promoPrice': 3600, 'promoEndsAt': ist(600)}).json().get('errors', {}))
check('tax over 50% refused', 'taxRate' in put(adm, f'/admin/api/landing/pages/{P}/pricing', {'price': 3500, 'taxRate': 60}).json().get('errors', {}))
r = put(adm, f'/admin/api/landing/pages/{P}/pricing', {'price': 3500, 'listPrice': 4999, 'promoLabel': 'Diwali offer', 'promoPrice': 2999, 'promoEndsAt': ist(60 * 48),
        'taxMode': 'INCLUDED', 'taxRate': 18, 'taxLabel': 'GST', 'couponsEnabled': True, 'inclusions': ['Eight live classes', 'Recordings']})
q = r.json().get('quote', {})
check('pricing saved: the offer is what buyers see', r.status_code == 200 and q.get('totalLabel') == '₹2,999' and q.get('listLabel') == '₹4,999' and q.get('offerEndsLabel'), r.text[:300])
pub = requests.get(B + '/landing/lp-test-diwali')
check('public page shows the offer price and its real end', '₹2,999' in pub.text and 'Diwali offer' in pub.text)
check('page 1 keeps its own price', '₹3,999' in requests.get(B + '/landing/watercolour-landscapes-for-beginners').text)
check('pricing change audited', sql(f"select count(*) from admin_audit where entity = 'landing_page' and entity_id = {P} and details = 'Pricing'") != '0')

# ================================================================================================= checkout
diya = student('diya@example.com')
diya.get(B + '/landing/lp-test-diwali?utm_source=newsletter&utm_campaign=diwali')   # visitor cookie + attribution
comp, props, _ = inertia(diya, '/landing/lp-test-diwali/enroll')
check('checkout page: only the methods this page allows', comp == 'Landing/Checkout' and [c['key'] for c in props.get('choices', [])] == ['upi'], [c.get('key') for c in props.get('choices', [])])
check('checkout uses the page price', props.get('quote', {}).get('totalLabel') == '₹2,999')
comp1, props1, _ = inertia(diya, '/landing/watercolour-landscapes-for-beginners/enroll')
check('page 1 checkout offers its own methods', [c['key'] for c in props1.get('choices', [])] == ['upi', 'card', 'netbanking', 'paypal'], [c.get('key') for c in props1.get('choices', [])])
check('guest: checkout API needs sign-in', requests.post(B + f'/landing/checkout/{P}/order', json={}).status_code in (401, 419))

r = post(diya, f'/landing/checkout/{P}/order', {'name': '', 'phone': '12', 'terms': False})
check('required fields: name, phone, terms', r.status_code == 422 and {'name', 'phone', 'terms'} <= set(r.json()['errors']), r.text)

# coupons
sql("delete from landing_coupons where code like 'LPT%'")
r = post(adm, '/admin/api/landing/coupons', {'code': 'LPTDIWALI', 'kind': 'PERCENT', 'value': 10, 'campaign': 'Diwali 2026', 'pages': [P], 'perUserLimit': 1, 'maxUses': 5, 'expiresAt': ist(60 * 24)})
check('page coupon created', r.status_code == 200, r.text)
check('coupon codes are unique', post(adm, '/admin/api/landing/coupons', {'code': 'lptdiwali', 'kind': 'FLAT', 'value': 100, 'allPages': True}).status_code == 422)
check('percent over 100 refused', post(adm, '/admin/api/landing/coupons', {'code': 'LPTBIG', 'kind': 'PERCENT', 'value': 150, 'allPages': True}).status_code == 422)
post(adm, '/admin/api/landing/coupons', {'code': 'LPTOLD', 'kind': 'FLAT', 'value': 100, 'allPages': True, 'expiresAt': '2020-01-01T00:00'})
post(adm, '/admin/api/landing/coupons', {'code': 'LPTMIN', 'kind': 'FLAT', 'value': 100, 'allPages': True, 'minAmount': 9000})
r = post(diya, f'/landing/checkout/1/quote', {'coupon': 'LPTDIWALI'})
check('page coupon does not apply on another page', r.status_code == 200 and r.json()['couponError'], r.text)
check('expired coupon explained', post(diya, f'/landing/checkout/{P}/quote', {'coupon': 'LPTOLD'}).json()['couponError'])
check('minimum amount explained', post(diya, f'/landing/checkout/{P}/quote', {'coupon': 'LPTMIN'}).json()['couponError'])
check('inactive coupon explained', post(diya, f'/landing/checkout/{P}/quote', {'coupon': 'SUMMER25'}).json()['couponError'])
r = post(diya, f'/landing/checkout/{P}/quote', {'coupon': 'lptdiwali'})
check('coupon applies (code is case-insensitive): 10% off 2,999 = 300 off, whole rupees', r.json()['couponError'] is None and r.json()['quote']['totalLabel'] == '₹2,699', r.text)

r = post(diya, f'/landing/checkout/{P}/order', {'name': 'Diya Patel', 'phone': '+91 98765 43210', 'terms': True, 'coupon': 'LPTDIWALI', 'price': 1, 'total': 1})
check('order opened at the server price (the browser number is ignored)', r.status_code == 200, r.text)
oid = r.json()['orderId']
check('order keeps page, coupon, amounts, phone and UTM', sql(f"select concat_ws('|', landing_page_id, coupon_code, price, list_amount, discount_amount, billing_phone, utm_source, utm_campaign) from orders where id = {oid}")
      == f'{P}|LPTDIWALI|2699.00|4999.00|300.00|+919876543210|newsletter|diwali', sql(f"select concat_ws('|', landing_page_id, coupon_code, price, list_amount, discount_amount, billing_phone, utm_source, utm_campaign) from orders where id = {oid}"))
check("phone isn't written to the sign-in phone", sql("select coalesce(phone_number, '-') from users where id = 2") in ('-', ''))
r = post(diya, f'/landing/checkout/{P}/pay', {'orderId': oid, 'method': 'card'})
check('a method this page does not offer is refused', r.status_code == 422, r.text)
r = post(diya, f'/landing/checkout/{P}/pay', {'orderId': oid, 'method': 'upi'})
check('UPI accepted (test mode: no keys)', r.status_code == 200 and r.json()['gateway'] == 'test' and float(r.json()['amount']) == 2699, r.text)
check('payment start recorded with method and provider', sql(f"select concat(payment_provider, '/', payment_method) from orders where id = {oid}") == 'razorpay/upi')
r = post(diya, '/checkout/test-pay', {'orderId': oid})
check('paid', r.status_code == 200 and sql(f"select payment_status from orders where id = {oid}") == '1', r.text)
comp_c, _, _ = inertia(diya, f'/user/course/{oid}')
check('the buyer can open the course', comp_c == 'Course/Overview', comp_c)
check('coupon redeemed once', sql("select used_count from landing_coupons where code = 'LPTDIWALI'") == '1' and sql(f"select count(*) from landing_coupon_redemptions where order_id = {oid}") == '1')
check('purchase in the funnel', sql(f"select count(*) from landing_events where landing_page_id = {P} and event = 'PURCHASE' and order_id = {oid}") == '1')
_, done, _ = inertia(diya, f'/landing/lp-test-diwali/enroll/done?order_id={oid}')
check('confirmation screen says paid', (done.get('result') or {}).get('state') in ('paid', 'success'), done.get('result'))
check('already enrolled: a new order is refused', post(diya, f'/landing/checkout/{P}/order', {'name': 'Diya', 'phone': '9876543210', 'terms': True}).status_code == 409)
check('...also on page 1 (same course)', post(diya, '/landing/checkout/1/order', {'name': 'Diya', 'phone': '9876543210', 'terms': True}).status_code == 409)
check('per-user coupon limit: the same buyer can\'t use it again', post(diya, f'/landing/checkout/{P}/quote', {'coupon': 'LPTDIWALI'}).json()['couponError'])

# failed, then retried; first-time coupon for a returning buyer
ishita = student('ishita.bose@example.com')
check('first-time offer applies to someone who never bought', post(ishita, '/landing/checkout/1/quote', {'coupon': 'FIRSTCOURSE'}).json()['couponError'] is None)
farhan = student('farhan.siddiqui@example.com')
check('first-time offer refused for a returning buyer', post(farhan, '/landing/checkout/1/quote', {'coupon': 'FIRSTCOURSE'}).json()['couponError'])
r = post(ishita, f'/landing/checkout/{P}/order', {'name': 'Ishita Bose', 'phone': '9876500000', 'terms': True})
o2 = r.json()['orderId']
post(ishita, f'/landing/checkout/{P}/pay', {'orderId': o2, 'method': 'upi'})
post(ishita, f'/landing/checkout/{P}/failed', {'orderId': o2, 'reason': 'declined'})
st = get(ishita, f'/landing/checkout/order/{o2}/status').json()
check('declined payment: order failed, nothing enrolled', sql(f"select payment_status from orders where id = {o2}") == '2' and st.get('state') == 'failed'
      and sql("select count(*) from enrollments where user_id = 3 and course_id = 1") == '0', st)
check("another user's order status is private", get(diya, f'/landing/checkout/order/{o2}/status').status_code == 404)
r = post(ishita, f'/landing/checkout/{P}/order', {'name': 'Ishita Bose', 'phone': '9876500000', 'terms': True})
check('trying again reopens the same order', r.status_code == 200 and r.json()['orderId'] == o2 and sql(f"select payment_status from orders where id = {o2}") == '0')

# price changes while the buyer is paying
sql(f"update landing_pricing set promo_ends_at = '2000-01-02 00:00:00', promo_starts_at = '2000-01-01 00:00:00' where landing_page_id = {P}")
r = post(ishita, f'/landing/checkout/{P}/pay', {'orderId': o2, 'method': 'upi'})
check('the offer ended: the pay step re-prices (409) instead of charging the old price', r.status_code == 409 and r.json().get('repriced') and float(sql(f"select price from orders where id = {o2}")) == 3500, r.text)
check('...and the page shows the regular price by itself', '₹3,500' in requests.get(B + '/landing/lp-test-diwali').text and '2,999' not in requests.get(B + '/landing/lp-test-diwali').text)

# =============================================================================================== webhook
r = post(ishita, f'/landing/checkout/{P}/pay', {'orderId': o2, 'method': 'upi'})
sql(f"update orders set gateway_ref = 'order_LPTEST{o2}' where id = {o2}")
def hook(amount, pid):
    body = json.dumps({'event': 'payment.captured', 'payload': {'payment': {'entity': {'id': pid, 'order_id': f'order_LPTEST{o2}', 'amount': amount, 'currency': 'INR', 'method': 'upi'}}}})
    sig = hmac.new(b'test-wh-secret', body.encode(), hashlib.sha256).hexdigest()
    return requests.post(B + '/api/v1/courses/razorpay-webhook', data=body, headers={'X-Razorpay-Signature': sig, 'Content-Type': 'application/json'})
hook(100, 'pay_LPTWRONG')
check('webhook with the wrong amount does not mark the order paid', sql(f"select payment_status from orders where id = {o2}") == '0')
r = hook(350000, 'pay_LPTRIGHT')
check('webhook with the amount fixed on the order: paid', r.status_code == 200 and sql(f"select payment_status from orders where id = {o2}") == '1', r.text)
check('...course open for the buyer', inertia(ishita, f'/user/course/{o2}')[0] == 'Course/Overview')
hook(350000, 'pay_LPTRIGHT'); hook(350000, 'pay_LPTRIGHT')
check('replayed webhooks change nothing (one payment log, one paid order)', sql("select count(*) from orders where user_id = 3 and course_id = 1 and payment_status = 1") == '1'
      and sql(f"select count(*) from payment_logs where order_id = {o2} and payment_status = 1") in ('0', '1')
      and sql(f"select count(*) from landing_events where order_id = {o2} and event = 'PURCHASE'") in ('0', '1'))

# ================================================================================================ orders, refunds
r = get(adm, f'/admin/api/landing/orders?page={P}&range=30d')
rows = r.json()['rows'] if r.status_code == 200 else []
check('orders of this page with method, provider, coupon and status', any(o['id'] == oid and o['payment_method'] == 'upi' and o['payment_provider'] == 'razorpay' and o['coupon_code'] == 'LPTDIWALI'
      and o['status'] == 'successful' and o['enrollment'] == 'Enrolled' for o in rows), rows[:2])
check('filter by method', all(o['payment_method'] == 'paypal' for o in get(adm, f'/admin/api/landing/orders?method=paypal&range=30d').json()['rows']))
check("moderator can't see orders", get(mod, '/admin/api/landing/orders').status_code == 403)
check('refund needs a reason', post(adm, f'/admin/api/landing/orders/{oid}/refund', {'note': ''}).status_code == 422)
r = post(adm, f'/admin/api/landing/orders/{oid}/refund', {'note': 'Customer asked before the first class'})
check('refund sent through the refunds queue (test gateway)', r.status_code == 200 and sql(f"select status from payment_refunds where source = 'COURSE' and source_ref = '{oid}'") in ('DONE', 'SUCCEEDED', 'REFUNDED', 'COMPLETED'), r.text)
check('refunded order shows as refunded', sql(f"select payment_status from orders where id = {oid}") == '3')
check('a refunded order is not refunded twice', post(adm, f'/admin/api/landing/orders/{oid}/refund', {'note': 'again'}).status_code == 409)
check("a page people bought through can't be deleted", post(adm, f'/admin/api/landing/pages/{P}/unpublish').status_code == 200 and delete(adm, f'/admin/api/landing/pages/{P}').status_code == 409)
post(adm, f'/admin/api/landing/pages/{P}/publish')

# ================================================================================================ analytics
v = requests.Session(); v.get(B + '/landing/lp-test-diwali')
before = int(sql(f"select count(*) from landing_events where landing_page_id = {P} and event = 'CTA'"))
r = v.post(B + '/landing/e', json={'event': 'CTA', 'page': P}, headers=H(v))
check('CTA click recorded (204, no personal data)', r.status_code == 204 and int(sql(f"select count(*) from landing_events where landing_page_id = {P} and event = 'CTA'")) == before + 1)
v.post(B + '/landing/e', json={'event': 'METHOD', 'page': P, 'method': '<script>'}, headers=H(v))
check('junk method ignored', sql(f"select count(*) from landing_events where method like '%<%'") == '0')
check('visitor id is a hash, never an e-mail', sql("select count(*) from landing_events where visitor like '%@%'") == '0')
r = get(adm, f'/admin/api/landing/overview?range=30d&page={P}')
k = r.json().get('kpis', {}) if r.status_code == 200 else {}
check('overview for the page: views, the real (non-test) sale and its revenue; test payments left out', k.get('views', 0) > 0 and k.get('successful') == 1
      and float(k.get('revenueInr', 0)) == 3500 and k.get('enrollments') == 1, k)
check('overview has a daily series for the charts', len(r.json().get('series', [])) == 30)

print(f'\n{"ALL PASSED" if not fails else str(fails) + " FAILED"}')
raise SystemExit(1 if fails else 0)
