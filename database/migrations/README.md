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
| `2026-10-26_enrollment_refunded.sql` | Lets `enrollments.status` be `Refunded` (used when an admin records a refund). |
| `2026-10-27_course_lesson_videos.sql` | Separate videos: `courses.intro_video_url` (trailer on the course page) and `course_module_lessons.video_url` (each lesson's own video). Copies the old shared `course_videos` list over once: first video to the intro, video N to lesson N. |
| `2026-10-28_marketplace_shop.sql` | Marketplace shop: `paintings.shop_formats` (prints sold besides the original), `edition_size` / `editions_sold`, `hires_path` / `hires_name` (private download file) and artist contact links; tables `shop_price_options` (price list, with starter prices), `shop_carts`, `shop_cart_items`, `shop_coupons` (with `WELCOME10`), `shop_orders`, `shop_order_items`, `artist_threads`, `artist_thread_messages`. Existing listings start out selling canvas and poster prints. |
| `2026-11-05_documents_s3.sql` | `student_documents` (every generated portfolio report and course certificate PDF, with its private S3 key); widens `shop_order_items.image_url`, `student_submissions.file_url` and `certificates.certificate_path` for full S3 / CDN URLs and keys. |
| `2026-11-05_age_groups.sql` | Table `age_groups` (Admin > Catalog > Age groups) with the three groups the menus used so far; existing courses' hand-typed ages ("12+ years") are put in the matching group by their first number. |
| `2026-11-05_security_audit.sql` | Security log: append-only, hash-chained `audit_logs` (every staff change with before/after JSON, sign-ins, exports, refused requests) and `audit_chain_head`. |
| `2026-11-05_payments_refunds.sql` | `payment_refunds` (the refunds queue staff confirm in Admin > Sales > Refunds) and `payment_reconciliations` (payments the scheduled gateway check matched late); `orders.payment_status` 4 = refund due. |
| `2026-11-05_seats_waitlist.sql` | `courses.seat_limit`, `seat_holds` (15-minute holds while paying, then PAID), `course_waitlist`, `access_grants` (hashed priority / waitlist links). |
| `2026-11-05_course_messages.sql` | `courses.cancelled_at` / `cancel_reason`, `course_messages` and `course_message_deliveries` (studio notice + e-mail status + read receipt per student). |
| `2026-11-05_ledger_payouts.sql` | Escrow ledger: append-only `ledger_entries` (sale, commission, artist share, refund, payout, dispute loss), `payout_batches`, `payout_lines`, `ledger_disputes`. |
| `2026-11-05_moderation.sql` | `account_restrictions` (ban / no posting / no selling, with reason and end date) and `content_reports` (Report button queue with reason codes). |
| `2026-11-05_taxonomy_hierarchy.sql` | Tag & hierarchy manager: `parent_id` (nesting) and `archived_at` on `mediums`, `genres`, `skills` and `age_groups`. |
| `2026-11-06_test_buyers.sql` | Test-buyer accounts: `users.test_buyer`, and `is_test` on `orders`, `marketplace_orders`, `shop_orders` and `commission_orders` (simulated payments, kept out of revenue, ledger and payouts). |
| `2026-11-06_room_scenes.sql` | Room photos for the room preview (`room_scenes`, managed in Admin › Room preview) with the four original rooms, and the `room_preview.enabled` setting. |
| `2026-11-06_admin_setup.sql` | Configuration panel values (`config_overrides`, secrets encrypted by the app); Studio Stories workflow and analytics (`posts.author_id`, `publish_at`, `published_at`, `archived_at`, `views`, `read_minutes`, daily `post_views`). |
| `2026-11-06_student_selling.sql` | `student_payout_details` (where a student artist's marketplace earnings are paid: method, holder, encrypted details, masked summary) for My studio › Sell your art. |

The earlier SQL Server (T-SQL) migrations were removed when the project moved to MySQL. Everything they did is
already in `schema.sql`, and they remain available in git history.

| `2026-11-09_landing_site_pages.sql` | Information landing pages (no course): `landing_pages.kind` (`COURSE` / `SITE`), `course_id` now nullable; also adds `site_route` + its index (not used any more). Additive; no rows changed. The three showcase pages are then created by the app through the landing module (not by SQL). |
| `2026-11-10_landing_collation.sql` | Gives the landing tables the text collation of the rest of the database (the `orders` table's) and sets the database default to it. Fixes "Illegal mix of collations" (Error 500 on Admin › Landing Page Management) on databases loaded from a dump, e.g. AWS RDS imported from a PC. Does nothing where everything already matches. |
| `2026-11-11_landing_quick_signin.sql` | Quick sign-in on landing pages: `auth_email_codes` (six-digit sign-in codes sent by e-mail, stored as an HMAC). |
| `2026-11-13_admin_mfa.sql` | Two-step verification for staff: `admins.mfa_secret` (encrypted TOTP secret), `mfa_enabled_at`, `mfa_last_step`, `mfa_changed_at`, and `admin_mfa_recovery_codes` (hashes only). |
| `2026-11-12_landing_login_verify.sql` | Login & verification: `landing_checkout.verify_with` (how a page's buyers are verified when they pay; NULL = the default). |

**Collations.** A database loaded from a dump keeps each table's own collation, while tables created later by a migration get
the database default. Mixed collations make MySQL refuse joins / UNIONs on text columns. `2026-11-10_landing_collation.sql`
aligns the landing tables and the database default with `orders`; new migrations that create tables inherit that default.
