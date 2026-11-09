#!/bin/bash
# =====================================================================
#  ArtistikCity - the go-live reset, run by GitHub Actions (job "go-live" in .github/workflows/deploy.yml) after a
#  successful deploy, when the commit contains deploy/go-live.request (docs/GO-LIVE.md). Each REQUEST_ID runs once.
#
#   1  check the request file and the two password hashes (GitHub secrets GO_LIVE_ADMIN_HASH / GO_LIVE_STUDENT_HASH)
#   2  ask the server whether this request was already carried out -> then stop, nothing changes
#   3  RDS snapshot pre-go-live-<REQUEST_ID>, waited for (the undo point; nothing destructive happens before it exists)
#   4  the request (hashes, no passwords) -> s3://<bucket>/private/go-live/<id>.env (encrypted); the server
#      (ec2-deploy.sh --go-live) reads and deletes it, rebuilds the database, archives the uploads, uploads the sample
#      course files and restarts the site
#   5  DELETE_OLD_SNAPSHOTS=true: every other manual snapshot in the region (pre-go-live-* are kept) and every retained
#      automated backup are deleted
#   6  CLEAR_AUTOMATED_BACKUPS=true: backup retention 0 then back (deletes the old automated backups; two short restarts)
#   7  public health check, summary
#
#  Environment: AWS_REGION, STACK (artistikcity), INSTANCE_ID, SITE_DOMAIN, REQUEST_FILE (deploy/go-live.request),
#  ADMIN_HASH, STUDENT_HASH, DISTRIBUTION_ID, GITHUB_STEP_SUMMARY (optional), CHECK_ONLY=true (only check the request, used by
#  the test job so a bad request stops the pipeline before anything is built). Uses the AWS CLI with the deploy role.
# =====================================================================
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-south-1}"
STACK="${STACK:-artistikcity}"
REQUEST_FILE="${REQUEST_FILE:-deploy/go-live.request}"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"

log() { echo "[$(date -u +%H:%M:%S)] $*"; }
die() { echo "::error::$*" >&2; echo "### Go-live reset stopped: $*" >> "$SUMMARY"; exit 1; }
aws_() { aws --region "$AWS_REGION" "$@"; }

if [ ! -f "$REQUEST_FILE" ]; then
  log "no $REQUEST_FILE in this commit: no go-live reset"
  exit 0
fi

# ---------------------------------------------------------------- 1. the request
val() { grep -E "^$1=" "$REQUEST_FILE" | tail -n 1 | cut -d= -f2- | sed 's/[[:space:]]*#.*$//; s/^[[:space:]]*//; s/[[:space:]]*$//' || true; }
CONFIRM="$(val CONFIRM)"; REQUEST_ID="$(val REQUEST_ID)"
ADMIN_EMAIL="$(val ADMIN_EMAIL)"; ADMIN_EMAIL="${ADMIN_EMAIL:-admin@artistikcity.com}"
ADMIN_NAME="$(val ADMIN_NAME)"; ADMIN_NAME="${ADMIN_NAME:-ArtistikCity}"
STUDENT_EMAIL="$(val STUDENT_EMAIL)"; STUDENT_EMAIL="${STUDENT_EMAIL:-student@artistikcity.com}"
KEEP_SITE_LOCK="$(val KEEP_SITE_LOCK)"; KEEP_SITE_LOCK="${KEEP_SITE_LOCK:-false}"
ARCHIVE_UPLOADS="$(val ARCHIVE_UPLOADS)"; ARCHIVE_UPLOADS="${ARCHIVE_UPLOADS:-true}"
DELETE_OLD_SNAPSHOTS="$(val DELETE_OLD_SNAPSHOTS)"; DELETE_OLD_SNAPSHOTS="${DELETE_OLD_SNAPSHOTS:-true}"
CLEAR_AUTOMATED_BACKUPS="$(val CLEAR_AUTOMATED_BACKUPS)"; CLEAR_AUTOMATED_BACKUPS="${CLEAR_AUTOMATED_BACKUPS:-true}"

[ "$CONFIRM" = "RESET PRODUCTION" ] || die "$REQUEST_FILE must contain the line CONFIRM=RESET PRODUCTION"
[[ "$REQUEST_ID" =~ ^[A-Za-z0-9._-]{1,40}$ ]] || die "REQUEST_ID in $REQUEST_FILE: 1-40 letters, digits, dot, dash or underscore (a new id for every reset)"
email_re='^[^@[:space:]'"'"'"\\]+@[^@[:space:]'"'"'"\\]+\.[^@[:space:]'"'"'"\\]+$'
[[ "$ADMIN_EMAIL" =~ $email_re ]] || die "ADMIN_EMAIL is not an e-mail address"
[[ "$STUDENT_EMAIL" =~ $email_re ]] || die "STUDENT_EMAIL is not an e-mail address"
[[ "$ADMIN_NAME" =~ ^[^\'\"\\\<\>]{2,100}$ ]] || die "ADMIN_NAME: a plain name of 2-100 characters"
for f in KEEP_SITE_LOCK ARCHIVE_UPLOADS DELETE_OLD_SNAPSHOTS CLEAR_AUTOMATED_BACKUPS; do
  case "${!f}" in true|false) ;; *) die "$f must be true or false" ;; esac
done
hash_re='^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
[[ "${ADMIN_HASH:-}" =~ $hash_re ]] || die "the GitHub secret GO_LIVE_ADMIN_HASH is missing or not a bcrypt hash (make it with HASH-PASSWORD.bat)"
[[ "${STUDENT_HASH:-}" =~ $hash_re ]] || die "the GitHub secret GO_LIVE_STUDENT_HASH is missing or not a bcrypt hash (make it with HASH-PASSWORD.bat)"
[ "${CHECK_ONLY:-false}" = true ] || : "${INSTANCE_ID:?INSTANCE_ID (GitHub variable EC2_INSTANCE_ID) is required}"
log "go-live request $REQUEST_ID: super admin $ADMIN_NAME <$ADMIN_EMAIL>, student $STUDENT_EMAIL"
if [ "${CHECK_ONLY:-false}" = true ]; then log "request is valid (check only)"; exit 0; fi

# runs a command on the server through SSM and prints its output; returns non-zero unless it succeeded
ssm_run() {   # $1 = command, $2 = timeout seconds
  local params id status i
  params=$(jq -n --arg c "$1" --arg t "${2:-600}" '{commands: [$c], executionTimeout: [$t]}')
  id=$(aws_ ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript \
         --comment "artistikcity go-live $REQUEST_ID" --parameters "$params" --query Command.CommandId --output text)
  status=Pending
  for i in $(seq 1 $(( ${2:-600} / 5 + 12 ))); do
    sleep 5
    status=$(aws_ ssm get-command-invocation --command-id "$id" --instance-id "$INSTANCE_ID" --query Status --output text 2>/dev/null || echo Pending)
    case "$status" in Pending|InProgress|Delayed) continue ;; *) break ;; esac
  done
  aws_ ssm get-command-invocation --command-id "$id" --instance-id "$INSTANCE_ID" --query StandardOutputContent --output text || true
  if [ "$status" != Success ]; then
    aws_ ssm get-command-invocation --command-id "$id" --instance-id "$INSTANCE_ID" --query StandardErrorContent --output text >&2 || true
    return 1
  fi
}

# ---------------------------------------------------------------- 2. already done?
out="$(ssm_run "/opt/artistikcity/ec2-deploy.sh --go-live-status $REQUEST_ID" 300)" || die "could not ask the server whether $REQUEST_ID was already carried out"
if echo "$out" | grep -qx "GO_LIVE_DONE=$REQUEST_ID" && echo "$out" | grep -qx "GO_LIVE_FILES=$REQUEST_ID"; then
  log "go-live request $REQUEST_ID was already carried out: nothing to do"
  { echo "### Go-live reset $REQUEST_ID: already done"; echo ""; echo "Nothing changed. You can delete \`$REQUEST_FILE\` from the repository."; } >> "$SUMMARY"
  exit 0
fi
if echo "$out" | grep -qx "GO_LIVE_DONE=$REQUEST_ID"; then
  log "the database of $REQUEST_ID was reset by an earlier run; this run finishes the remaining steps (the existing snapshot stays the undo point)"
fi

# ---------------------------------------------------------------- 3. the final snapshot
outputs="$(aws_ cloudformation describe-stacks --stack-name "$STACK" --query 'Stacks[0].Outputs' --output json)"
out_val() { echo "$outputs" | jq -r --arg k "$1" '.[] | select(.OutputKey == $k) | .OutputValue'; }
endpoint="$(out_val DatabaseEndpoint)"; bucket="$(out_val UploadsBucketName)"
[ -n "$endpoint" ] && [ -n "$bucket" ] || die "stack $STACK has no DatabaseEndpoint / UploadsBucketName outputs"
db_json="$(aws_ rds describe-db-instances --output json | jq --arg e "$endpoint" '[.DBInstances[] | select(.Endpoint.Address == $e)][0]')"
db_id="$(echo "$db_json" | jq -r '.DBInstanceIdentifier')"; retention="$(echo "$db_json" | jq -r '.BackupRetentionPeriod')"
[ -n "$db_id" ] && [ "$db_id" != null ] || die "no RDS database with endpoint $endpoint"
final="pre-go-live-$(echo "$REQUEST_ID" | tr 'A-Z._' 'a-z--' | tr -s '-' | sed 's/-$//')"   # RDS: letters, digits, single hyphens
if aws_ rds describe-db-snapshots --db-snapshot-identifier "$final" --output text >/dev/null 2>&1; then
  log "snapshot $final already exists (an earlier attempt): using it"
else
  log "1/5 final snapshot $final of $db_id (5-15 minutes)"
  aws_ rds create-db-snapshot --db-instance-identifier "$db_id" --db-snapshot-identifier "$final" \
    --tags "Key=purpose,Value=pre-go-live" "Key=request,Value=$REQUEST_ID" --output text >/dev/null || die "could not take the snapshot (does the deploy role have the RDS permissions? docs/GO-LIVE.md)"
fi
aws_ rds wait db-snapshot-available --db-snapshot-identifier "$final" || die "snapshot $final did not become available"
log "snapshot $final is ready"

# ---------------------------------------------------------------- 4. the reset on the server
key="private/go-live/$REQUEST_ID.env"
printf '%s\n' "REQUEST_ID=$REQUEST_ID" "ADMIN_EMAIL=$ADMIN_EMAIL" "ADMIN_NAME=$ADMIN_NAME" "ADMIN_HASH=$ADMIN_HASH" \
  "STUDENT_EMAIL=$STUDENT_EMAIL" "STUDENT_HASH=$STUDENT_HASH" "KEEP_SITE_LOCK=$KEEP_SITE_LOCK" "ARCHIVE_UPLOADS=$ARCHIVE_UPLOADS" |
  aws_ s3 cp - "s3://$bucket/$key" --sse AES256 --only-show-errors || die "could not hand the request to the server (s3://$bucket/$key)"
log "2/5 database, files and restart on the server"
if ! ssm_run "/opt/artistikcity/ec2-deploy.sh --go-live $key" 1500; then
  aws_ s3 rm "s3://$bucket/$key" --only-show-errors >/dev/null 2>&1 || true
  die "the reset on the server failed (see the output above). Undo point: snapshot $final"
fi
if [ -n "${DISTRIBUTION_ID:-}" ]; then
  aws_ cloudfront create-invalidation --distribution-id "$DISTRIBUTION_ID" --paths '/storage/uploads/*' --query Invalidation.Id --output text >/dev/null || true
fi

# ---------------------------------------------------------------- 5. old snapshots and retained backups
deleted=0
if [ "$DELETE_OLD_SNAPSHOTS" = true ]; then
  log "3/5 deleting older manual snapshots (pre-go-live-* are kept)"
  for s in $(aws_ rds describe-db-snapshots --snapshot-type manual --query 'DBSnapshots[].DBSnapshotIdentifier' --output text); do
    case "$s" in pre-go-live-*) continue ;; esac
    log "   deleting snapshot $s"; aws_ rds delete-db-snapshot --db-snapshot-identifier "$s" --output text >/dev/null && deleted=$((deleted + 1))
  done
  for r in $(aws_ rds describe-db-instance-automated-backups --query "DBInstanceAutomatedBackups[?Status=='retained'].DbiResourceId" --output text); do
    log "   deleting retained backup $r"; aws_ rds delete-db-instance-automated-backup --dbi-resource-id "$r" --output text >/dev/null && deleted=$((deleted + 1))
  done
fi

# ---------------------------------------------------------------- 6. automated backups
if [ "$CLEAR_AUTOMATED_BACKUPS" = true ] && [ "${retention:-0}" -gt 0 ] 2>/dev/null; then
  log "4/5 clearing the automated backups of $db_id (retention 0, then $retention days; two short restarts)"
  aws_ rds modify-db-instance --db-instance-identifier "$db_id" --backup-retention-period 0 --apply-immediately --output text >/dev/null
  sleep 30; aws_ rds wait db-instance-available --db-instance-identifier "$db_id"
  aws_ rds modify-db-instance --db-instance-identifier "$db_id" --backup-retention-period "$retention" --apply-immediately --output text >/dev/null
  sleep 30; aws_ rds wait db-instance-available --db-instance-identifier "$db_id"
fi

# ---------------------------------------------------------------- 7. check
ok=false
if [ -n "${SITE_DOMAIN:-}" ]; then
  log "5/5 public health check"
  for i in $(seq 1 30); do
    if curl -fsS --max-time 10 "https://$SITE_DOMAIN/healthz" >/dev/null 2>&1; then ok=true; break; fi
    sleep 10
  done
fi
{
  echo "### Go-live reset $REQUEST_ID done"
  echo ""
  echo "* Database rebuilt: super admin **$ADMIN_EMAIL**, student **$STUDENT_EMAIL**, six sample courses (configuration kept)."
  echo "* Undo point: RDS snapshot \`$final\`; old files under \`s3://$bucket/private/archive/pre-go-live-$REQUEST_ID/\`."
  echo "* Older snapshots / retained backups deleted: $deleted. Automated backups cleared: $CLEAR_AUTOMATED_BACKUPS."
  echo "* Health check: $([ "$ok" = true ] && echo "https://$SITE_DOMAIN answers" || echo "not confirmed, check the site")."
  echo ""
  echo "Next: delete \`$REQUEST_FILE\` from the repository (a later push would only say \"already done\"), sign in, set up"
  echo "two-step verification, and follow the checklist in docs/GO-LIVE.md."
} >> "$SUMMARY"
log "go-live request $REQUEST_ID done"
