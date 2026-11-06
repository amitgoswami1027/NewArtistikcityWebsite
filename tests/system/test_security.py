"""Security log end to end (HTTP level): admin sign-in -> AUTH, a change with before/after -> UPDATE with diff,
a teacher probing 5 forbidden admin APIs -> DENIED x5 then session revoked, a student calling the admin API -> 401 JSON
and student session revoked, export -> EXPORT, verify -> chain intact, tamper -> chain broken."""
import os, re, subprocess, sys, urllib.parse, requests
B = 'http://localhost:8080'
MYSQL = os.environ.get('MYSQL_CMD', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity').split() + ['-N', '-e']
def sql(q): return subprocess.run(MYSQL + [q], capture_output=True, text=True, check=True).stdout.strip()
fails = 0
def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  {extra}'))
    if not cond: fails += 1

def xsrf(s): return urllib.parse.unquote(s.cookies.get('XSRF-TOKEN', ''))
def admin_login(email):
    s = requests.Session()
    html = s.get(B + '/admin/login').text
    tok = re.search(r'name="_token" value="([^"]+)"', html).group(1)
    r = s.post(B + '/admin/login', data={'_token': tok, 'email': email, 'password': 'password'}, allow_redirects=False)
    assert r.status_code in (302, 303), r.status_code
    s.get(B + '/admin/dashboard')
    return s
def post(s, path, body=None):
    return s.post(B + path, json=body or {}, headers={'X-XSRF-TOKEN': xsrf(s), 'Accept': 'application/json'})

start = int(sql('select coalesce(max(id),0) from audit_logs'))
adm = admin_login('admin@artistikcity.com')
check('admin sign-in logged as AUTH', sql(f"select count(*) from audit_logs where id>{start} and action_type='AUTH' and action='admin_login' and actor_email='admin@artistikcity.com'") == '1')

# a failed admin sign-in
s = requests.Session(); html = s.get(B + '/admin/login').text
tok = re.search(r'name="_token" value="([^"]+)"', html).group(1)
s.post(B + '/admin/login', data={'_token': tok, 'email': 'admin@artistikcity.com', 'password': 'wrong'})
check('failed admin sign-in logged as AUTH/FAIL', sql(f"select count(*) from audit_logs where id>{start} and action='admin_login_failed' and outcome='FAIL'") == '1')

# change a medium name -> UPDATE with before / after
mid, mname = sql("select id, name from mediums order by id limit 1").split('\t')
r = post(adm, '/admin/api/taxonomy/mediums', {'id': int(mid), 'name': mname + ' X', 'status': True})
check('medium renamed', r.status_code == 200, r.text[:200])
row = sql(f"select action_type, resource_type, resource_id, before_json like '%\"name\":\"{mname}\"%', after_json like '%\"name\":\"{mname} X\"%' from audit_logs where id>{start} and resource_type='mediums' order by id desc limit 1")
check('UPDATE has before and after snapshots', row.split('\t') == ['UPDATE', 'mediums', mid, '1', '1'], row)
post(adm, '/admin/api/taxonomy/mediums', {'id': int(mid), 'name': mname, 'status': True})
eid = sql(f"select id from audit_logs where id>{start} and resource_type='mediums' order by id desc limit 1")

# the page and the drawer API
r = adm.get(B + '/admin/security/audit', headers={'X-Inertia': 'true', 'X-Inertia-Version': adm.get(B + '/admin/security/audit').text and ''})
page = adm.get(B + '/admin/security/audit?types=UPDATE&window=24h')
check('security log page renders', page.status_code == 200 and 'Admin/SecurityAudit' in page.text)
d = adm.get(B + f'/admin/api/security/audit/{eid}', headers={'Accept': 'application/json'}).json()['entry']
check('drawer API returns before/after JSON', d['before_json'] and d['after_json'] and d['row_hash'])

# teacher: forbidden admin API calls -> DENIED, the 5th revokes the session
t = admin_login('vaishali@artistikcity.com')
codes = [post(t, '/admin/api/taxonomy/mediums', {'name': 'probe'}).status_code for _ in range(5)]
check('teacher refused with 403', codes == [403] * 5 or codes[:5].count(403) >= 4, codes)
import time
tid = sql("select id from admins where email='vaishali@artistikcity.com'")
denied = lambda: int(sql(f"select count(*) from audit_logs where id>{start} and action_type='DENIED' and actor_type='admin' and actor_id={tid}"))
for _ in range(20):                      # the refusal is logged after the response is sent
    if denied() >= 5: break
    time.sleep(0.25)
check('5 DENIED entries for the teacher', denied() >= 5, denied())
check('teacher session revoked', sql(f"select count(*) from audit_logs where id>{start} and action='session_revoked' and resource_type='admin' and resource_id='{tid}'") == '1')
r = post(t, '/admin/api/taxonomy/mediums', {'name': 'probe'})
check('revoked teacher now gets 401 JSON reauth', r.status_code == 401 and r.json().get('reauth') is True, r.status_code)
r = t.get(B + '/admin/dashboard', allow_redirects=False)
check('revoked teacher pages redirect to sign-in', r.status_code == 302 and '/admin/login' in r.headers.get('Location', ''))

# student calling the admin API -> 401, session signed out
st = requests.Session()
st.get(B + '/login')
r = st.post(B + '/login', json={'email': 'student@artistikcity.com', 'password': 'password'}, headers={'X-XSRF-TOKEN': xsrf(st), 'Accept': 'application/json'})
check('student signed in', st.get(B + '/dashboard', allow_redirects=False).status_code == 200, r.status_code)
r = st.get(B + '/admin/api/security/audit/1', headers={'Accept': 'application/json'})
check('student gets 401 JSON from the admin API', r.status_code == 401 and r.json().get('reauth') is True, r.status_code)
check('student session revoked', st.get(B + '/dashboard', allow_redirects=False).status_code == 302)
check('student revoke + DENIED logged', sql(f"select count(*) from audit_logs where id>{start} and action='session_revoked' and resource_type='student'") == '1'
      and sql(f"select count(*) from audit_logs where id>{start} and action_type='DENIED' and actor_type='student'") == '1')

# export -> EXPORT entry; CSV
r = adm.get(B + '/admin/security/audit/export?types=DENIED')
check('CSV export', r.status_code == 200 and r.text.lstrip('﻿').startswith('"Time","Type"') and 'DENIED' in r.text)
check('export logged as EXPORT', sql(f"select count(*) from audit_logs where id>{start} and action_type='EXPORT'") == '1')
t = admin_login('vaishali@artistikcity.com')
r = t.get(B + '/admin/security/audit/export', allow_redirects=False)
check('teacher (signed in again) cannot export: 403', r.status_code == 403, r.status_code)
r = t.get(B + '/admin/security/audit', allow_redirects=False)
check('teacher cannot open the security log: 403 page', r.status_code == 403 and 'Admin/Denied' in r.text, r.status_code)

# integrity
v = post(adm, '/admin/api/security/audit/verify').json()
check('chain intact', v['ok'] is True and v['checked'] > 0, v)
sql(f"update audit_logs set summary = concat(coalesce(summary,''), ' (edited)') where id = {eid}")
v = post(adm, '/admin/api/security/audit/verify').json()
check('tampered row detected', v['ok'] is False and str(v['brokenAt']) == eid, v)
sql(f"update audit_logs set summary = substring(summary, 1, char_length(summary) - 9) where id = {eid}")
v = post(adm, '/admin/api/security/audit/verify').json()
check('restored row: chain intact again', v['ok'] is True, v)
print('FAILED', fails) if fails else print('ALL PASSED')
sys.exit(1 if fails else 0)
