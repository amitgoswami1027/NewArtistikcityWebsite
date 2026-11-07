"""Landing Page Management: own addresses, information pages and the three showcase pages (HTTP + DB). Dev profile, fresh database:
    run_app.sh dev   then   python3 tests/system/test_landing_showcase.py

Covers
* LandingShowcase: the three pages were made through the module once (recorded in settings), as published versions;
  the existing Watercolour page got a new version and its earlier version stays restorable;
* every landing page has its own address /landing/{slug} (generated from the name, unique); /how-it-works,
  /course/watercolour-landscapes-for-beginners and /commission/step-1 stay the original pages; information pages (no
  course): course-only sections and missing button addresses refused, templates must match the page type, instructors
  can't see or make them, no checkout;
* content rules: SEO title / description / canonical / Open Graph / structured data, sitemap, alt text, no invented
  claims, commission "from" prices equal to the commission price list, courses and artists from the database;
* analytics: CTA events are recorded for site pages too.
"""
import html, json, os, re, subprocess, urllib.parse
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
def get(s, path): return s.get(B + path, headers={'Accept': 'application/json'})
def component(path):
    r = requests.get(B + path)
    m = re.search(r'data-page="(\{[^"]+)"', r.text)
    return (json.loads(html.unescape(m.group(1))).get('component') if m else None), r
def staff(email='admin@artistikcity.com'):
    s = requests.Session(); h = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', h).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False); assert r.status_code == 302, email
    s.get(B + '/admin/dashboard'); return s
def is_landing(r): return r.status_code == 200 and 'class="lp ' in r.text and '&quot;component&quot;' not in r.text
def text_of(r): return html.unescape(re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', re.sub(r'<(script|style|svg)[\s\S]*?</\1>', ' ', r.text))))

adm = staff()
HIW = int(sql("select id from landing_pages where slug = 'how-it-works'") or 0)
COM = int(sql("select id from landing_pages where slug = 'commission'") or 0)
WC = int(sql("select id from landing_pages where slug = 'watercolour-landscapes-for-beginners'") or 0)

# ================================================================================================ installation
flag = json.loads(sql("select meta_value from settings where meta_tag = 'landing_pages' and meta_key = 'showcase_v1'") or '{}')
check('installed once, outcome recorded', sql("select count(*) from settings where meta_tag = 'landing_pages' and meta_key = 'showcase_v1'") == '1', flag)
check('all three published', [flag.get(k, {}).get('status') for k in ('how-it-works', 'watercolour', 'commission')] == ['published'] * 3, flag)
check('information pages: kind SITE, no course, the right templates',
      sql(f"select group_concat(concat(kind, ':', coalesce(course_id, 'null'), ':', template_key) order by id) from landing_pages where id in ({HIW}, {COM})")
      == 'SITE:null:platform-showcase,SITE:null:commission-studio')
check('Watercolour: still the course page of course 1, new template', sql(f"select concat(kind, ':', course_id, ':', template_key) from landing_pages where id = {WC}") == 'COURSE:1:watercolour-wash')
check('Watercolour: published as a new version, the old one kept', sql(f"select group_concat(version_no order by version_no) from landing_page_versions where landing_page_id = {WC}") == '1,2'
      and 'installed automatically' in sql(f"select note from landing_page_versions where landing_page_id = {WC} and version_no = 2"))
check('nothing deleted: seed page 2 untouched', sql("select concat(slug, ':', status) from landing_pages where id = 2") == 'acrylic-portrait-festive-masterclass:DRAFT')

# ================================================================================================ public pages
# the site's own pages are untouched: landing pages never take over an address
comp, _ = component('/how-it-works')
check('/how-it-works is the original page', comp == 'HowItWorks', comp)
comp, _ = component('/course/watercolour-landscapes-for-beginners')
check('/course/watercolour-landscapes-for-beginners is the original course page', comp == 'CourseDetails', comp)
comp, _ = component('/commission/step-1')
check('/commission/step-1 is the original wizard step 1', comp == 'Commission/Step1Theme', comp)

r = requests.get(B + '/landing/how-it-works')
check('/landing/how-it-works is the landing page', is_landing(r))
t = text_of(r)
check('How it works: title, tagline, SEO title', 'Discover, Learn and Create with ArtistikCity' in t and 'Your creative journey starts here.' in t
      and '<title>How ArtistikCity Works | Discover Art, Artists and Creative Learning</title>' in r.text)
check('How it works: description, canonical, Open Graph', 'Discover how ArtistikCity connects art lovers' in r.text and re.search(r'<link rel="canonical" href="http://localhost:8080/landing/how-it-works">', r.text)
      and 'property="og:image"' in r.text and 'og:title' in r.text)
ld = json.loads(html.unescape(re.search(r'<script type="application/ld\+json">([\s\S]*?)</script>', r.text).group(1)).replace('<\\/', '</'))
check('How it works: WebPage + Organization + FAQPage data', {'WebPage', 'Organization', 'FAQPage'} <= {g['@type'] for g in ld['@graph']})
check('How it works: no price, no checkout, no sticky bar', 'lp-pricebox' not in r.text and '/enroll' not in r.text and 'id="lp-sticky"' not in r.text)
courses = sql("select group_concat(title order by id separator '|') from courses where status = 1").split('|')
hidden = [x for x in sql("select group_concat(title order by id separator '|') from courses where status <> 1").split('|') if x and x != 'NULL']
check('How it works: every published course listed, none that is unpublished', all(c in t for c in courses) and not any(f'>{h}<' in r.text for h in hidden), (courses, hidden))
check('How it works: Watercolour card links to its landing page', 'href="/landing/watercolour-landscapes-for-beginners"' in r.text)
check('How it works: artists from the database with their profile links', 'href="/teacher/vaishali-j-goswami"' in r.text and 'href="/teacher/rajat-malhotra"' in r.text)
check('How it works: final CTA buttons', all(x in r.text for x in ['>Explore Courses</a>', 'href="#meet-artists" data-cta="final-2">Discover Artists</a>', 'href="/commission/step-1" data-cta="final-3">Commission Artwork</a>']))
check('How it works: only approved testimonials', 'Daniel Brooks' not in t and 'Sneha Reddy' in t)
imgs = re.findall(r'<img [^>]*>', r.text)
check('every image has alt text (empty only for decorative ones)', all(' alt="' in i for i in imgs), [i for i in imgs if ' alt="' not in i][:3])
for link in set(re.findall(r'href="(/[^"#]*)"', r.text)):
    if link.startswith('/img/') or link.endswith('.css') or link.endswith('.ico'): continue
    code = requests.get(B + link, allow_redirects=True).status_code
    check(f'How it works: link {link} works', code == 200, code)

r = requests.get(B + '/landing/watercolour-landscapes-for-beginners')
t = text_of(r)
check('Watercolour: landing page with the brief\'s title and tagline', is_landing(r) and 'Master the Art of Watercolour Landscapes' in t and 'one brushstroke at a time' in t)
check('Watercolour: SEO title', '<title>Watercolour Landscapes for Beginners | ArtistikCity</title>' in r.text)
check('Watercolour: Enroll goes to this page\'s checkout', 'href="/landing/watercolour-landscapes-for-beginners/enroll" data-cta="hero"' in r.text)
has_video = sql("select coalesce(intro_video_url, '') from courses where id = 1") != ''
check('Watercolour: preview button only when the course has a preview video', ('Watch Course Preview' in r.text) == has_video)
check('Watercolour: curriculum from the course', all(m in t for m in sql("select group_concat(module_title separator '|') from course_modules where course_id = 1 and status = '1'").split('|')))
check('Watercolour: course price (early bird) and its real end date', '₹3,999' in t and '30 Nov 2026' in t)
check('Watercolour: course reviews only, stars from real ratings', 'Verified student' in t and 'Sneha Reddy' not in t)
check('Watercolour: no invented claims', not re.search(r'lifetime|guarantee|money-back|\d+\+ students|award', t, re.I), re.findall(r'lifetime|guarantee|money-back|award', t, re.I))
check('Watercolour: "Who should join" from the course, not repeated in the intro', 'Who should join' in t and "Who it's for</h3>" not in r.text)

r = requests.get(B + '/landing/commission')
t = text_of(r)
check('/landing/commission is the landing page', is_landing(r) and 'Turn Your Memories Into Timeless Art' in t)
check('Commission: SEO title', '<title>Custom Art Commissions | Personalized Artwork | ArtistikCity</title>' in r.text)
check('Commission: the four wizard styles and the 6-step progress bar', all(f'data-theme="{x}"' in r.text for x in ['PORTRAIT', 'LANDSCAPE', 'WATERCOLOR', 'CHARCOAL'])
      and t.count('Size & delivery') >= 1 and 'aria-current="step"' in r.text)
check('Commission: the form hands over to step 2', 'action="/commission/step-2"' in r.text and 'data-commission-form' in r.text)
rate = 84
def inr(usd): return int(round(usd * rate / 10.0)) * 10
expect = {'Digital file': inr(49 * 0.8), 'Canvas print': inr(89 * 0.8 + 15), 'Original painting': inr(249 * 0.8 + 15)}
check('Commission: "from" prices = the commission price list', all(f'₹{v:,}' in t for v in expect.values()), expect)
check('Commission: no deposit, no promised timelines, no guarantees', not re.search(r'deposit required|within \d+ (days|weeks)|guarantee', t, re.I) and 'no separate deposit' in t)
check('Commission: no testimonials pretending to be commission reviews', 'What our community says' not in t and 'lp-quote' not in r.text)
check('Commission: hero image has a description', re.search(r'<figure class="lp-frame">\s*<img [^>]*alt="[^"]+"', r.text) is not None)

check('landing pages are not reachable as /course/{slug}', not is_landing(requests.get(B + '/course/how-it-works')) and not is_landing(requests.get(B + '/course/commission')))
sm = requests.get(B + '/sitemap.xml').text
check('sitemap: the three landing addresses with their publish date', all(re.search(rf'/landing/{x}</loc><lastmod>\d{{4}}-\d\d-\d\d', sm) for x in ('how-it-works', 'commission', 'watercolour-landscapes-for-beginners')))
check('sitemap: the original pages are still listed', '/how-it-works</loc>' in sm and '/course/watercolour-landscapes-for-beginners</loc>' in sm)

# ================================================================================================ admin
r = get(adm, '/admin/api/landing/pages?per=100')
rows = {x['id']: x for x in r.json()['rows']}
check('list: every page with its own address, as a full link too', rows.get(HIW, {}).get('publicUrl') == '/landing/how-it-works' and rows.get(COM, {}).get('publicUrl') == '/landing/commission'
      and rows[HIW]['fullUrl'] == B + '/landing/how-it-works' and rows.get(WC, {}).get('publicUrl') == '/landing/watercolour-landscapes-for-beginners')
d = get(adm, f'/admin/api/landing/pages/{HIW}').json()
check('editor: site page data without pricing / payments', d['page']['site'] is True and 'pricing' not in d and 'payments' not in d and d['page']['publicUrl'] == '/landing/how-it-works' and d['page']['fullUrl'] == B + '/landing/how-it-works')
cfg = d['config']
bad = json.loads(json.dumps(cfg)); bad['sections'].append({'type': 'pricing', 'enabled': True, 'content': {}})
r = put(adm, f'/admin/api/landing/pages/{HIW}/draft', {'config': bad})
check('draft: a course-only section is refused on an information page', r.status_code == 422 and any('needs a course' in v for v in r.json().get('errors', {}).values()), r.text)
bad = json.loads(json.dumps(cfg)); bad['cta']['primaryHref'] = ''
r = put(adm, f'/admin/api/landing/pages/{HIW}/draft', {'config': bad})
check('draft: the main button needs an address', r.status_code == 422 and 'cta.primaryHref' in r.json().get('errors', {}), r.text)
bad = json.loads(json.dumps(cfg)); bad['sections'][2]['content']['items'][0]['link'] = 'javascript:alert(1)'
r = put(adm, f'/admin/api/landing/pages/{HIW}/draft', {'config': bad})
check('draft: unsafe links refused', r.status_code == 422 and any('.link' in k for k in r.json().get('errors', {})), r.text)
r = put(adm, f'/admin/api/landing/pages/{HIW}/template', {'template': 'artist-showcase'})
check('template: a course template is refused on an information page', r.status_code == 422, r.text)
r = put(adm, f'/admin/api/landing/pages/{WC}/template', {'template': 'platform-showcase'})
check('template: an information-page template is refused on a course page', r.status_code == 422, r.text)
r = post(adm, '/admin/api/landing/pages', {'kind': 'SITE', 'name': 'Spring campaign', 'template': 'artist-showcase'})
check('create: information page with a course template refused', r.status_code == 422 and 'template' in r.json().get('errors', {}), r.text)
r = post(adm, '/admin/api/landing/pages', {'kind': 'SITE', 'name': 'How it works', 'template': 'platform-showcase'})
check('create: the address is generated from the name and made unique', r.status_code == 200 and r.json().get('slug') == 'how-it-works-2', r.text)
second = r.json().get('id')
check('new information page: kind SITE, no course', sql(f"select concat(kind, ':', coalesce(course_id, 'null')) from landing_pages where id = {second}") == 'SITE:null')
r = post(adm, '/admin/api/landing/pages', {'kind': 'SITE', 'name': 'x', 'slug': 'how-it-works', 'template': 'platform-showcase'})
check('create: an address already used is refused', r.status_code == 422 and 'slug' in r.json().get('errors', {}), r.text)
r = requests.get(B + f'/admin/landing/preview/{second}', cookies=adm.cookies)
check('preview of the draft: noindex', r.status_code == 200 and 'noindex' in r.text and 'lp--preview' in r.text)
check('a draft is not public', requests.get(B + '/landing/how-it-works-2').status_code == 404)
check('delete the unused draft', adm.delete(B + f'/admin/api/landing/pages/{second}', headers=H(adm)).status_code == 200)

# unpublish: the landing address goes offline, the original pages never change
r = post(adm, f'/admin/api/landing/pages/{HIW}/unpublish'); check('unpublish How it works', r.status_code == 200 and '/landing/how-it-works' in r.json().get('message', ''), r.text)
check('its address is offline', requests.get(B + '/landing/how-it-works').status_code == 404)
comp, _ = component('/how-it-works')
check('/how-it-works is still the original page', comp == 'HowItWorks', comp)
r = post(adm, f'/admin/api/landing/pages/{HIW}/publish', {'note': 'back'}); check('publish again', r.status_code == 200 and '/landing/how-it-works' in r.json().get('message', ''), r.text)
check('live again', is_landing(requests.get(B + '/landing/how-it-works')))
comp, _ = component('/commission/step-2')
check('the rest of the wizard is untouched (step 2 is the React page)', comp == 'Commission/Step2Fulfillment', comp)

# rollback of the Watercolour page to the version before the showcase, then forward again
v1 = sql(f"select id from landing_page_versions where landing_page_id = {WC} and version_no = 1")
r = post(adm, f'/admin/api/landing/pages/{WC}/versions/{v1}/restore')
check('Watercolour: the earlier version can be made live again', r.status_code == 200 and 'Master the Art' not in requests.get(B + '/landing/watercolour-landscapes-for-beginners').text, r.text)
v2 = sql(f"select id from landing_page_versions where landing_page_id = {WC} and version_no = 2")
post(adm, f'/admin/api/landing/pages/{WC}/versions/{v2}/restore')
check('Watercolour: and the showcase version again', 'Master the Art' in requests.get(B + '/landing/watercolour-landscapes-for-beginners').text)

# instructors and checkout
vaishali = staff('vaishali@artistikcity.com')
check("instructor can't open an information page", get(vaishali, f'/admin/api/landing/pages/{HIW}').status_code == 403)
check("instructor doesn't see information pages in the list", all(x['id'] not in (HIW, COM) for x in get(vaishali, '/admin/api/landing/pages?per=100').json().get('rows', [])))
r = post(vaishali, '/admin/api/landing/pages', {'kind': 'SITE', 'name': 'mine', 'slug': 'mine-hiw', 'template': 'platform-showcase'})
check("instructor can't create an information page", r.status_code == 403, r.status_code)
g = requests.Session(); g.get(B + '/landing/how-it-works')
r = post(g, f'/landing/checkout/{HIW}/quote', {})
check('no checkout on an information page', r.status_code in (401, 404, 410), r.status_code)
before = int(sql(f"select count(*) from landing_events where landing_page_id = {COM} and event = 'CTA'"))
r = post(g, '/landing/e', {'event': 'CTA', 'page': COM, 'label': 'commission-next'})
r2 = requests.get(B + '/landing/how-it-works/enroll', allow_redirects=False)
check('information page has no checkout address (sent back to the page)', r2.status_code in (301, 302) and r2.headers.get('Location', '').endswith('/landing/how-it-works'), r2.headers.get('Location'))
check('CTA events recorded for information pages', r.status_code == 204 and int(sql(f"select count(*) from landing_events where landing_page_id = {COM} and event = 'CTA'")) == before + 1)
views = int(sql(f"select count(*) from landing_events where landing_page_id = {HIW} and event = 'VIEW'"))
check('views of the information page counted', views > 0)
g.get(B + '/landing/how-it-works?utm_source=newsletter&utm_medium=email&utm_campaign=nov')
check('UTM kept for 30 days (cookie)', 'ac_utm' in g.cookies.get_dict())

print(f'\n{"ALL PASSED" if not fails else str(fails) + " FAILED"}')
raise SystemExit(1 if fails else 0)
