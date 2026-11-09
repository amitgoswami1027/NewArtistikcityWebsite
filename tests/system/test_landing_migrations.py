"""Landing page migrations on a database that already holds data - the path the live site takes.

  1. builds the database of an older release (its schema.sql + seed.sql: by default the release before Landing Page
     Management, git commit f3d4804, read with `git show`; or OLD_SCHEMA / OLD_SEED files);
  2. gives the database a different default collation than its tables (what a dump imported into AWS RDS looks like:
     the cause of the earlier "Illegal mix of collations" 500);
  3. applies every newer migration in file-name order, like START-ArtistikCity.bat and deploy/db-migrate.sh, and then
     applies them all a second time (each must be safe to run twice);
  4. compares the result with a fresh install of the current schema.sql: every table, column (type, NULL, default) and
     index of the landing, sign-in, orders and settings tables must match, and the landing tables' collation must match
     the orders table's;
  5. checks the old data survived (users, orders, courses counts unchanged).

With --install the upgraded database replaces the app database (MYSQL_DB, default artistikcity), so the app can then be
started on it and tests/system/test_landing_regression.py run against an upgraded database (see tests/system/README.md).

  MYSQL_ROOT='mysql -h 127.0.0.1 -u root -pArtistikRoot@2026' python3 tests/system/test_landing_migrations.py [--install]
"""
import os, subprocess, sys, glob

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MYSQL = os.environ.get('MYSQL_ROOT', 'mysql -h 127.0.0.1 -u root -pArtistikRoot@2026').split()
APP_DB = os.environ.get('MYSQL_DB', 'artistikcity')
BASE = os.environ.get('BASE_COMMIT', 'f3d4804')
FRESH, UP = 'ac_mig_fresh', 'ac_mig_upgrade'
fails = 0


def check(name, cond, extra=''):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  {str(extra)[:1500]}'))
    if not cond:
        fails += 1


def run(sql, db=None, stdin=None):
    p = subprocess.run(MYSQL + ['--default-character-set=utf8mb4', '-N', '-B'] + ([db] if db else []) + ([] if stdin is not None else ['-e', sql]),
                       input=stdin, capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip()[:2000])
    return p.stdout.strip()


def old_file(name, env):
    if os.environ.get(env):
        return open(os.environ[env], encoding='utf-8').read()
    return subprocess.run(['git', 'show', f'{BASE}:database/{name}'], cwd=ROOT, capture_output=True, text=True, check=True).stdout


def rows(db, q):
    out = run(q.replace('{db}', db))
    return set(tuple(l.split('\t')) for l in out.splitlines() if l)


# ------------------------------------------------------------------ 1. the older release
old_schema, old_seed = old_file('schema.sql', 'OLD_SCHEMA'), old_file('seed.sql', 'OLD_SEED')
base_migrations = set(os.path.basename(x) for x in glob.glob(os.path.join(ROOT, 'database/migrations/*.sql')))
try:
    listed = subprocess.run(['git', 'ls-tree', '--name-only', f'{BASE}', 'database/migrations/'], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    base_migrations = set(os.path.basename(x) for x in listed.split() if x.endswith('.sql'))
except Exception:
    base_migrations = set(x for x in base_migrations if x < os.environ.get('BASE_MIGRATIONS_BEFORE', '2026-11-08'))   # an older base: e.g. 2026-11-05
newer = sorted(os.path.basename(x) for x in glob.glob(os.path.join(ROOT, 'database/migrations/*.sql')) if os.path.basename(x) not in base_migrations)
print('migrations to apply:', ', '.join(newer))
check('there are landing migrations to apply', any('landing' in m for m in newer), newer)

for db in (FRESH, UP):
    run(f'DROP DATABASE IF EXISTS {db}; CREATE DATABASE {db} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
run('', UP, stdin=old_schema)
run('', UP, stdin=old_seed)
before = {t: run(f'select count(*) from {t}', UP) for t in ('users', 'orders', 'courses', 'settings')}
check('old release loaded (no landing tables yet)', run(f"select count(*) from information_schema.tables where table_schema = '{UP}' and table_name like 'landing%'") == '0')

# ------------------------------------------------------------------ 2. imported-dump collations
run(f'ALTER DATABASE {UP} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')

# ------------------------------------------------------------------ 3. migrations, twice
for rnd in (1, 2):
    for m in newer:
        try:
            run('', UP, stdin=open(os.path.join(ROOT, 'database/migrations', m), encoding='utf-8').read())
            ok = True
            err = ''
        except RuntimeError as e:
            ok, err = False, str(e)
        check(f'{m}: applies ({"first" if rnd == 1 else "second"} run)', ok, err)

# ------------------------------------------------------------------ 4. same structure as a fresh install
run('', FRESH, stdin=open(os.path.join(ROOT, 'database/schema.sql'), encoding='utf-8').read())
TABLES = "(table_name like 'landing%' or table_name like 'auth\\_%' or table_name in ('users', 'orders', 'settings', 'courses', 'admins', 'admin_audit'))"
cols_q = ("select table_name, column_name, column_type, is_nullable, coalesce(column_default, 'NULL') from information_schema.columns "
          "where table_schema = '{db}' and " + TABLES)
idx_q = ("select table_name, index_name, group_concat(column_name order by seq_in_index), non_unique from information_schema.statistics "
         "where table_schema = '{db}' and " + TABLES + " group by table_name, index_name, non_unique")
tab_q = "select table_name from information_schema.tables where table_schema = '{db}' and " + TABLES
for label, q in (('tables', tab_q), ('columns', cols_q), ('indexes', idx_q)):
    f, u = rows(FRESH, q), rows(UP, q)
    check(f'upgraded database has the same {label} as a fresh install', f == u,
          {'missing after upgrade': sorted(f - u)[:15], 'only after upgrade': sorted(u - f)[:15]})
coll = run(f"select table_name, table_collation from information_schema.tables where table_schema = '{UP}' and (table_name like 'landing%' or table_name in ('orders', 'auth_email_codes'))")
colls = dict(l.split('\t') for l in coll.splitlines())
check('landing tables use the same collation as orders (joins work)', len(set(colls.values())) == 1, colls)
mixed = run(f"select count(*) from information_schema.columns c join information_schema.tables t on t.table_schema = c.table_schema and t.table_name = c.table_name "
            f"where c.table_schema = '{UP}' and c.table_name like 'landing%' and c.collation_name is not null and c.collation_name <> '{colls.get('orders', '')}'")
check('no landing column with another collation', mixed == '0', mixed)
check('a landing join with orders works', run("select count(*) from orders o join landing_pages p on p.slug = o.order_number", UP) == '0')
check('the default collation now matches the tables', run(f"select default_collation_name from information_schema.schemata where schema_name = '{UP}'") == colls.get('orders'))

# ------------------------------------------------------------------ 5. data kept
after = {t: run(f'select count(*) from {t}', UP) for t in before}
check('old data kept (users, orders, courses, settings)', all(after[t] >= before[t] for t in before) and after['users'] == before['users'] and after['orders'] == before['orders'], (before, after))
check('verify_with added, existing checkout rows untouched', run(f"select count(*) from information_schema.columns where table_schema = '{UP}' and table_name = 'landing_checkout' and column_name = 'verify_with'") == '1')

if '--install' in sys.argv:
    run(f'DROP DATABASE IF EXISTS {APP_DB}; CREATE DATABASE {APP_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    dumper = os.path.join(os.path.dirname(MYSQL[0]), 'mysqldump') if os.path.basename(MYSQL[0]) in ('mysql', 'mysql.exe') else 'mysqldump'
    dump = subprocess.run([dumper] + MYSQL[1:] + ['--routines', UP], capture_output=True, text=True)
    if dump.returncode != 0:
        raise SystemExit('mysqldump failed: ' + dump.stderr[:500])
    run('', APP_DB, stdin=dump.stdout)
    run(f'ALTER DATABASE {APP_DB} CHARACTER SET utf8mb4 COLLATE {colls.get("orders", "utf8mb4_unicode_ci")};')
    print(f'upgraded database installed as {APP_DB}')
run(f'DROP DATABASE IF EXISTS {FRESH}; DROP DATABASE IF EXISTS {UP};')

print(f'\n{"ALL PASSED" if not fails else str(fails) + " FAILED"}')
raise SystemExit(1 if fails else 0)
