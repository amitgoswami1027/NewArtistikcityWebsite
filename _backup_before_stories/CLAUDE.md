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

# Clear ALL site data (students, courses, orders, paintings, content); keeps staff logins, settings, FAQs and lists.
# Asks you to type WIPE, takes a full backup into backups\wipe-<date>\ first and moves uploads there (not deleted).
WIPE-SITE-DATA.bat                                        # = setup-and-run.ps1 -WipeData; SQL: database/maintenance/wipe-site-data.sql
RESTORE-SITE-DATA.bat                                     # = -RestoreData [-RestoreFrom backups\wipe-<date>]: undo a wipe (current data saved first)

# Rebuild the React bundle -> src/main/resources/static/js/app.js
cd frontend && npm install && npm run dev                 # or: npm run prod / npm run watch
```

Unit tests cover the auth crypto (`src/test/java/com/artistikcity/auth`: tokens, password policy, JWT/JWKS, WebAuthn), the
rich-text cleaner (`support/HtmlCleanerTest`), the video upload check (`admin/VideoUploadCheckTest`), S3 request signing against
AWS's published examples (`support/s3/SigV4Test`), the CloudFront filter (`http/CloudFrontFilterTest`), shop pricing (`shop/ShopPricingTest`) and
the unified orders endpoint (`orders/UnifiedOrdersEndpointTest`, MockMvc), the configuration panel's encryption and allow-list
(`config/panel/ConfigPanelTest`), SNS SMS requests (`auth/sms/SnsSmsSenderTest`) and the Studio Stories / site layout checks
(`admin/StudioAndLayoutTest`), the studio stage (`lifecycle/StudioStageTest`) and payout details checks (`selling/SellingTest`): `mvn test`.
`tests/system/test_selling.py` covers Sell your art end to end. `tests/system/test_admin_setup.py` (dev profile, fresh DB) proves 401/403 for non-admins on every
new admin API, secret masking, validation and commit of panel values, site settings and the Studio Stories workflow. Browser tests for the purchase flow live in `tests/e2e`.
The database race tests `marketplace/MarketplaceConcurrencyIT` (many buyers reserve / pay for one original at once: one wins,
the rest get 409 or REFUND_REQUIRED) and `seats/SeatRaceIT` (20 students, 5 seats: exactly 5 get one) run only with
`ARTISTIK_IT_DB_URL` set: `mvn test -Dtest=MarketplaceConcurrencyIT,SeatRaceIT` (the deploy workflow runs both).
React unit tests (Jest + React Testing Library, own React 17 copy) live in `tests/frontend`: `cd tests/frontend && npm install && npm test`.
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
  shop/         prints, room preview, artist hub, cart, shop orders (purchase flow)
  orders/       Your orders: one UnifiedOrder list from every order system (OrderSource beans), certificate of authenticity
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
tests/e2e/      Playwright browser tests (purchase flow, room preview, Your orders); run against the dev profile
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
- **Lost races:** MySQL Connector/J 8 can throw its own unchecked `CJException` through `JdbcTemplate` (deadlock victims,
  duplicate keys), so Spring never maps it to `ConcurrencyFailureException`. Decide with `support/DbErrors.isConcurrencyConflict`
  / `isDuplicateKey`, never by catching only Spring's types (see `MarketplaceService.reserve` / `finalizeSale`).
- `/api/**` is outside the global CSRF check (`SessionInterceptor`): every state-changing `/api` endpoint must call
  `Csrf.matches` itself (webhooks verify the gateway signature instead) and check who is signed in.
- Course catalogue filters are mirrored into the address bar (`Components/Site/catalogQuery.js`: comma-separated values,
  `history.replaceState` keeping Inertia's state), so refresh / Back / shared links keep the view.
- Users are unique by e-mail **and** by verified phone (`phone_number`, E.164 via `PhoneNumbers.normalize`). Google/Facebook
  identities live in `linked_accounts`. Merge accounts only through `AccountService`, which applies the verified-e-mail rules.
- Auth JSON endpoints (`web/AuthApiController`, `/auth/**`) return `{message, errors, retryAfter, reauth}` on failure. Changing
  sign-in methods needs a login younger than 30 minutes (403 `reauth`).
- **Student areas.** *My studio* uses `Components/Studio/StudioLayout` with five tabs (My studio, My courses, Submissions,
  Certificates & reports, Sell your art) and the seven-step journey Discover → Join → Choose a course → Learn → Submit → Certify → Sell
  (`Components/Studio/Journey.js` `JOURNEY`; the backend stage is `StudentStudioController.stage`: 7 once certified in any course).
  `/dashboard/portfolio` (the old portfolio page) redirects to `/dashboard`.
- **Sell your art** (`selling/StudentSellController`, `/dashboard/sell`, pages `Studio/Sell` + `Studio/SellListing`): a student certified in a
  course (certificate issued, or every requirement met: `certifiedCourses`) can list the *approved* submissions of that course as originals
  (`paintings.source = 'STUDENT'`, `artist_user_id`, `submission_id`, `shop_formats` NULL = no prints; one live listing per submission). The
  approved photo is the cover, extra photos go through `MarketplaceImages`; the student sets the price (`ListingInput` rules). Submitting puts it
  in the staff review queue (PENDING_REVIEW → Admin > Sales > Marketplace approve/reject with notes; "changes requested" = DRAFT with review notes).
  Editing a live listing sends it back to review; withdraw = DRAFT (clears the notes). The ledger pays the artist share to payee USER.
  API (CSRF-checked): `POST /api/studio/sell/listings`, `PUT .../listings/{id}`, `POST .../listings/{id}/withdraw`, `POST /api/studio/sell/images`,
  `POST /api/studio/sell/payout`. Moderation `SELLING` blocks it. **Payout details** (UPI / Indian bank / PayPal) live in `student_payout_details`,
  encrypted with the Configuration Panel key (`selling/PayoutDetails`, `PanelConfig.secretBox()`), shown masked; changing them needs a sign-in
  younger than 30 minutes (403 `reauth`; `/auth/reauthenticate?return_to=/dashboard/sell`). Finance staff (capability `revenue`) read them in
  Admin > Sales > Payouts & ledger (Artists tab / batch lines → "Payout details", `GET /admin/api/finance/payees/{userId}/payout-details`); each look is audited.
  *My course* is a focused view with no studio tabs (`web/user/CourseLearningController`, frame `Components/Course/CourseShell`):
  overview `/user/course/{order_id}` (`Pages/Course/Overview`), `/user/course/{order_id}/resume` (next unfinished lesson) and the learning
  view `/user/course/modules/{module_id}?lesson={id}` (`Pages/Course/Learn`: collapsible syllabus sidebar, prev/next across modules, mark
  complete via `POST /api/classroom/{courseId}/lessons/{lessonId}/complete`). Every course URL needs a paid order of the signed-in student.
  Old URLs (`/dashboard/classroom/{id}`, `/user/course/module/lesson/...`, `/user/course/syllabus/...`, `/user/workshop/...`) redirect there.
  Lesson tasks (`/user/course/module/lesson/task/{id}`) use the same frame via the `courseLinks` prop.
  *My account* (`Components/Account/AccountLayout`, list in `Components/Account/nav.js`) holds Profile, Your orders (`/user/orders`, page
  `Account/Orders`: every order system in one list through `orders/UnifiedOrderService`, see docs/marketplace-shop.md), Wish list, Addresses, Login & security, Notifications and Help. `Layouts/Authenticated` picks the shell from the URL.
  The public roadmap `/how-it-works` (`Pages/HowItWorks`) shows seven steps: the six of `JOURNEY` plus `SELL_STEP` (Sell your art, kept out of
  `JOURNEY` because the studio stage ends at Certify), with a process tracker right under the hero banner (`.hiw-tracker`, sticky below the header once reached; scrolls in code because of `<base href>`) and a
  zigzag timeline (`.hiw-*` in `ac-theme.css`). Step 7 quotes the real terms from `PagesController.howItWorks` (prop `sell`: artist share =
  100 - `app.ledger.commission-percent`, `payout-after-days`), so don't hard-code percentages in that copy.
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
- **Age groups** are a managed list (`age_groups`, Admin > Catalog > Age groups, `TaxonomyAdminController`, ordered by `sort_order`).
  A course picks one in the editor and `courses.age_group` stores the group's **name** (renaming a group renames it on its
  courses; a group in use can't be deleted). The list reaches React as `site.ageGroups` (`SiteSettings.ageGroups`, cached) and
  feeds the /courses Age filter (exact name match) and the header/footer "By age" menus via `useAgeGroups()` in `SiteHeader`.
- Site settings (`settings` table, tags `home`, `seo`, `contact`, `social`, `landing`, `nav`, `cookie`) are read through
  `admin/SiteSettings` (cached, invalidated on save) and edited in Admin > System parameters > SEO & Layout Controls
  (`/admin/setup/site-settings`, page `Admin/Setup/SiteSettings`: form + live desktop/mobile preview; `POST /admin/api/setup/site-settings`,
  only the groups sent are saved; `/admin/settings` redirects there). SEO values go into the root templates' `<head>` (`seo.*`; a controller
  can override them with the request attribute `Inertia.SEO_ATTRIBUTE`); contact, social (links + `hidden` list), landing hero, header menu
  (`nav.menu`, JSON list of {label, href, visible, accent}) and cookie banner reach React as the shared prop `site`. The public site reads
  them only through `Components/Site/siteContent.js` (defaults = the original texts/menu, so empty settings change nothing): `Welcome` hero,
  `SiteHeader` menu, `SiteFooter` icons, `CookieBanner` (cookie `ac_consent` = all | essential). Links must be a site path or https (`ContentAdminController.siteLink`).
- **Course videos are separate:** `courses.intro_video_url` is the trailer on the public course page; `course_module_lessons.video_url`
  is each lesson's own video in the learning view (no fallback to another video). Both hold a link or `/storage/uploads/courses/<id>/videos/<file>`,
  uploaded through `POST /admin/api/courses/{id}/videos` (streamed to disk, 500 MB). The old `course_videos` table is no longer used.
  Spring's multipart limit is 520 MB for that endpoint; every other form keeps 25 MB per file / 100 MB per form via `support/Input`.
- Course page section tabs (`Pages/CourseDetails.js`): the root template has `<base href="/">`, so in-page links must not be
  bare `#id` hrefs. The tabs scroll in code (`scrollToSection`) to just below the sticky header + tab bar.
- **Storage:** always go through `support/Storage` (`put`, `put(stream)`, `copy`, `exists`, `read`, `storeUpload`); never write
  files under the storage folder directly. `STORAGE_DRIVER=s3` keeps them in S3 (`support/s3/S3Client`: plain java.net.http +
  SigV4, credentials from env or the EC2 role via IMDSv2), same `/storage/...` URLs; `web/StorageController` serves them when no CDN does.
  `Storage.publicUrl(rel)` gives the address to save for new public files (absolute when `S3_PUBLIC_BASE_URL` is set) and
  `Storage.pathOf(url)` turns either form back into a storage path: use it instead of stripping `/storage/` by hand.
  Marketplace photos: `marketplace/MarketplaceImages` → `uploads/marketplace/yyyy/MM/<uuid>.jpg|png`.
- **Generated PDFs** (portfolio reports, course certificates): rendered in memory (OpenHTMLtoPDF into a byte array) and
  stored privately by `documents/StudentDocuments` (`private/documents/portfolios/<user>/…`, `private/documents/certificates/<no>.pdf`),
  one row each in `student_documents` (storage key, owner, kind). Students get them through short-lived links:
  a 10-minute presigned S3 GET (`S3Client.presignedGet`, `SigV4.presignGet`) or, with local storage, the owner-checked
  `/dashboard/documents/{id}/file`. Never link to `private/` keys any other way. PDF palette: `CredentialsService` constants
  (ink #2C3E50, soft #34495E, panels #F8F9FA / #ECF0F1); the cover is page 1 via `@page :first` (no named page: that forced a blank page).
- **Preview and frame** (`/purchase/step-2`, `Components/Shop/ArtPreview.js`): the artwork is the default, large view (lit wall,
  framer's dimension lines); the room photos come from `room_scenes`, managed in Admin > System parameters > Room Preview Setup (`/admin/setup/room-preview`, `rooms/RoomAdminController`,
  capability `marketplace.publish`): upload a photo (>= 1000 px wide), drag the hanging area (percent of the photo), optionally give its real
  width in inches (then paintings are drawn to scale), order / hide rooms, or switch the room step off (`settings` room_preview.enabled).
  The page adapts to 0..12 rooms. The artwork page's "On a wall" tab uses the same component. `RoomStage.js` / `rooms.js` are no longer used.
- **AWS** (docs/AWS-DEPLOY.md): CloudFront → EC2 (Docker) + RDS + S3, `infra/*.yml` (CloudFormation), `.github/workflows/deploy.yml`,
  `deploy/ec2-deploy.sh` (deploy/rollback/status, runs from inside the image) and `deploy/db-migrate.sh` (schema on an empty
  DB, else pending migrations - same rules as the launcher). `http/CloudFrontFilter` (`TRUST_CLOUDFRONT`) takes the visitor IP and
  https from CloudFront headers and rejects requests without `X-Origin-Verify`. `GET /healthz` is the health check.
  `http/PreviewLockFilter` (`SITE_PREVIEW_PASSWORD`): while set, every page asks for that password (cookie `ac_preview`, 30 days),
  responses carry `X-Robots-Tag: noindex` and robots.txt disallows all; `/healthz`, the payment webhooks and static files stay open.
  Migrations must stay compatible with the previous release (rollback only swaps the image).
- **Marketplace collections** (`Pages/Marketplace/Index.js`): `/marketplace` shows **artist originals** (every source except STUDENT) by
  default; a toggle switches to **student paintings** (`source = 'STUDENT'`, listed by certified students from My studio > Sell your art),
  mirrored as `?source=student` (`/student-shop` redirects there). Cards and the artwork page label student work "Student painting" and the
  rest "Artist original". Filtering is client-side over `publicListings()`.
- **Marketplace shop** (package `shop/`, docs/marketplace-shop.md): every listing can also sell a numbered canvas edition, a poster and a
  digital download (`paintings.shop_formats`), priced from the global list `shop_price_options` by `ShopPricing` (the one source of prices;
  `Components/Shop/shop.js` only mirrors it for live UI). Four screens: the artwork page (buy box `Components/Shop/BuyPanel`: format, size,
  price, "Talk to the artist") → `/purchase/step-2` preview and frame (`Components/Shop/ArtPreview`, rooms from Admin > System parameters > Room Preview Setup) → `/purchase/checkout` (accordion contact/delivery/
  payment, sticky summary) → `/purchase/orders/{id}`. `/purchase/step-1` and `/purchase/preview` only redirect. The choice is the session draft
  `purchase.draft`. There is no cart in the UI: "Buy now" saves the draft and `/purchase/buy-now` makes it the only line of the
  basket (`CartService.replaceWith`; `shop_carts` per owner key `U:<id>`, JSON API `/api/v1/cart/**` kept for the checkout's pricing and
  coupons, client store `Components/Shop/CartStore`). An original in the cart is a 15-minute hold (one at a time). Orders: `shop_orders` / `shop_order_items`, finalised by
  `ShopOrderService.finalizeSale` (idempotent; lines that can't be supplied become REFUND_REQUIRED with `refund_due_inr`); the marketplace
  webhook also resolves shop orders. Downloads stream from private storage (`Storage.putPrivate/openPrivate`, never /storage/**) at
  `/shop/download/{token}`. Admin: one console, Sales > Marketplace (`/admin/dashboard/marketplace`: paintings, review, holds, orders, messages, prices, coupons;
  `MarketplaceAdminController` + `ShopAdminController`, sections in `Components/Admin/ShopSections`; `/admin/dashboard/shop` only redirects). The old single-original checkout
  page now forwards into the cart checkout. Browser tests: `tests/e2e` (Playwright, against the dev profile).
- Blog posts ("Studio Stories") are public only when `status = 1`; drafts 404 for visitors (admins can preview). Status: 0 draft, 1 published,
  2 archived, 3 in review, 4 scheduled (`publish_at`; `BlogAdminController.publishDue` publishes due ones every minute). CMS: Admin > Content >
  Studio Stories (`/admin/content/stories`, `/new`, `/{id}`; page `Admin/Content/StudioStories` = dashboard or editor), JSON API
  `GET/POST /admin/api/content/stories`, `POST|PUT /admin/api/content/stories/{id}`, `POST .../{id}/workflow` (old `/admin/api/posts` kept,
  `/admin/blog/post*` redirect). Views: `BlogController.countView` (one per visitor session; admins and bots not counted) -> `posts.views` +
  daily `post_views`; `read_minutes` = words / 200. `/faq` reads the `faqs` table.
  Order `payment_status`: 0 pending, 1 paid, 2 failed, 3 refunded; course access needs 1.
- Wish list: table `wishlist_items` (type `course` or `painting`), `POST /wishlist {type, id, saved}`, page `/user/wishlist`
  (`Pages/Students/Wishlist.js`), and the heart button `Components/Wishlist/SaveButton.js`.
- Use Java time (`LocalDateTime.now()`) for every timestamp, and pass `created_at` explicitly. Don't rely on MySQL `NOW()` or
  column defaults, because the DB server may run in a different time zone than the app.
- Payments only mark an order paid after a **verified gateway response**. The dev profile / `PAYMENT_TEST_MODE=true`
  enables simulated payments when no keys are set. On the live site only **test buyers** may simulate (`payments/TestPayments`):
  a super admin ticks "Test buyer" on an account in Admin > People (`users.test_buyer`, audited); its checkouts offer "Simulate payment"
  and the orders get `is_test = 1` (courses, marketplace, shop, commissions), which revenue figures, the ledger and the reconciler skip.
  `PAYMENTS_TEST_BUYERS=false` switches the feature off.
- **Buying needs an account.** "Enroll", "Buy now", the checkouts and their payment APIs send a guest to `/login?return_to=…&intent=buy`
  (`web/PurchaseIntent`: the log-in / join page names what they are buying); JSON endpoints answer 401 `{code: SIGN_IN, redirect}`.
  A guest choosing an original is not given a hold; it is taken after sign-in (`PurchaseController.holdAfterSignIn`).
- The header "Courses" is one link to `/courses?type=all` (no dropdown); the course menu shows Format as buttons
  (All formats / Live courses / Workshops with counts) above the Age pills.
- **Configuration: one central file, `config/artistikcity.yml`** (packaged into the jar as defaults; `./config/artistikcity.yml`
  next to the running app overrides it; environment variables override single values through each `${ENV:default}`).
  The `dev` profile is its last section. New settings go there, with a comment and an env name, and are read through
  `config/Settings` (typed getters), never as raw strings in feature code. `application.properties` only imports it.
  Never commit `.env` or real keys (README §4, `.env.example`).
- **Configuration Panel** (Admin > System parameters, `/admin/setup`, capability `setup` = super admin only; `config/panel/`):
  payment, SMS and Google sign-in settings stored in `config_overrides`. `PanelConfig` adds them as the **first** Spring property
  source, so they win over Parameter Store / env and the yml, and features see them at once because they read `env.getProperty` at call
  time (keep it that way: no caching of these values in fields). Only keys in `ConfigCatalog` (the allow-list, with format checks) can be
  set. Secrets are AES-256-GCM encrypted (`SecretBox`, key from `CONFIG_ENCRYPTION_KEY`, else `OTP_SECRET`), are sent to the browser only as
  `ConfigCatalog.MASK`, and sending the mask (or an empty box) back keeps them; `reset` gives a key back to the server value. The audit
  snapshot shows secrets as "(secret, saved <time>)". SMS goes through `auth/sms/DynamicSmsSender` (log | twilio | sns | infobip, picked per send).
  Stripe keys are stored only (not wired to the checkout). API: `GET/POST /admin/api/setup/config`, `POST /admin/api/setup/sms-test`.
- The admin sidebar group **System Parameters** (`AdminLayout` NAV, `collapsible: true`) holds Configuration Panel, Room Preview Setup,
  SEO & Layout Controls and the Security log; it opens by itself on its pages and remembers its state (`ac.admin.groups`).
- **Security log** (`security/AuditLog`, Admin > Setup > Security log, capability `security`): every `Audit.log` also writes the
  append-only, hash-chained `audit_logs` (actor, action type CREATE/UPDATE/DELETE/AUTH/EXPORT/DENIED, resource, IP + CloudFront
  country/city). Use `audit.change(admin, entity, id, action, details, before, after)` with `audit.snapshot(table, id)` for
  updates, so the drawer can show the JSON diff. Never update or delete `audit_logs` rows ("Verify integrity" re-hashes the chain).
  `security/SecurityWatch` logs every 401/403 under `/admin/**` as DENIED, signs out a staff session after 5 refusals in 10 minutes
  and any student session that calls the admin API; the admin guard answers 401 JSON `{reauth}` for `/admin/api/**`.
- **Payments** (`payments/`): `PaymentReconciler` asks Razorpay (`GET /v1/orders/{id}/payments`) / PayPal about orders still
  waiting (10 min - 7 days old) every 5 minutes and completes them through the same idempotent code as the webhooks. Refunds are
  never automatic: `RefundQueue.flag` queues them (course paid twice -> `orders.payment_status` 4, sold-out seat, cancelled course;
  marketplace/shop REFUND_REQUIRED rows are synced in), staff confirm each in Admin > Sales > Refunds, which calls the gateway.
- **Seats** (`seats/SeatService`): `courses.seat_limit` caps live courses; checkout holds a seat (15 min) under a row lock on the
  course, 409 `SOLD_OUT` when full; waitlist invitations and staff priority links are `access_grants` (only the SHA-256 is stored).
- **Course notices** (`notices/`): course editor > Students: message enrolled students (studio notice + e-mail + delivery log),
  move the start date, cancel the run (refunds queued, waitlist closed).
- **Ledger** (`ledger/LedgerService`): append-only `ledger_entries` with idempotency keys; each artwork sale splits into
  commission (`app.ledger.commission-percent`) and artist share (payable after `payout-after-days`); weekly draft payout batches
  staff mark paid; open disputes (Razorpay `payment.dispute.*` or manual) hold the artist's share. Admin > Sales > Payouts & ledger.
- **Moderation** (`moderation/`): `account_restrictions` (BAN ends sessions and blocks sign-in in `Auth.login`; POSTING / SELLING
  checked with `Moderation.assertAllowed`) and the Report button -> `content_reports` queue (Admin > Community > Reports & bans).
- **Course filters** (`Pages/Courses.js`): Medium tabs, Format buttons and Age pills visible, Level / Subject in the "More filters" drawer,
  `catalogQuery.fuzzyScore` search (accents, small typos), removable chips, zero-results "one change brings courses back".
  Filter lists can be nested (`parent_id`) and archived (`archived_at`) in Admin > Catalog; the editor's genres use `TagPicker`.
- Public pages: `SiteLayout` wraps content in an error boundary, opens a sign-in sheet on any 401 (`SignInSheet`), shows
  skeletons for slow visits and an Undo toast (`showUndo`). Brand colour for text/buttons is #9C0D48 and muted text #4A5866
  (WCAG AAA); a global `:focus-visible` ring is defined at the end of `ac-theme.css`.
- Reference only: `docs/sqlserver-ddl.sql` (SQL Server version of courses / taxonomy / audit log), `frontend/resources/js/types/catalog.d.ts`,
  `docs/prototype/` (one-file React + Tailwind prototype of the filters, tag manager and audit viewer).
- `logs/`, `target/`, `.tools/` and `frontend/node_modules/` are local-only and git-ignored.
