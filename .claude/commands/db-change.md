---
description: Make a database schema change the ArtistikCity way (MySQL migration + schema.sql + seed)
argument-hint: <what to change, e.g. "add phone_verified TINYINT(1) to users">
---
Schema change requested: $ARGUMENTS

1. Create `database/migrations/<today's date as YYYY-MM-DD>_<short_name>.sql` in **MySQL 8** syntax. It must be safe to run
   twice. Use `CREATE TABLE IF NOT EXISTS`. For new columns or indexes, check `information_schema.columns` / `statistics`
   first (MySQL has no `ADD COLUMN IF NOT EXISTS`), for example with a prepared statement built from that check.
2. Apply the same change to `database/schema.sql`, so fresh installs get it.
3. Update `database/seed.sql` if demo rows need values for new NOT NULL columns.
4. Update the Java code that reads or writes the table (search `src/main/java` for the table name). Remember that `Db` returns
   lower-case column names, and `TINYINT(1)` comes back as `Boolean`.
5. Apply it to the local database: `.tools\mysql-8.4.13-winx64\bin\mysql.exe -h 127.0.0.1 -u artistik -pArtistik@2026 artistikcity < database/migrations/<file>.sql`.
   Then verify with `/run-dev`. To also check `schema.sql` + `seed.sql` from scratch, run `START-MySQL.bat -ResetDatabase`
   (this wipes local data).
