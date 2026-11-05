# Database migrations (MySQL)

`database/schema.sql` is the complete, current schema. A fresh install only needs `schema.sql` and then `seed.sql`.

To change the schema of a database that already holds real data:

1. Add a dated script here, for example `2026-11-02_add_phone_verified.sql`. Write it in MySQL 8 syntax and make it
   safe to run twice. Use `CREATE TABLE IF NOT EXISTS`. For columns and indexes, check `information_schema` first,
   because MySQL has no `ADD COLUMN IF NOT EXISTS`.
2. Make the same change in `schema.sql`, so fresh installs match.
3. Apply it: `mysql -u artistik -p artistikcity < database/migrations/<file>.sql`

The earlier SQL Server (T-SQL) migrations were removed when the project moved to MySQL. Everything they did is
already in `schema.sql`, and they remain available in git history.
