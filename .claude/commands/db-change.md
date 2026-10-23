---
description: Make a database schema change the ArtistikCity way (migration + schema.sql + seed)
argument-hint: <what to change, e.g. "add phone_verified BIT to users">
---
Schema change requested: $ARGUMENTS

1. Create `database/migrations/<today's date as YYYY-MM-DD>_<short_name>.sql`. It must be **idempotent T-SQL**:
   guard every change with `IF NOT EXISTS (SELECT * FROM sys.tables …)` / `sys.columns` / `sys.indexes` checks, like the existing migrations.
2. Apply the same change to `database/schema.sql`, so fresh installs and the H2 dev profile get it. Keep the
   statements in the subset H2 `MODE=MSSQLServer` understands.
3. Update `database/seed.sql` if demo rows need values for new NOT NULL columns.
4. Update the Java code that reads or writes the table (search `src/main/java` for the table name). Remember that `Db` returns lower-case column names.
5. Verify by running the dev profile (`/run-dev`). Startup fails if schema.sql or seed.sql has an error.
