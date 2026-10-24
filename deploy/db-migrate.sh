#!/bin/bash
# =====================================================================
#  ArtistikCity - bring the database up to date (used by ec2-deploy.sh on every deploy).
#  The same rules as setup-and-run.ps1 on Windows:
#    * empty database      -> load database/schema.sql (+ seed.sql / demo pack when asked), mark every
#                             migration as applied (schema.sql already contains them)
#    * existing database   -> run each database/migrations/*.sql not yet in schema_migrations, in name order
#  Any SQL error stops the script with a non-zero exit, so a failed migration stops the deploy before the
#  running site is touched.
#
#  Environment: DB_HOST, DB_PORT (3306), DB_NAME, DB_USER, MYSQL_PWD (password, read by the mysql client),
#               DB_SSL_MODE (REQUIRED), LOAD_SEED (false), LOAD_DEMO (false), DB_DIR (folder holding schema.sql)
#  Needs the MySQL 8 command-line client (ec2-deploy.sh runs it in the official mysql:8.4 image).
# =====================================================================
set -euo pipefail

: "${DB_HOST:?DB_HOST is required}" "${DB_NAME:?DB_NAME is required}" "${DB_USER:?DB_USER is required}" "${MYSQL_PWD:?MYSQL_PWD is required}"
DB_PORT="${DB_PORT:-3306}"
DB_SSL_MODE="${DB_SSL_MODE:-REQUIRED}"
DB_DIR="${DB_DIR:-$(cd "$(dirname "$0")/../database" && pwd)}"
export MYSQL_PWD

case "$DB_NAME" in
  *[!A-Za-z0-9_]*) echo "DB_NAME may only contain letters, digits and _" >&2; exit 2 ;;
esac

mysql_base=(mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --ssl-mode="$DB_SSL_MODE"
            --default-character-set=utf8mb4 --connect-timeout=15)

q() {          # q "SQL"  -> tab separated rows, no header
  "${mysql_base[@]}" --batch --skip-column-names "$DB_NAME" -e "$1"
}
run_file() {   # run_file path  (stops at the first error)
  echo "    running $(basename "$1")"
  "${mysql_base[@]}" "$DB_NAME" < "$1"
}

echo "==> Database $DB_NAME on $DB_HOST"
for i in $(seq 1 20); do
  if "${mysql_base[@]}" -e "SELECT 1" >/dev/null 2>&1; then break; fi
  if [ "$i" = 20 ]; then echo "Cannot connect to MySQL at $DB_HOST:$DB_PORT" >&2; "${mysql_base[@]}" -e "SELECT 1"; exit 1; fi
  sleep 3
done
"${mysql_base[@]}" -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

q "CREATE TABLE IF NOT EXISTS schema_migrations (filename VARCHAR(191) NOT NULL PRIMARY KEY, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)"

has_tables=$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DB_NAME' AND table_name = 'users'")
shopt -s nullglob
migrations=("$DB_DIR"/migrations/*.sql)
IFS=$'\n' migrations=($(printf '%s\n' "${migrations[@]}" | sort)); unset IFS

if [ "$has_tables" = "0" ]; then
  echo "==> Empty database: creating the tables"
  run_file "$DB_DIR/schema.sql"
  if [ "${LOAD_SEED:-false}" = "true" ]; then
    echo "==> Loading sample data (seed.sql)"
    run_file "$DB_DIR/seed.sql"
  fi
  for m in "${migrations[@]}"; do
    q "INSERT IGNORE INTO schema_migrations (filename) VALUES ('$(basename "$m")')"
  done
else
  applied=$(q "SELECT filename FROM schema_migrations")
  pending=0
  for m in "${migrations[@]}"; do
    name=$(basename "$m")
    case "$name" in *[!A-Za-z0-9._-]*) echo "Skipping oddly named migration $name" >&2; continue ;; esac
    if ! grep -qxF "$name" <<<"$applied"; then
      echo "==> Migration $name"
      run_file "$m"
      q "INSERT IGNORE INTO schema_migrations (filename) VALUES ('$name')"
      pending=$((pending + 1))
    fi
  done
  echo "==> $pending migration(s) applied"
fi

if [ "${LOAD_DEMO:-false}" = "true" ] && [ -f "$DB_DIR/demo/demo-data.sql" ]; then
  echo "==> Loading the demo data pack (safe to repeat)"
  run_file "$DB_DIR/demo/demo-data.sql"
fi
echo "==> Database is up to date"
