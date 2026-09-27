#!/bin/bash
# Creates the database if needed and loads database/schema.sql + seed.sql
# when the tables do not exist yet (or when RESET_DB=true).
set -e
DB_HOST="${DB_HOST:-db}"
DB_NAME="${DB_NAME:-artistikcity}"

if [ -x /opt/mssql-tools18/bin/sqlcmd ]; then SQLCMD=/opt/mssql-tools18/bin/sqlcmd; TLS="-C"
else SQLCMD=/opt/mssql-tools/bin/sqlcmd; TLS=""; fi
sql() { "$SQLCMD" -S "$DB_HOST" -U sa -P "$MSSQL_SA_PASSWORD" $TLS -b -x "$@"; }

echo "[db-init] waiting for SQL Server at $DB_HOST ..."
for i in $(seq 1 90); do
  if sql -Q "SELECT 1" >/dev/null 2>&1; then break; fi
  if [ "$i" = 90 ]; then echo "[db-init] SQL Server did not become ready"; exit 1; fi
  sleep 2
done

sql -Q "IF DB_ID('$DB_NAME') IS NULL CREATE DATABASE [$DB_NAME]"
HAS_TABLES=$(sql -d "$DB_NAME" -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN OBJECT_ID('dbo.users') IS NULL THEN 0 ELSE 1 END" | tr -d '[:space:]')

if [ "$HAS_TABLES" != "1" ] || [ "$RESET_DB" = "true" ]; then
  echo "[db-init] creating tables (schema.sql)"
  sql -d "$DB_NAME" -I -i /init/database/schema.sql
  echo "[db-init] loading demo data (seed.sql)"
  sql -d "$DB_NAME" -I -i /init/database/seed.sql
  echo "[db-init] database '$DB_NAME' is ready (fresh demo data)"
else
  echo "[db-init] database '$DB_NAME' already has tables - keeping existing data (set RESET_DB=true to recreate)"
fi
