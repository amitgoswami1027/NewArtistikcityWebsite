#!/bin/bash
# =====================================================================
#  ArtistikCity - the database part of the go-live reset (run on the EC2 server by ec2-deploy.sh --go-live, inside the
#  official mysql:8.4 image like db-migrate.sh; also by the CI test job against its own MySQL).
#
#    MODE=status   prints GO_LIVE_DONE=<request id> when a reset has already been made (settings system/go_live_request)
#                  and GO_LIVE_FILES=<request id> once the server finished the file step for it (system/go_live_files)
#    MODE=files-done  records that the file step of REQUEST_ID is finished (so a re-run after a failed file step resumes there)
#    MODE=reset    the reset, unless REQUEST_ID is already recorded (then it prints ALREADY_DONE and changes nothing):
#                  go-live-before.sql, schema.sql, every migration marked applied, golive-seed.sql, go-live-after.sql,
#                  then records REQUEST_ID. One SQL run: it stops at the first error (the final snapshot is the undo).
#
#  Environment: DB_HOST, DB_PORT (3306), DB_NAME, DB_USER, MYSQL_PWD, DB_SSL_MODE (REQUIRED), DB_DIR (folder holding
#  schema.sql), MODE, and for reset REQUEST_ID, ADMIN_EMAIL, ADMIN_NAME, ADMIN_HASH, STUDENT_EMAIL, STUDENT_HASH,
#  KEEP_SITE_LOCK (false). The hashes are bcrypt ($2a/$2b/$2y), made by HASH-PASSWORD.bat; no password is ever handled here.
# =====================================================================
set -euo pipefail

: "${DB_HOST:?}" "${DB_NAME:?}" "${DB_USER:?}" "${MODE:?MODE is status or reset}"
DB_PORT="${DB_PORT:-3306}"
DB_SSL_MODE="${DB_SSL_MODE:-REQUIRED}"
DB_DIR="${DB_DIR:-$(cd "$(dirname "$0")/../database" && pwd)}"
export MYSQL_PWD="${MYSQL_PWD:-}"

case "$DB_NAME" in *[!A-Za-z0-9_]*) echo "DB_NAME may only contain letters, digits and _" >&2; exit 2 ;; esac
mysql_base=(mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --ssl-mode="$DB_SSL_MODE" --default-character-set=utf8mb4 --connect-timeout=15)
q() { "${mysql_base[@]}" --batch --skip-column-names "$DB_NAME" -e "$1"; }

marker() {   # $1 = go_live_request | go_live_files
  q "SELECT meta_value FROM settings WHERE meta_tag = 'system' AND meta_key = '$1' ORDER BY id DESC LIMIT 1" 2>/dev/null || true
}
done_id() { marker go_live_request; }

if [ "$MODE" = status ]; then
  echo "GO_LIVE_DONE=$(done_id)"
  echo "GO_LIVE_FILES=$(marker go_live_files)"
  exit 0
fi
if [ "$MODE" = files-done ]; then
  [[ "${REQUEST_ID:-}" =~ ^[A-Za-z0-9._-]{1,40}$ ]] || { echo "REQUEST_ID is missing or invalid" >&2; exit 2; }
  [ "$(done_id)" = "$REQUEST_ID" ] || { echo "the database reset of $REQUEST_ID is not recorded" >&2; exit 2; }
  q "DELETE FROM settings WHERE meta_tag = 'system' AND meta_key = 'go_live_files';
     INSERT INTO settings (meta_tag, meta_key, meta_value, created_at, updated_at) VALUES ('system', 'go_live_files', '$REQUEST_ID', NOW(), NOW());"
  [ "$(marker go_live_files)" = "$REQUEST_ID" ] || { echo "could not record the file step" >&2; exit 2; }
  echo "GO_LIVE_FILES_OK=$REQUEST_ID"
  exit 0
fi
[ "$MODE" = reset ] || { echo "MODE must be status, reset or files-done" >&2; exit 2; }

: "${REQUEST_ID:?}" "${ADMIN_EMAIL:?}" "${ADMIN_NAME:?}" "${ADMIN_HASH:?}" "${STUDENT_EMAIL:?}" "${STUDENT_HASH:?}"
fail() { echo "ERROR: $*" >&2; exit 2; }
[[ "$REQUEST_ID" =~ ^[A-Za-z0-9._-]{1,40}$ ]] || fail "REQUEST_ID: 1-40 letters, digits, dot, dash or underscore"
email_re='^[^@[:space:]'"'"'"\\]+@[^@[:space:]'"'"'"\\]+\.[^@[:space:]'"'"'"\\]+$'
[[ "$ADMIN_EMAIL" =~ $email_re ]] || fail "ADMIN_EMAIL is not an e-mail address"
[[ "$STUDENT_EMAIL" =~ $email_re ]] || fail "STUDENT_EMAIL is not an e-mail address"
[[ "$ADMIN_NAME" =~ ^[^\'\"\\\<\>]{2,100}$ ]] || fail "ADMIN_NAME: a plain name of 2-100 characters"
hash_re='^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
[[ "$ADMIN_HASH" =~ $hash_re ]] || fail "ADMIN_HASH is not a bcrypt hash (make it with HASH-PASSWORD.bat)"
[[ "$STUDENT_HASH" =~ $hash_re ]] || fail "STUDENT_HASH is not a bcrypt hash (make it with HASH-PASSWORD.bat)"
for f in schema.sql golive/golive-seed.sql golive/go-live-before.sql golive/go-live-after.sql; do [ -f "$DB_DIR/$f" ] || fail "$DB_DIR/$f is missing"; done

already="$(done_id)"
if [ "$already" = "$REQUEST_ID" ]; then
  echo "ALREADY_DONE: go-live request $REQUEST_ID was carried out before; nothing changed."
  exit 0
fi

keep_lock=0; [ "${KEEP_SITE_LOCK:-false}" = "true" ] && keep_lock=1
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
sql="$work/go-live.sql"
{
  # the hashes contain $ and /, never quotes (checked above), so plain quoting is safe
  vars="SET @ga_admin_email = '$ADMIN_EMAIL'; SET @ga_admin_name = '$ADMIN_NAME'; SET @ga_admin_hash = '$ADMIN_HASH';
SET @ga_student_email = '$STUDENT_EMAIL'; SET @ga_student_hash = '$STUDENT_HASH'; SET @ga_keep_site_lock = $keep_lock; SET @ga_request_id = '$REQUEST_ID';"
  echo "$vars"
  cat "$DB_DIR/golive/go-live-before.sql"; echo
  cat "$DB_DIR/schema.sql"; echo
  echo "CREATE TABLE IF NOT EXISTS schema_migrations (filename VARCHAR(191) NOT NULL PRIMARY KEY, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);"
  for m in "$DB_DIR"/migrations/*.sql; do
    n="$(basename "$m")"
    case "$n" in *[!A-Za-z0-9._-]*) continue ;; esac
    echo "INSERT IGNORE INTO schema_migrations (filename) VALUES ('$n');"
  done
  echo "$vars"
  cat "$DB_DIR/golive/golive-seed.sql"; echo
  cat "$DB_DIR/golive/go-live-after.sql"; echo
} > "$sql"
echo "==> Go-live reset of $DB_NAME on $DB_HOST (request $REQUEST_ID)"
"${mysql_base[@]}" --table "$DB_NAME" < "$sql"
[ "$(done_id)" = "$REQUEST_ID" ] || fail "the reset ran but the request was not recorded"
echo "GO_LIVE_RESET_OK=$REQUEST_ID"
