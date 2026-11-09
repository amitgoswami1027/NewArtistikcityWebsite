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
AWS's published examples (`support/s3/SigV4Test`), the CloudFront filter (`http/CloudFrontFilterTest`), shop pricing incl. custom sizes (`shop/ShopPricingTest`), landing page pricing and config (`landing/LandingPricingTest`, `landing/LandingSiteConfigTest`) and
the unified orders endpoint (`orders/UnifiedOrdersEndpointTest`, MockMvc), the configuration panel's encryption and allow-list
(`config/panel/ConfigPanelTest`), SNS SMS requests (`auth/sms/SnsSmsSenderTest`) and the Studio Stories / site layout checks
(`admin/StudioAndLayoutTest`), admin MFA (`security/mfa/TotpTest`: RFC 6238 vectors, QR structure), the studio stage (`lifecycle/StudioStageTest`) and payout details checks (`selling/SellingTest`): `mvn test`.
`tests/system/test_selling.py` covers Sell your art end to end; `tests/system/test_site_pages.py` covers the page tabs of SEO & Layout Controls, the four information pages and shop test payments (dev or production profile); `tests/system/test_cart_offers.py` covers the cart, Buy now, custom sizes and offers; `tests/system/test_landing_quick_checkout.py` covers the in-page checkout, quick sign-in and admin test payments; `tests/system/test_landing.py` covers Landing Page Management end to end (`test_landing_showcase.py`: own addresses, information pages and the three showcase pages) (both dev profile, fresh DB). `tests/system/test_admin_setup.py` (dev profile, fresh DB) proves 401/403 for non-admins on every
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
`database/seed.sql` gives every entity 4-5 varied rows (ids are fixed by load order; the system tests rely on them): admins 1-5
(admin, Vaishali, moderator, teacher `rajat@`, moderator `pooja@`), users 1-5 (1 Aarav certified + selling, 2 Diya `diya@example.com`
print buyer / commission client, 3 Ishita `ishita.bose@example.com` email not verified + unpaid order, 4 Farhan
`farhan.siddiqui@example.com` collector, refunded workshop, half-way through course 5, 5 Tanvi `tanvi.kulkarni@example.com` certified
student with live listing `shaniwar-wada-at-dusk`), courses 1-5, course orders in every payment state, shop orders `AC-S-SEED01..05`
(DELIVERED / SHIPPED / PACKED / PAID / PAYMENT_PENDING), 3 carts, commissions `AC-SEED-C01..04`, artist threads, reviews, wishlists,
contacts, newsletter. Course 3 has no enrolments on purpose (test_platform caps it at one seat). Diya owns no course (tests buy for her).
Landing pages: 1 Watercolour (published at `/landing/watercolour-landscapes-for-beginners`; UPI, cards, net banking, PayPal),
2 Acrylic festive campaign (draft, own address; UPI and cards only); coupons WATERCOLOUR10 (page 1), FIRSTCOURSE (first-time), SUMMER25 (inactive).
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
  landing/      Landing Page Management: configurable course landing pages, per-page pricing / payments / checkout / coupons,
                versions, analytics (docs/landing-pages.md)
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
docs/           AWS-DEPLOY.md, awsarchitecture.md, marketplace-shop.md, landing-pages.md
tests/e2e/      Playwright browser tests (purchase flow, room preview, Your orders, cart & offers, landing pages); run against the dev profile
storage/        uploaded files, served at /storage/**  (storage/certificates is git-ignored)
storage-private/ paid download files (git-ignored, never served directly; S3 key prefix private/)
```

## Conventions and gotchas

- **Errors** (`http/GlobalExceptionHandler`): API / admin API / XHR / `Accept: application/json` calls get JSON `{message, reference}`;
  page visits get a small branded page (404 / 409 / 500) with a link home. A database refusal (foreign key) is a 409 "other records
  still depend on it", not a 500; unexpected errors log `ref XXXXX` so a user's reference can be found in the log.
- **Deleting a course** (`DELETE /admin/api/courses/{id}`) is refused (409, "hide it instead") when it has any order, enrolment,
  submission or certificate; only untouched drafts can be deleted.
- Newsletter links use `APP_URL`; without it, the address the admin used to send the campaign (never localhost).
- **SQL is MySQL 8.** Page with `LIMIT n OFFSET m` at the end of the statement. Quote reserved words
  (`primary`, `like`, `order`, `key`, …) in backticks, and use MySQL functions (`DATE_ADD`, `CONCAT`, …). Backslashes in
  string literals are escape characters. Always pass parameters through `db.select/first/update(sql, params…)`,
  and never concatenate input into SQL.
- `Db` returns `Row` maps with **lower-case column names**. Dates are serialized as Laravel strings
  (`yyyy-MM-dd HH:mm:ss`), and the React pages depend on that exact JSON shape.
- **Collations:** a database loaded from a dump (AWS RDS imported from a PC) keeps each table's collation, while tables created by
  later migrations get the database default; mixed collations break joins/UNIONs on text ("Illegal mix of collations").
  `2026-11-10_landing_collation.sql` aligns the landing tables and the database default with `orders`. Don't add COLLATE/CHARSET to
  new CREATE TABLEs: let them inherit the database default.
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
  The page has **tabs** (address hash `#vision|instructor|support|faq`, default Layout & SEO; `/admin/faq` redirects to `#faq`;
  `Components/Admin/SitePagesTabs`): **Our vision** (`/artistikcity-vision`), **Instructor** (`/instructor`), **Customer support**
  (`/customer-support`) and **FAQ** (`/faq`: the question manager `FaqManager` from `Pages/Admin/Faq` + page texts). Their content is
  `admin/SitePages` (settings tags `page_vision|page_instructor|page_support|page_faq`, private: not in `site`; empty = the factual
  defaults in `SitePages.DEFAULTS`; kinds text / html (HtmlCleaner) / link / flag / number / cards JSON {icon,title,text,label,href} /
  people JSON {id, website, facebook, instagram, youtube, linkedin} (https only)), saved per page by `POST /admin/api/setup/site-pages/{page}`
  (capability `settings`, 422 per field, audited). The public pages are React on `SiteLayout` (`web/InfoPagesController`, pages
  `Info/Vision|Instructor|Support|Faq`, parts `Components/Site/InfoParts`, `.ip-*` in `ac-theme.css`; aliases `/vision`, `/instructors`,
  `/help`, `/faqs`); the instructor's name, photo, title and bio come from the admin profile. The old mustache templates
  `pages/artistikcity-vision|customer-support|faq` and `Pages/Instructor.js` are no longer used. The footer copyright reads
  "© 2021 ArtistikCity" everywhere (`SiteFooter`, page templates, landing pages, thank-you e-mail).
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
  The page adapts to 0..12 rooms. Step 2 is split into three parts with Back / Next buttons, A. Size (presets + custom size), B. Frame,
  C. See it on your wall (summary; switches the picture to the first room), kept in the address hash (`#size|frame|wall`). The picture is a
  carousel (`ArtPreview`): arrows, swipe, arrow keys, "2 of 6", thumbnails, and a "person for scale" switch in rooms measured in inches. The artwork page's "On a wall" tab uses the same component. `RoomStage.js` / `rooms.js` are no longer used.
- **AWS** (docs/AWS-DEPLOY.md): CloudFront → EC2 (Docker) + RDS + S3, `infra/*.yml` (CloudFormation), `.github/workflows/deploy.yml`,
  `deploy/ec2-deploy.sh` (deploy/rollback/status, runs from inside the image) and `deploy/db-migrate.sh` (schema on an empty
  DB, else pending migrations - same rules as the launcher). `http/CloudFrontFilter` (`TRUST_CLOUDFRONT`) takes the visitor IP and
  https from CloudFront headers and rejects requests without `X-Origin-Verify`. `GET /healthz` is the health check.
  `http/PreviewLockFilter` ("Site under construction" wall): on when `SITE_PREVIEW_PASSWORD` is set on the server **or** when an
  admin switches it on at Admin > System parameters > **Site Access Password** (`/admin/parameters`, alias `/admin/setup/site-access`,
  `config/panel/SiteAccessController`, page `Admin/Setup/SiteAccess`, capability `setup`; `GET|POST /admin/api/setup/site-access`
  `{enabled, password, confirm}`; min 8 chars, confirm must match, switching on needs a password; audited, never the value).
  `http/SiteLock` keeps it in `settings` under the private tag `system` (`site_lock_enabled`, `site_lock_hash` = bcrypt,
  `site_lock_changed_at|by`), cached 10 s and dropped on save. While on: pages answer 401 with the password form (no app shell, so no
  flash), APIs a JSON 401 `PREVIEW_LOCKED`, Inertia visits 409 + `X-Inertia-Location`; `X-Robots-Tag: noindex`, robots.txt disallows
  all. The form posts to `/__preview`; the cookie `ac_preview` (HttpOnly, Lax, 30 days) holds an HMAC token (keyed by the bcrypt hash
  for the admin password, so a new password signs everyone out); 10 wrong tries per IP per 15 min -> 429. Always open: `/healthz`,
  payment webhooks, static files, the whole `/admin` area, and any request from a signed-in admin. Either password opens the site.
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
  `purchase.draft`. **Cart and Buy now** sit side by side (artwork page,
  step 2, the "Add to cart" button on marketplace cards): `shop_carts` per owner key `U:<id>` (max 50 lines, 409 `CART_FULL`), JSON API
  `/api/v1/cart/**` (`POST .../items/{id}/save` = save for later), client store `Components/Shop/CartStore`, header `CartButton` + drawer
  (`Components/Shop/CartDrawer`) and the page `/cart` (`Pages/Shop/Cart`, `OfferController`). "Buy now" saves the draft and `/purchase/buy-now`
  (`CartService.buyNow`) adds it to the cart and stores that line id in the session key `purchase.only`: checkout then pays for that line only
  (prop `only {otherCount}`, `/api/v1/cart?checkout=1` prices the same way); `/purchase/checkout?all=1` (the cart's and drawer's Checkout)
  clears it. A guest adding an original gets 401 `SIGN_IN`. An original in the cart is a 15-minute hold (one at a time).
  **Custom print sizes:** canvas and poster prints take any long side in a range (default 8–60 in, settings tag `shop`: `custom_enabled`,
  `custom_min_in`, `custom_max_in`, hard limits 4–96; Admin > Sales > Marketplace > Prices, `PUT /admin/console/api/shop/custom-size`). Size code
  `C<n>`; `ShopPricing.customPrice` interpolates the price by print area between the preset sizes and rounds up to the next ₹x99 (stretching
  to the nearest ₹100); labelled "(custom)". **Make an offer** (originals only, `shop/OfferService` + `OfferController`, tables `painting_offers`,
  `painting_offer_events`, migration `2026-11-07_cart_offers.sql`): studio originals take offers by default, student paintings only when the
  student ticks "Accept offers" (`paintings.offers_enabled` NULL = default, `offer_min_pct` NULL = site rule); staff override per painting in
  Admin > Marketplace > Offers. Rules in settings tag `offers` (on, lowest offer 70 %, answer within 72 h, pay within 48 h, 3 rounds, 5 offers
  per buyer per 24 h). Statuses OPEN → COUNTERED → ACCEPTED → PAID, or DECLINED / WITHDRAWN / EXPIRED / CLOSED; one live offer per buyer and
  painting. Accepting (serializable transaction locking the painting and its offers) closes the other offers and holds the piece for the buyer
  (`cart_reservations` key `U:<buyer>` until `pay_by`; `MarketplaceService` keeps that hold, `OFFER_HOLD`); the buyer pays the agreed price
  (`ShopRepository.agreedPrice`) through the normal checkout with every gateway. `OfferService.sweep` (every minute) expires unanswered offers,
  releases unpaid holds and closes offers on sold pieces; every step e-mails the other side (`emails/offer.mustache`) and shows on
  `/user/offers` (`Pages/Account/Offers`, nav count `offers`), My studio > Sell your art (`sellOffers`) and the admin tab. APIs: buyer
  `GET /api/v1/offers/painting/{id}`, `POST /api/v1/offers` (needs `agree`), `POST /api/v1/offers/{id}` {action accept|reoffer|decline|withdraw},
  `POST /api/v1/offers/{id}/pay`; student `GET /api/studio/sell/offers`, `POST /api/studio/sell/offers/{id}`, `POST /api/studio/sell/listings/{id}/offers`;
  staff `GET|POST /admin/console/api/offers[/{id}]` (capability `marketplace`, teachers see their own pieces), `PUT .../offers/settings`
  (`marketplace.edit`, audited), `PUT .../offers/paintings/{id}`. UI: `Components/Shop/OfferDialog` (button + dialog) and `OfferCard`. Orders: `shop_orders` / `shop_order_items`, finalised by
  `ShopOrderService.finalizeSale` (idempotent; lines that can't be supplied become REFUND_REQUIRED with `refund_due_inr`); the marketplace
  webhook also resolves shop orders. Downloads stream from private storage (`Storage.putPrivate/openPrivate`, never /storage/**) at
  `/shop/download/{token}`. Admin: one console, Sales > Marketplace (`/admin/dashboard/marketplace`: paintings, review, holds, orders, messages, prices, coupons;
  `MarketplaceAdminController` + `ShopAdminController`, sections in `Components/Admin/ShopSections`; `/admin/dashboard/shop` only redirects). The old single-original checkout
  page now forwards into the cart checkout. Browser tests: `tests/e2e` (Playwright, against the dev profile).
- **Landing Page Management** (package `landing/`, Admin sidebar group of the same name, `/admin/landing[/{tab}]`, editor
  `/admin/landing/pages/{id}/{step}`, pages `Admin/Landing/Index` + `Admin/Landing/Editor`, shared parts `Components/Admin/Landing/parts`;
  full write-up in docs/landing-pages.md). Everything about a landing page is managed there: pages, templates (`LandingCatalog`),
  sections, design, pricing & offers, **per-page payment settings**, checkout, coupons, SEO & marketing, orders & refunds, analytics,
  media, defaults and Team access. Public page: server-rendered (`LandingPublic` → `LandingView` → `templates/landing/page.mustache`,
  `static/css/landing.css`, `static/js/landing.js`, no React) at the page's own address **`/landing/{slug}`** (slug generated from
  the name when none is typed, unique; full link shown in the list and editor). A landing page never replaces a site page:
  `/course/{slug}`, `/how-it-works` and `/commission/step-1` are always the original pages.
  **Checkout inside the page**: every Enroll button and the header *Log in* (`data-signin`) open a dialog on the landing page
  (`#lp-co`, `static/js/landing-checkout.js`, no React, `.lp-co*` in `landing.css`): quick sign-in for guests (Google Identity
  Services one-tap / button -> `POST /auth/google/credential`, a six-digit e-mail code `POST /auth/email-code/send|verify`
  (`auth/EmailCodeService`, table `auth_email_codes`), SMS code `/auth/otp/*` with WebOTP, passkey), then details + payment
  (Razorpay's window opens on top of the page), then the result; `QuickSignInController.options` only offers methods that can
  reach people (mail server / SMS provider / Google client id) and are switched on in Payments & checkout › **Sign-in at checkout**
  (settings `landing_pages`/`signin`, `GET|PUT /admin/api/landing/signin`); e-mail + password (`/login`, `/register` from the dialog)
  needs no setup and is always offered when nothing else can run. `/landing/{slug}/enroll` and `/enroll/done?order_id=` only redirect
  to the page (`#enroll`, `?order=`; the old React `Pages/Landing/Checkout` is no longer used; old `/course/{slug}/enroll` links
  redirect) + JSON `GET /landing/checkout/{pageId}/state[?order=]` (guests too), `/landing/checkout/{pageId}/quote|order|pay|failed`.
  A passwordless account's first order sets its name. **Test payments** (Payments & checkout tab, `LandingTestPayments`,
  `landing.payments`): test buyers (existing account or a new passwordless one), a server-side simulator (page incl. drafts,
  buyer, method, coupon, outcome paid / declined / pending; lists every step) and undo of test orders (paid -> refunded, access
  and seat released; nothing deleted); simulated orders (`payment_option = 'Simulated (test)'`) never use up a coupon. **Simulate Razorpay** switch
  (`LandingPayments.razorpaySimulated`, settings `landing_pages`/`simulate_razorpay`; only without Razorpay keys): Razorpay counts as connected, test
  buyers pay in a simulated Razorpay window on the page (`POST /landing/checkout/{id}/simulate`), everybody else sees "payment opens soon"
  (state `paymentsClosed`). New pages: an address nobody typed is generated by the server and made unique (`-2`). A landing
  purchase is a normal course `orders` row with `landing_page_id`, coupon, amounts, `payment_method|provider`, UTM; completion,
  webhook and reconciler stay in `CheckoutController` (expected amount = the order's own price, `LandingHooks.onPaid` after every
  verified payment). Content config is versioned JSON (`draft_config` → publish = `landing_page_versions` row; rollback re-publishes;
  `LandingConfig.normalize` validates/cleans everything); money settings are tables, live on save, one calculation for page,
  checkout and gateway (`LandingPricing.quote`; the browser never sends a price; pay re-prices with 409 `repriced`). Payment
  capability model: provider can (catalogue) × page allows (GLOBAL defaults or CUSTOM) × provider connected; checked on every pay;
  a page can't be published/scheduled when nobody could pay. Cashfree/PayU are listed but not integrated. Capabilities `landing`,
  `landing.content|publish|pricing|payments|orders|refunds` (`Personas`; per-person grants in `admins.extra_capabilities`, edited in
  Settings › Team access by a super admin). Settings live under the private settings tag `landing_pages` (`defaults`, `payments`):
  never use the public tag `landing` (that one is the home hero and is sent to every page). Events `landing_events` (hashed visitor
  cookie, no personal data). `/sitemap.xml` (`web/SitemapController`) lists live courses, landing pages that allow it and stories.
  Optional lead capture = the site newsletter (`POST /api/newsletter`, source `landing:<page id>`), with a consent tick.
  Migration `2026-11-08_landing_pages.sql`. React 17 doesn't batch state updates inside promises: guard renders on every piece of
  state a load sets (`if (!data || !cfg)`), not just the first.
  **Login & verification** (`/admin/landing/login`, `landing/LandingLoginAdmin`, `auth/LoginSettings`, settings `landing_pages`/`login`):
  guests fill in the checkout without an account; at Pay a code to their mobile (default) or e-mail signs them in / creates the
  account (`landing/LandingBuyerVerify`, `/landing/checkout/{id}/verify/send|check`); per page `landing_checkout.verify_with`
  (migration `2026-11-12_landing_login_verify.sql`). Simulated SMS / e-mail codes are shown on screen only for new identities,
  test buyers and accounts made with a simulated code (`auth_events` `simulated_signup`), also on `/login`. `simulate_razorpay_all`
  lets every visitor pay with simulated Razorpay (otherwise test buyers and simulated-code accounts). After paying: congratulations + single-use `/auth/continue?t=` ("Log in to My
  studio"), and the thank-you e-mail + receipt `emails/landing-thank-you` (`LandingHooks.sendThankYou`; its button is a single-use sign-in
  link to the course, `landing/CourseAccessLink`, `/auth/course-access`; preview / test copy in Login & verification › 6). The congratulations screen shows the same receipt
  (`result.receipt`) with **Download receipt (PDF)** `/landing/receipt/{order}.pdf` (`LandingReceipt`, buyer only, Poppins font for ₹). **Test pages** (`LandingTestKit`,
  `/admin/api/landing/test-kit`): four published `[Test]` pages `/landing/test-*` (one per verification mode, noindex). Tests:
  `test_landing_login_verify.py`, `test_landing_regression.py` (groups), `test_landing_migrations.py` (old release -> migrations -> = schema.sql).
  **Information pages** (`landing_pages.kind = 'SITE'`, `course_id` null; migration `2026-11-09_landing_site_pages.sql`; the
  `site_route` column is unused): no price/payments/checkout (`LandingConfig.normalize(cfg, site)` refuses `COURSE_ONLY` sections,
  the main button needs `cta.primaryHref`); instructors can't see them. The commission request section writes the theme into
  the wizard's sessionStorage `artistikcity.commission.v1` and opens `/commission/step-2` (keep it in sync with
  `CommissionStore.THEMES`; `LandingSiteConfigTest` checks the ids). `LandingShowcase` (ApplicationRunner, once per DB,
  settings `landing_pages`/`showcase_v1`, off with `app.landing.showcase=false`) creates /landing/how-it-works, the Watercolour
  page and /landing/commission through `LandingPages`; tests: `tests/system/test_landing_showcase.py`, `tests/e2e/specs/landing-showcase.spec.js`
- Blog posts ("Studio Stories") are public only when `status = 1`; drafts 404 for visitors (admins can preview). Status: 0 draft, 1 published,
  2 archived, 3 in review, 4 scheduled (`publish_at`; `BlogAdminController.publishDue` publishes due ones every minute). CMS: Admin > Content >
  Studio Stories (`/admin/content/stories`, `/new`, `/{id}`; page `Admin/Content/StudioStories` = dashboard or editor), JSON API
  `GET/POST /admin/api/content/stories`, `POST|PUT /admin/api/content/stories/{id}`, `POST .../{id}/workflow` (old `/admin/api/posts` kept,
  `/admin/blog/post*` redirect). **Public journal:** `web/StudioStoriesController`: `/studio-stories` (page `StudioStories`: featured story in
  the banner, topic tabs from `categories`, tags, search, load more; `?category=slug&tag=&q=` mirrored in the address bar) and
  `/studio-stories/{slug}` (page `StoryDetails`: progress bar, byline, cover, body, tags, share links, author box, newer/older, related;
  drafts only for signed-in admins, with a Preview bar). `/blog/posts[?id=]` and `/blog/post/{slug}` answer 301 to these (`BlogController`
  is an empty stub). Views: `StudioStoriesController.countView` (one per visitor session; admins and bots not counted) -> `posts.views` +
  daily `post_views`; `read_minutes` = words / 200. `/api/studio-stories` (home page strip) gives published stories, topped up from the
  Studio Vaishali Arts WordPress blog (`service/StudioStoriesService`), which the journal also shows lazily as "More from Studio Vaishali Arts"
  (`/api/studio-stories/studio-blog`). The old `Pages/Posts.js` / `PostDetails.js` are no longer used. `/faq` reads the `faqs` table (managed in SEO & Layout Controls › FAQ).
  Order `payment_status`: 0 pending, 1 paid, 2 failed, 3 refunded; course access needs 1.
- Wish list: table `wishlist_items` (type `course` or `painting`), `POST /wishlist {type, id, saved}`, page `/user/wishlist`
  (`Pages/Students/Wishlist.js`), and the heart button `Components/Wishlist/SaveButton.js`.
- Use Java time (`LocalDateTime.now()`) for every timestamp, and pass `created_at` explicitly. Don't rely on MySQL `NOW()` or
  column defaults, because the DB server may run in a different time zone than the app.
- Payments only mark an order paid after a **verified gateway response**. The dev profile / `PAYMENT_TEST_MODE=true`
  enables simulated payments when no keys are set. On the live site only **test buyers** may simulate (`payments/TestPayments`):
  a super admin ticks "Test buyer" on an account in Admin > People (`users.test_buyer`, audited); its checkouts offer "Simulate payment"
  and the orders get `is_test = 1` (courses, marketplace, shop, commissions), which revenue figures, the ledger and the reconciler skip.
  `PAYMENTS_TEST_BUYERS=false` switches the feature off. The shop checkout `/purchase/checkout` has its own wider switch
  (`TestPayments.offerShopSimulation`, settings `shop`/`simulate_payments`, Admin > Sales > Marketplace > Prices › Test payments,
  `PUT /admin/console/api/shop/test-payments`, `marketplace.edit`, audited): `test_buyers`, `staff` (default: also a buyer whose browser
  is signed in to the admin) or `everyone` (every signed-in buyer, for trying the site before launch). The option is ticked from the
  start when no gateway has keys; simulated shop orders are always `is_test = 1`.
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
- **Admin two-step verification (MFA)** (`security/mfa/`: `Totp` RFC 6238, `QrCode` (own SVG encoder, no library), `AdminMfa`;
  `web/admin/AdminMfaController`; migration `2026-11-13_admin_mfa.sql`): authenticator app + 10 single-use recovery codes
  (SHA-256 only). The TOTP secret is encrypted with `PanelConfig.secretBox()` (no key = MFA not offered); `admins.mfa_last_step`
  makes each code single-use. Sign-in: a correct password only creates a *pending* sign-in in the HttpSession (10 min, 5 wrong
  codes, then back to the password); `/admin/login/verify` (code / `?recovery=1`, "trust this browser" = HttpOnly cookie
  `ac_mfa_trust` HMAC'd with `mfa_changed_at`, 30 days) or, when required and not set up, `/admin/login/setup` (QR + key, then
  recovery codes once); `auth.loginAdmin` runs only after that (`Auth.verifyAdminPassword` checks the password without a session).
  Account menu › Two-step verification = `/admin/account/security` (page `Admin/Account/Security`): set up, new recovery codes /
  turn off (need a current code), forget trusted browsers; super admins (`setup`) require it for all staff (settings `system`
  `admin_mfa_required`; only once their own MFA is on) and reset a colleague (`POST /admin/api/setup/mfa/{id}/reset`). Public
  pages strip `mfa_*` from admin rows. Tests: `security/mfa/TotpTest` (RFC vectors), `tests/system/test_admin_mfa.py` (dev, decodes
  the QR with OpenCV), `tests/e2e/specs/admin-mfa.spec.js`.
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
  (WCAG AAA); a global `:focus-visible` ring is defined at the end of `ac-theme.css`. Pre-built Tailwind resets (`.tw button { background-color:
  transparent }`) beat single-class button rules: give such buttons a two-class selector (see `.pf .pf__btn--primary`, the brand-coloured
  "Create portfolio PDF" on `/dashboard/certificates`).
- Reference only: `docs/sqlserver-ddl.sql` (SQL Server version of courses / taxonomy / audit log), `frontend/resources/js/types/catalog.d.ts`,
  `docs/prototype/` (one-file React + Tailwind prototype of the filters, tag manager and audit viewer).
- `logs/`, `target/`, `.tools/` and `frontend/node_modules/` are local-only and git-ignored.
