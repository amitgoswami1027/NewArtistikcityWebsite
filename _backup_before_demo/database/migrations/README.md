# Database migrations (MySQL)

`database/schema.sql` is the complete, current schema. A fresh install only needs `schema.sql` and then `seed.sql`.

To change the schema of a database that already holds real data:

1. Add a dated script here, for example `2026-11-02_add_phone_verified.sql`. Write it in MySQL 8 syntax and make it
   safe to run twice. Use `CREATE TABLE IF NOT EXISTS`. For columns and indexes, check `information_schema` first,
   because MySQL has no `ADD COLUMN IF NOT EXISTS`.
2. Make the same change in `schema.sql`, so fresh installs match.
3. Apply it. On Windows, `START-ArtistikCity.bat` does this for you: it runs every script that is not yet listed in the
   `schema_migrations` table, in file-name order, and records it there. (A fresh install records all scripts as already
   applied, because `schema.sql` already contains them.) By hand, or in Docker with an existing volume:
   `mysql -u artistik -p artistikcity < database/migrations/<file>.sql`

| Script | What it does |
|---|---|
| `2026-10-23_auth_system.sql` | Sign-in system: `users.password` → `password_hash` (nullable), `phone` → `phone_number` (E.164, unique) + `phone_verified_at`, `is_verified`; moves `provider`/`provider_id` into `linked_accounts`; adds `auth_sessions`, `auth_refresh_tokens`, `auth_otps`, `auth_tokens`, `webauthn_credentials`, `auth_events`; drops `password_resets`. When two accounts shared one phone number, the oldest keeps it. |
| `2026-10-23_wishlist.sql` | Adds `wishlist_items` (saved courses/workshops and marketplace originals). |
| `2026-10-24_course_checkout.sql` | Adds `orders.gateway_ref` (the Razorpay / PayPal order id behind each payment) and indexes for the checkout. |
| `2026-10-25_admin_console.sql` | Admin console: curriculum order (`sort_order` on modules and lessons), blog SEO fields (`posts.meta_title`, `meta_description`; `subtitle` widened to 500), newsletter subscriber state (`source`, `unsubscribed_at`) and `newsletter_campaigns`, `faqs` (with starter questions), `contacts.read_at`, `admin_audit`, and the SEO / contact / social rows in `settings`. |

The earlier SQL Server (T-SQL) migrations were removed when the project moved to MySQL. Everything they did is
already in `schema.sql`, and they remain available in git history.
