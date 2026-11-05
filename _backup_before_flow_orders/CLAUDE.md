# ArtistikCity

Online art school: live courses, workshops, a student studio, an original-art marketplace and art commissions.
This is a Java port of a Laravel 8 + Inertia + React app. The React pages were kept as they were, and the backend
speaks the Inertia protocol to them.

- **Backend:** Java 17, Spring Boot 3.3, plain JDBC through the `Db` helper. There is no JPA/Hibernate.
- **Database:** MySQL 8.4 LTS. On Windows the launcher runs a portable copy from `.tools\` (no install needed).
- **UI:** React 17 + Inertia (`frontend/`), plus Mustache server-side templates for static pages, the admin sign-in page, certificates and e-mails.
- **Platform:** the developer works on Windows. The `.bat` and `.ps1` launchers are the user-facing entry points.

## Commands

```bash
# Windows one-click: downloads JDK 17 / Maven / MySQL 8.4 into .tools\ if missing, sets up the DB
# (first run loads schema.sql + seed.sql; later runs keep the data), starts MySQL + the site
START-ArtistikCity.bat                                    # http://localhost:8080
START-ArtistikCity.bat -ResetDatabase                     # drop all tables, reload demo data
START-MySQL.bat / STOP-MySQL.bat                          # only the database (setup-and-run.ps1 -DatabaseOnly / -StopDatabase)

# With MySQL running (defaults: 127.0.0.1:3306 / artistikcity / artistik / Artistik@2026)
mvn spring-boot:run -Dspring-boot.run.profiles=dev        # dev = no template cache + test payments

mvn -B package -DskipTests                                # -> target/artistikcity-1.0.0.jar
java -jar target/artistikcity-1.0.0.jar --spring.profiles.active=dev   # run from the repo root

# Full stack (MySQL 8.4 + app) in Docker; first start loads schema + seed into the empty volume
docker compose up -d --build
docker compose down -v && docker compose up -d            # wipe and reload demo data

# Rebuild the React bundle -> src/main/resources/static/js/app.js
cd frontend && npm install && npm run dev                 # or: npm run prod / npm run watch
```

Unit tests cover the auth crypto (`src/test/java/com/artistikcity/auth`: tokens, password policy, JWT/JWKS, WebAuthn), the
rich-text cleaner (`support/HtmlCleanerTest`), the video upload check (`admin/VideoUploadCheckTest`), S3 request signing against
AWS's published examples (`support/s3/SigV4Test`) and the CloudFront filter (`http/CloudFrontFilterTest`): `mvn test`.
For everything else, run the app against MySQL and check the pages by hand. Use the demo logins below.

**Local database access:** `127.0.0.1:3306`, database `artistikcity`, app user `artistik` / `Artistik@2026`,
root `root` / `ArtistikRoot@2026`. Client: `.tools\mysql-8.4.13-winx64\bin\mysql.exe -h 127.0.0.1 -u artistik -p artistikcity`.

**Demo logins** (password `password`): student `student@artistikcity.com` at `/login`, admin `admin@artistikcity.com`
at `/admin/login`, teacher `vaishali@artistikcity.com` at `/admin/login`.
Optional demo pack `database/demo/demo-data.sql` (`LOAD-DEMO-DATA.bat` / `-DemoData`; generated, idempotent, ids 100+/1000+):
teachers `ananya|kabir|meera|rohit|sana@artistikcity.com`, students `ishaan|neha|arjun|sophia|rahul|emily@example.com`,
courses 101-113, workshops 121-126, orders 1001-1030. Same password. Its images are under `storage/uploads`.

## Layout

```
src/main/java/com/artistikcity/
  config/       WebConfig (interceptors, /storage/** mapping), MustacheConfig
  inertia/      Inertia (server adapter) and Routes (named-route table, published to the browser as Ziggy)
  http/         Auth (web + admin guards), SessionInterceptor, Csrf, GuardInterceptors, Flash, Redirects, exceptions
  auth/         student sign-in: SessionService (DB sessions + refresh tokens), AccountService (one user, many providers),
                OtpService + sms/, EmailTokenService (magic links, verify, reset), JwtVerifier, webauthn/ (passkeys), RateLimiter
  support/      Db, Row, Input, Validator, Paginator, Storage, Json, Slugs, Str
  view/         Views (Mustache rendering) and ViewHelpers
  service/      CourseService, PostService, CertificateService, PaymentGateways (Razorpay/PayPal), Mailer
  web/          public site controllers;  web/user/ student area;  web/admin/ admin sign-in;  web/api/ JSON API
  admin/        admin console: AdminKit (page props, JSON answers, upload checks), Grid (server-side tables), Audit,
                Course/Order/Newsletter/Blog/Taxonomy/Content/People/FreeCourse controllers, SiteSettings, NewsletterService
  lifecycle/    student studio, credentials and admin console overview (learn -> submit -> sell)
  marketplace/  1-of-1 original-art marketplace (listings, cart reservations, admin review)
  commission/   commission-art requests, pricing and payments
src/main/resources/
  templates/    Mustache: inertia/ (root HTML for React), pages/, admin/login, emails/, certificates/
  static/       compiled assets. static/js/app.js is BUILD OUTPUT, so do not edit it by hand
  ziggy/        route.js used by the React route() helper
frontend/resources/js/   React source: Pages/, Components/, Layouts/, Interface/
database/       schema.sql (drops + recreates everything), seed.sql (demo data), migrations/ (dated MySQL scripts), demo/ (optional demo pack)
deploy/         ec2-deploy.sh, db-migrate.sh (AWS server scripts, shipped inside the image)
infra/          CloudFormation: certificate.yml (us-east-1), artistikcity.yml (ap-south-1)
docs/           AWS-DEPLOY.md, awsarchitecture.md, marketplace-shop.md
storage/        uploaded files, served at /storage/**  (storage/certificates is git-ignored)
storage-private/ paid download files (git-ignored, never served directly; S3 key prefix private/)
```

## Conventions and gotchas

- **SQL is MySQL 8.** Page with `LIMIT n OFFSET m` at the end of the statement. Quote reserved words
  (`primary`, `like`, `order`, `key`, …) in backticks, and use MySQL functions (`DATE_ADD`, `CONCAT`, …). Backslashes in
  string literals are escape characters. Always pass parameters through `db.select/first/update(sql, params…)`,
  and never concatenate input into SQL.
- `Db` returns `Row` maps with **lower-case column names**. Dates are serialized as Laravel strings
  (`yyyy-MM-dd HH:mm:ss`), and the React pages depend on that exact JSON shape.
- **Schema changes:** update `database/schema.sql` (fresh installs) **and** add a dated, re-runnable MySQL script to
  `database/migrations/` for databases that already hold data (see its README; MySQL has no `ADD COLUMN IF NOT EXISTS`).
  Update `seed.sql` if demo data needs it. Types: `BIGINT AUTO_INCREMENT` keys, `VARCHAR(n)` / `LONGTEXT`, `DATETIME`,
  `TINYINT(1)` for booleans (read back as Java `Boolean`), `DOUBLE`/`DECIMAL` for money.
- **New routes:** add the Spring mapping **and** register the name/URI in `inertia/Routes.java`, so that React's
  `route('name')` and the Java `routes.path("name", params…)` both work. Route names match the old Laravel names.
- **Rendering:** for React pages, use `inertia.render(request, "Folder/Page", props)`. The page lives at
  `frontend/resources/js/Pages/Folder/Page.js`, and the bundle must be rebuilt after JS changes.
  For server-rendered pages, use `views.page(request, "admin/…", model)` with a `.mustache` file under `templates/`.
- Forms using PATCH or DELETE send `_method` (the hidden-method filter is on). Every POST needs the CSRF token: `_token`
  field or `X-XSRF-TOKEN`/`X-CSRF-TOKEN` header.
- Auth has two guards. `web` (students) uses DB sessions in `auth_sessions` via the `ac_session`/`ac_refresh` cookies
  (`auth/SessionService`, resolved by `AuthSessionFilter`). `admin` (admins/teachers) uses the HttpSession. Passwords are bcrypt
  in `users.password_hash` (nullable: passwordless accounts exist) and compatible with Laravel `$2y$`. Read the user with
  `auth.user(request)`, which never contains secrets. Start a login only through `auth.login(request, userId, method, remember)`.
- Users are unique by e-mail **and** by verified phone (`phone_number`, E.164 via `PhoneNumbers.normalize`). Google/Facebook
  identities live in `linked_accounts`. Merge accounts only through `AccountService`, which applies the verified-e-mail rules.
- Auth JSON endpoints (`web/AuthApiController`, `/auth/**`) return `{message, errors, retryAfter, reauth}` on failure. Changing
  sign-in methods needs a login younger than 30 minutes (403 `reauth`).
- **Student areas.** *My studio* is for learning only. It uses `Components/Studio/StudioLayout` with five tabs (My studio, My courses,
  Submissions, Portfolio & shop, Certificates & reports) and the journey Discover → Join → Choose a course → Learn → Submit → Certify →
  Showcase (`Components/Studio/Journey.js`; the backend stage is `StudentStudioController.stage`).
  *My course* is a focused view with no studio tabs (`web/user/CourseLearningController`, frame `Components/Course/CourseShell`):
  overview `/user/course/{order_id}` (`Pages/Course/Overview`), `/user/course/{order_id}/resume` (next unfinished lesson) and the learning
  view `/user/course/modules/{module_id}?lesson={id}` (`Pages/Course/Learn`: collapsible syllabus sidebar, prev/next across modules, mark
  complete via `POST /api/classroom/{courseId}/lessons/{lessonId}/complete`). Every course URL needs a paid order of the signed-in student.
  Old URLs (`/dashboard/classroom/{id}`, `/user/course/module/lesson/...`, `/user/course/syllabus/...`, `/user/workshop/...`) redirect there.
  Lesson tasks (`/user/course/module/lesson/task/{id}`) use the same frame via the `courseLinks` prop.
  *My account* (`Components/Account/AccountLayout`, list in `Components/Account/nav.js`) holds Profile, Your orders (`/user/orders`, page
  `Account/Orders`), Wish list, Addresses, Login & security, Notifications and Help. `Layouts/Authenticated` picks the shell from the URL.
  The header "My studio" is a plain link, and the avatar menu lists the account pages, with counts from the shared prop `nav`.
- The Tailwind CSS (`static/css/commission.css`) is pre-built and only contains the classes already in use, scoped to `.tw`. A new
  utility class does nothing until the CSS is rebuilt, so new pages use classes that already exist or small custom classes in `ac-theme.css`.
- **Admin console** (everything under `/admin`, admin guard). One React shell: `Components/Admin/AdminLayout` (grouped,
  collapsible sidebar filtered by persona capabilities, breadcrumbs, account menu + Log out top right), UI kit
  `Components/Admin/kit.js` (Card, Button, Badge, DataTable + `useGrid`, Pagination, SearchBox, FilterSelect, Segments,
  Drawer, `useConfirm`) and form kit `Components/Admin/form.js` (`useForm` with inline rules + server errors, FormSection,
  fields, Toggle, ChoiceChips, SlugField, TagInput, ListEditor, DropZone, RichText, SaveBar). Pages are `Pages/Admin/*`.
  Backend: render with `kit.page(request, capability, "Admin/X", () -> props)`; lists use `Grid` (query string
  `q, sort, dir, page, per` + named filters; sort keys and filters are whitelisted) and return `{rows, meta}`; actions are
  JSON under `/admin/api/**` and fail with `kit.invalid(errors)` = 422 `{message, errors: {field: text}}`, which the
  forms show inline. Check the persona capability (`Personas`) on every API call; instructors only see their own
  courses (`kit.teacherScope`). Rich text is saved through `support/HtmlCleaner` (allow-list); uploads go through
  `AdminKit.checkImage/checkPdf`. Important changes are written to `admin_audit` (`Audit.log`).
- Site settings (`settings` table, tags `home`, `seo`, `contact`, `social`) are read through `admin/SiteSettings` (cached,
  invalidated on save). SEO values go into the root templates' `<head>` (`seo.*`; a controller can override them with the
  request attribute `Inertia.SEO_ATTRIBUTE`), contact/social reach React as the shared prop `site`.
- **Course videos are separate:** `courses.intro_video_url` is the trailer on the public course page; `course_module_lessons.video_url`
  is each lesson's own video in the learning view (no fallback to another video). Both hold a link or `/storage/uploads/courses/<id>/videos/<file>`,
  uploaded through `POST /admin/api/courses/{id}/videos` (streamed to disk, 500 MB). The old `course_videos` table is no longer used.
  Spring's multipart limit is 520 MB for that endpoint; every other form keeps 25 MB per file / 100 MB per form via `support/Input`.
- Course page section tabs (`Pages/CourseDetails.js`): the root template has `<base href="/">`, so in-page links must not be
  bare `#id` hrefs. The tabs scroll in code (`scrollToSection`) to just below the sticky header + tab bar.
- **Storage:** always go through `support/Storage` (`put`, `put(stream)`, `copy`, `exists`, `read`, `storeUpload`); never write
  files under the storage folder directly. `STORAGE_DRIVER=s3` keeps them in S3 (`support/s3/S3Client`: plain java.net.http +
  SigV4, credentials from env or the EC2 role via IMDSv2), same `/storage/...` URLs; `web/StorageController` serves them when no CDN does.
- **AWS** (docs/AWS-DEPLOY.md): CloudFront → EC2 (Docker) + RDS + S3, `infra/*.yml` (CloudFormation), `.github/workflows/deploy.yml`,
  `deploy/ec2-deploy.sh` (deploy/rollback/status, runs from inside the image) and `deploy/db-migrate.sh` (schema on an empty
  DB, else pending migrations - same rules as the launcher). `http/CloudFrontFilter` (`TRUST_CLOUDFRONT`) takes the visitor IP and
  https from CloudFront headers and rejects requests without `X-Origin-Verify`. `GET /healthz` is the health check.
  Migrations must stay compatible with the previous release (rollback only swaps the image).
- **Marketplace shop** (package `shop/`, docs/marketplace-shop.md): every listing can also sell a numbered canvas edition, a poster and a
  digital download (`paintings.shop_formats`), priced from the global list `shop_price_options` by `ShopPricing` (the one source of prices;
  `Components/Shop/shop.js` only mirrors it for live UI). Purchase tunnel `/purchase/step-1?artwork=<slug>` → `step-2` (room visualiser,
  `Components/Shop/RoomStage`) → `preview` (estimate, coupon, "Meet the artist" `ArtistHub`) → `checkout` (whole cart); the step's choice is
  the session draft `purchase.draft`. Cart: `shop_carts` per owner key (`U:<id>` / guest `S:<token>`, same keys as holds, guest cart merged
  on sign-in), JSON API `/api/v1/cart/**`, client store `Components/Shop/CartStore` (module-level, no provider) + header bag/drawer
  `CartDrawer`. An original in the cart is a 15-minute hold (one at a time). Orders: `shop_orders` / `shop_order_items`, finalised by
  `ShopOrderService.finalizeSale` (idempotent; lines that can't be supplied become REFUND_REQUIRED with `refund_due_inr`); the marketplace
  webhook also resolves shop orders. Downloads stream from private storage (`Storage.putPrivate/openPrivate`, never /storage/**) at
  `/shop/download/{token}`. Admin: Sales > Art shop (`/admin/dashboard/shop`, `ShopAdminController`). The old single-original checkout
  page now forwards into the cart checkout.
- Blog posts are public only when `status = 1`; drafts 404 for visitors (admins can preview). `/faq` reads the `faqs` table.
  Order `payment_status`: 0 pending, 1 paid, 2 failed, 3 refunded; course access needs 1.
- Wish list: table `wishlist_items` (type `course` or `painting`), `POST /wishlist {type, id, saved}`, page `/user/wishlist`
  (`Pages/Students/Wishlist.js`), and the heart button `Components/Wishlist/SaveButton.js`.
- Use Java time (`LocalDateTime.now()`) for every timestamp, and pass `created_at` explicitly. Don't rely on MySQL `NOW()` or
  column defaults, because the DB server may run in a different time zone than the app.
- Payments only mark an order paid after a **verified gateway response**. The dev profile / `PAYMENT_TEST_MODE=true`
  enables simulated payments when no keys are set.
- Configuration comes from environment variables (see README §4 and `.env.example`). Never commit `.env` or real keys.
- `logs/`, `target/`, `.tools/` and `frontend/node_modules/` are local-only and git-ignored.
