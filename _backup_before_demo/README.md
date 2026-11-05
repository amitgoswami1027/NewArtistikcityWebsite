# ArtistikCity – Java / Spring Boot + React + MySQL

This is the Java port of the original **Laravel 8 + Inertia.js + React** application in `Artistik-City/`.

| Layer | Before (Laravel) | Now |
|---|---|---|
| Backend | PHP 7/8, Laravel 8, Eloquent | **Java 17+, Spring Boot 3.3**, plain JDBC (`Db` helper) |
| Database | MySQL | **MySQL 8.4 LTS** (schema in `database/`; the same table and column names as Laravel) |
| Public site + student area UI | React 17 pages via Inertia.js | **Unchanged React pages.** The Java backend speaks the Inertia protocol and publishes the Ziggy `route()` list |
| Server-rendered pages (static pages, admin sign-in, e-mails) | Blade | **Mustache** templates (same HTML/CSS) |
| Auth | Laravel guards `web` + `admin`, bcrypt | Session auth with the same two guards. **Existing Laravel bcrypt hashes still work** |
| PDF certificates | dompdf | OpenHTMLtoPDF |
| Payments | razorpay-php, paypal/rest-api-sdk-php | Java `HttpClient` REST clients (Razorpay, PayPal v1 Payments API) |
| Social login | Socialite | OAuth2 authorization-code flow (Google, Facebook) |

---

## 1. Requirements

* **JDK 17 or newer** and **Maven 3.8+**. The Windows launcher downloads portable copies when they're missing.
* **MySQL 8.0.16+** (8.4 LTS recommended). The Windows launcher can download and run a portable MySQL 8.4 for you.
* Node.js 18+, only if you want to rebuild the React bundle (a compiled `js/app.js` is already included).

## ★ One-click setup on Windows

Double-click **`START-ArtistikCity.bat`**. It does everything, with no installers and no admin rights:

1. Uses your JDK 17+ and Maven, or downloads portable copies into `.tools\`.
2. Builds the app.
3. **MySQL:** the first time, it downloads MySQL 8.4 LTS (about 270 MB) into `.tools\mysql-8.4.13-winx64\`,
   creates its data folder `.tools\mysql-data\`, sets the passwords, creates the `artistikcity` database and the
   `artistik` user, and loads `database/schema.sql` and `database/seed.sql`. Later starts reuse it, so **your data is kept**.
4. Starts the site on http://localhost:8080 and opens your browser.

Closing the window or pressing **Ctrl+C** stops the site, and MySQL too if the launcher started it.

| File | What it does |
|---|---|
| **`START-ArtistikCity.bat`** | MySQL + website (see above). **Start here.** |
| `START-MySQL.bat` | Starts only MySQL, which keeps running in the background. Use it before running the app from VS Code (F5 also starts it for you). |
| `STOP-MySQL.bat` | Stops the portable MySQL. Your data is kept. |
| `DOCKER-START.bat` | The whole stack in Docker Desktop (see below). |

Options, from a command prompt (also accepted by `setup-and-run.ps1`):

```bat
START-ArtistikCity.bat -ResetDatabase                 :: drop all tables and reload the demo data
START-ArtistikCity.bat -Port 9090 -SkipBuild -NoBrowser
START-ArtistikCity.bat -DbPort 3307                   :: if another MySQL already uses 3306
START-ArtistikCity.bat -Db mysql -DbUser root -DbPassword "YourPassword"   :: your own MySQL server
```

The first run takes a few minutes: Java (about 190 MB, only if you have no JDK 17+), the Maven libraries (about 150 MB) and MySQL (about 270 MB). Later starts take about 20 seconds.

### Database access (portable MySQL)

| | Value |
|---|---|
| Host / port | `127.0.0.1` : `3306` (local connections only) |
| Database | `artistikcity` (utf8mb4) |
| Application user | `artistik` / `Artistik@2026` (all privileges on `artistikcity` only) |
| Administrator | `root` / `ArtistikRoot@2026` |
| JDBC URL | `jdbc:mysql://127.0.0.1:3306/artistikcity?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false` |
| Command line | `.tools\mysql-8.4.13-winx64\bin\mysql.exe -h 127.0.0.1 -u artistik -p artistikcity` |
| Files | binaries `.tools\mysql-8.4.13-winx64\`, data `.tools\mysql-data\`, log `logs\mysql-error.log` |

These are **local development passwords**. Change them for anything reachable from other machines: pass
`-AppPassword` / `-RootPassword` to the launcher, and set `DB_PASSWORD` for the app.

Any MySQL client works with these details, for example the SQLTools extension in VS Code (the connection is
pre-configured in `ArtistikCity.code-workspace`), MySQL Workbench, DBeaver or HeidiSQL.

**Troubleshooting**

| Problem | Fix |
|---|---|
| "MySQL needs the Microsoft Visual C++ Redistributable" | Install https://aka.ms/vs/17/release/vc_redist.x64.exe, then run again. |
| "Port 3306 is already used" | Another MySQL is running. Stop it, run with `-DbPort 3307`, or use it with `-Db mysql`. |
| MySQL does not start | See `logs\mysql-error.log`. To start completely fresh, stop MySQL and delete `.tools\mysql-data\` (this deletes all data). |
| Port 8080 in use | `START-ArtistikCity.bat -Port 8081` |
| Build fails | Copy the error text. A corporate proxy may block Maven Central; set the proxy in `%USERPROFILE%\.m2\settings.xml`. |

## 🐳 Run everything in Docker

| Service | Image | What it does |
|---|---|---|
| `db` | `mysql:8.4` | MySQL 8.4 LTS. On the first start (empty `mysql-data` volume) it creates the `artistikcity` database and the `artistik` user, and loads `database/schema.sql` + `seed.sql`. Exposed on `localhost:3306`. |
| `app` | built from `Dockerfile` (Maven build, then a Java 17 runtime image) | Spring Boot backend plus the compiled React UI on http://localhost:8080. Uploads live in the `app-storage` volume. |
| `frontend` (optional) | `node:18` | Rebuilds the React bundle: `docker compose --profile frontend run --rm frontend`, then `docker compose up -d --build app`. |

**Start:** with Docker Desktop running, double-click **`DOCKER-START.bat`**, or run `docker compose up -d --build`.

| Task | Command |
|---|---|
| Logs | `docker compose logs -f app` (or `db`) |
| Stop, keeping data | `DOCKER-STOP.bat` or `docker compose down` |
| Reload the demo data | `docker compose down -v`, then `docker compose up -d` (this deletes the database and uploads) |
| Change passwords, ports or payment keys | copy `.env.example` to `.env` and edit it, then `docker compose up -d` |

The Docker database uses the same user, password and port as the portable one.

## 2. Run with Maven against any MySQL

1. Create the database and a user, for example in the `mysql` client as root:
   ```sql
   CREATE DATABASE artistikcity CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'artistik'@'%' IDENTIFIED BY 'Artistik@2026';
   GRANT ALL PRIVILEGES ON artistikcity.* TO 'artistik'@'%';
   ```
2. Create the tables and demo data, from the project folder:
   ```bash
   mysql -h 127.0.0.1 -u artistik -p artistikcity < database/schema.sql
   mysql -h 127.0.0.1 -u artistik -p artistikcity < database/seed.sql
   ```
   Alternatively, start the app **once** with `DB_INIT=always`. `schema.sql` drops and recreates every table.
3. Start it. The defaults in `application.properties` already point at `127.0.0.1:3306` / `artistikcity` / `artistik`;
   use environment variables for anything else:
   ```bash
   # Windows (PowerShell)
   $env:DB_URL="jdbc:mysql://dbhost:3306/artistikcity?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false"
   $env:DB_USERNAME="artistik"
   $env:DB_PASSWORD="YourPassword"
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```
   The `dev` profile turns off template caching and enables **test payments**: when no Razorpay keys are set, the payment step shows *"Complete test payment"*.
4. Or build a jar: `mvn package`, then `java -jar target/artistikcity-1.0.0.jar`. Run it from the project folder so that `database/` and `storage/` are found, or set `STORAGE_DIR`.

### Demo logins (from `seed.sql`, password `password`)

| Area | URL | E-mail |
|---|---|---|
| Student | `/login` | `student@artistikcity.com` |
| Admin panel | `/admin/login` | `admin@artistikcity.com` |
| Teacher (instructor) | `/admin/login` | `vaishali@artistikcity.com` |

### Bringing over data from the old Laravel (MySQL) site

`schema.sql` keeps the Laravel table and column names. It also adds the columns the PHP code used but the migrations
never created, plus the new marketplace, commission and student-lifecycle tables. Because both sides are MySQL:

1. Load `schema.sql` into an empty `artistikcity` database. Don't load `seed.sql`.
2. Export only the data from the Laravel database: `mysqldump --no-create-info --complete-insert --skip-triggers <laraveldb> > laravel-data.sql`.
3. Import it: `mysql -u artistik -p artistikcity < laravel-data.sql`. `--complete-insert` names every column, so the extra columns simply keep their defaults.

Existing user and admin passwords keep working. Also copy Laravel's `storage/app/public/*` (`uploads/…`, `certificates/…`) into this project's `storage/` folder.

## 4. Configuration (environment variables)

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection | `jdbc:mysql://127.0.0.1:3306/artistikcity…` / `artistik` / `Artistik@2026` |
| `DB_INIT` | `always` = run schema + seed on start | `never` |
| `PORT` | HTTP port | 8080 |
| `STORAGE_DIR` | Uploaded files (served at `/storage/**`) | `./storage` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM_ADDRESS` | SMTP. If empty, e-mails are only logged | – |
| `MAIL_ADMIN_EMAIL` | Receives contact-form messages | admin@artistik.com |
| `RAZORPAY_KEY`, `RAZORPAY_SECRET` | INR payments (UPI, cards, NetBanking) | – |
| `RAZORPAY_WEBHOOK_SECRET` | Verifies Razorpay webhooks (courses, marketplace, commissions) | – |
| `PAYPAL_CLIENT_ID`, `PAYPAL_SECRET`, `PAYPAL_MODE` (`sandbox`/`live`) | USD payments | sandbox |
| `PAYMENT_TEST_MODE` | `true` = simulated payments when no keys are set | false |
| `APP_URL` | Public site address used in e-mail links and OAuth callbacks, e.g. `https://artistikcity.com` | taken from the request |
| `GOOGLE_CLIENT_ID/SECRET`, `FACEBOOK_CLIENT_ID/SECRET` | Google / Facebook sign-in (see §4a). A button only appears when both values are set | – |
| `FACEBOOK_TRUST_EMAIL` | `false` = never merge a Facebook login into an existing account by e-mail | true |
| `SMS_PROVIDER` | `log` (codes only written to the log) or `twilio` | log |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` or `TWILIO_MESSAGING_SERVICE_SID` | Sending sign-in codes by SMS | – |
| `OTP_SECRET` | Secret for hashing SMS codes (any long random string). **Set it in production** | random per start |
| `AUTH_COOKIE_SECURE` | `auto` (Secure on HTTPS), `true` or `false` | auto |
| `AUTH_SESSION_IDLE_MINUTES`, `AUTH_SESSION_MAX_HOURS`, `AUTH_REMEMBER_DAYS` | Sign-out after inactivity / longest session without "keep me signed in" / with it | 30 / 12 / 30 |
| `AUTH_DEFAULT_COUNTRY_CODE` | Country code added to phone numbers typed without one | 91 |
| `WEBAUTHN_RP_ID`, `WEBAUTHN_ORIGINS` | Passkeys: your domain (e.g. `artistikcity.com`) and allowed origins (e.g. `https://artistikcity.com`). Empty = the request's host | – |
| `FORWARD_HEADERS` | `framework` when running behind a reverse proxy / load balancer that terminates HTTPS | none |

### 4a. Sign-in & security

Students can sign in with **Google**, **Facebook**, **a code sent by SMS**, **an e-mail link**, **e-mail + password** or a **passkey**
(fingerprint / face / device PIN). Every method leads to the same account: one person = one `users` row, with providers in
`linked_accounts`. Admin and teacher logins (`/admin/login`) stay password-only.

**Setting up Google** (Google Cloud Console → APIs & Services → Credentials → OAuth client ID → *Web application*):
authorised redirect URI `https://YOUR-SITE/social-login/google/callback` (for local testing also
`http://localhost:8080/social-login/google/callback`). Put the client ID and secret in `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.

**Setting up Facebook** (developers.facebook.com → your app → Facebook Login → Settings): valid OAuth redirect URI
`https://YOUR-SITE/social-login/facebook/callback`; request the `email` and `public_profile` permissions. Put the app ID and app secret
in `FACEBOOK_CLIENT_ID` / `FACEBOOK_CLIENT_SECRET`.

**Keys on your PC:** put them in a `.env` file in the project folder (copy `.env.example`; `.env` is git-ignored).
`START-ArtistikCity.bat` reads it on every start, for example:

```
GOOGLE_CLIENT_ID=1234-abc.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-...
FACEBOOK_CLIENT_ID=1234567890
FACEBOOK_CLIENT_SECRET=...
```

Until keys are set, the login page shows a "not set up yet" note to a browser on your own PC instead of the buttons.

**SMS codes:** with the default `SMS_PROVIDER=log` no SMS is sent. The code is written to `logs/app.log`, and
`START-ArtistikCity.bat` (and the VS Code `dev` launch) also shows it on the page, but only to a browser on the same computer.
On a server nobody sees it, so set up a real SMS provider there. For real SMS set `SMS_PROVIDER=twilio` and the `TWILIO_*` values. To use another gateway (MSG91, Firebase, …) implement
`auth/sms/SmsSender` (one method) and select it in `auth/sms/SmsConfig`.

**E-mail links** need SMTP (`MAIL_*`). Without SMTP every e-mail is saved as an HTML file in `logs/mail-outbox/`, so sign-in links,
confirmations and password resets can still be tested locally.

**How accounts are joined:** a Google/Facebook login joins an existing account only when the provider confirms the e-mail address. An SMS
login finds an account only by a phone number that was verified with a code. Numbers imported from the old site are not verified yet: a
student verifies theirs once on *Account → Login & security*. If someone signs up with an e-mail they never confirmed and the real owner
later signs in with Google, the unconfirmed password is removed and that person's sessions are ended.

**Sessions:** two HttpOnly, SameSite=Lax cookies (`ac_session`, `ac_refresh`), Secure on HTTPS. Only SHA-256 hashes are stored in
`auth_sessions` / `auth_refresh_tokens`. The session token rotates every 15 minutes; refresh tokens are single-use, and reusing an old one
signs that login out everywhere. Students see and end their sessions on *Login & security*. Login, OTP and e-mail endpoints are rate limited, and changing
sign-in methods requires a login from the last 30 minutes. Security events are kept in `auth_events` for 180 days.

**Going live checklist:** HTTPS, `APP_URL`, `OTP_SECRET`, `MAIL_*`, `SMS_PROVIDER=twilio` + keys, the Google/Facebook keys with production
redirect URIs, `WEBAUTHN_RP_ID`/`WEBAUTHN_ORIGINS`, and `FORWARD_HEADERS=framework` behind a proxy. Do not set `AUTH_EXPOSE_DEV_OTP` on a
server. Even if it is set, codes are only shown to a browser on the server itself.

### 4b. Course checkout

"Enroll now" on a course leads to `/checkout`, one page with three steps (account, billing details, payment) and the order summary
alongside. Prices always come from `course_prices` (INR via Razorpay, USD via PayPal), never from the browser.

- **Razorpay:** the server creates a Razorpay order, the payment window opens on the page, and the result is accepted only with a
  valid signature for that order. Also add a webhook in the Razorpay dashboard: URL `https://YOUR-SITE/api/v1/courses/razorpay-webhook`,
  events `payment.captured` and `order.paid`, and the secret in `RAZORPAY_WEBHOOK_SECRET`. It completes enrollments when a student paid
  but closed the page before it confirmed.
- **PayPal:** the buyer approves on PayPal, and the order is captured when they return, after checking the amount and currency.
- **Test mode** (`PAYMENT_TEST_MODE=true`, on for the dev profile and the Windows launcher): while a gateway has no keys, the page offers a
  clearly marked simulated payment instead.
- Students who are already enrolled see "Go to course" instead of paying again. Receipts are e-mailed, and every order is listed under
  My account › Your orders.

### 4c. Admin console

Sign in at `/admin/login`. Everything an admin manages lives in one console with a sidebar (collapsible; a drawer on tablets
and phones) and the account menu and **Log out** in the top-right corner:

| Area | URL | What it does |
|---|---|---|
| Courses / Workshops | `/admin/courses`, `/admin/workshop` | Searchable, sortable lists; editor in sections (basics, category, schedule, pricing, page content, lists, photos & videos) and a **Curriculum** tab for modules, lessons (notes, PDF), practice tasks and projects. Duplicate copies the curriculum too. Paid courses can be hidden but not deleted. |
| Free lessons | `/admin/free-courses` | Free video lessons for new students. |
| Course orders | `/admin/orders` | Filters (status, course, method, currency, dates), CSV export, order details, manual *paid* / *refunded* with a required note (history is kept), certificates. |
| Newsletter | `/admin/newsletter` | Subscribers (add in bulk, unsubscribe, export) and newsletters (write, preview, send a test, send to everyone). Every e-mail has a signed unsubscribe link. |
| Blog | `/admin/blog/post` | Drafts and published posts, rich text with preview, URL slug, featured, categories, tags, cover image, SEO title and description. Drafts are not public. |
| Catalog / blog lists | `/admin/catalog`, `/admin/blog/taxonomy` | Mediums, genres, skill levels, course types, blog categories and tags. |
| FAQ, Contact messages | `/admin/faq`, `/admin/contacts` | The public `/faq` page; the contact-form inbox. |
| Site settings & SEO | `/admin/settings` | Site title, meta description, share image, homepage highlights, contact details, social links. |
| Instructor profiles | `/admin/instructors` | Admin accounts, roles and the public `/teacher/...` profiles. |
| Student tasks | `/admin/tasks` | Answers to lesson tasks; reply with feedback. |

What each person sees depends on their role (super admin, instructor, gallery moderator). Instructors only see their own
courses. Newsletter unsubscribe links are signed with `NEWSLETTER_SECRET` (falls back to `OTP_SECRET`, then to a random key
stored in the database). Without `MAIL_HOST`, e-mails are written to `logs/mail-outbox/` instead of being sent.

## 5. Front-end (React)

The React/Inertia source is in `frontend/resources/js` (the Laravel project's pages plus the new sign-in pages in `Pages/Auth`, `Components/Auth` and `Pages/Students/LinkedAccounts.js`). To rebuild the bundle:

```bash
cd frontend
npm install
npm run prod      # or: npm run dev / npm run watch
```

The build writes `src/main/resources/static/js/app.js` (Laravel Mix runs standalone; no PHP is needed).

## 6. Project layout

```
database/schema.sql, seed.sql      MySQL 8 DDL + demo data (migrations/ for future schema changes)
storage/uploads/...                demo images (course photos, mediums, teachers, ...)
frontend/                          React + Inertia source and build config
src/main/java/com/artistikcity/
  ArtistikCityApplication          Spring Boot entry point
  config/        WebConfig (middleware/interceptors, /storage mapping), MustacheConfig
  inertia/       Inertia (server-side adapter), Routes (named routes + Ziggy list)
  http/          Auth (web/admin guards), SessionInterceptor (session, flash, CSRF),
                 GuardInterceptors (auth/guest/admin), Flash, Redirects, GlobalExceptionHandler
  support/       Db (JDBC helper), Input (request input), Validator, Paginator, Storage, Slugs, Json, Str
  view/          Views (Mustache rendering), ViewHelpers (template helpers)
  service/       CourseService, PostService, CertificateService, PaymentGateways, Mailer
  web/           public site: Welcome, Pages, Courses, Blog, Profiles, Auth, Social, Checkout
  web/user/      student area: courses, lessons, tasks, account, certificates
  web/admin/     admin sign-in
  admin/         admin console (React pages in Pages/Admin): courses & workshops with curriculum, free lessons,
                 orders, newsletter, blog, catalog lists, FAQ, contact messages, site settings & SEO,
                 instructor profiles, student tasks; shared AdminKit, Grid (server-side tables), Audit
  lifecycle/     admin console overview, verification desk, people, gallery, testimonials
  web/api/       JSON API used by the React footer and newsletter sign-up
src/main/resources/
  templates/inertia/   root HTML for the React app ("app" and "user" layouts)
  templates/pages|admin|emails|certificates   Mustache views (the old admin screens were replaced by the React console)
  static/              original public/ assets (css, js/app.js, fonts, images)
```

## 7. Module map (Laravel → Java)

| Laravel | Java |
|---|---|
| `WelcomeController`, `PagesController` | `web/WelcomeController`, `web/PagesController` |
| `CoursesController` + `CourseResource/Collection` | `web/CoursesController` + `service/CourseService` |
| `BlogController` | `web/BlogController` + `service/PostService` |
| `StudentController`, `TeacherController` | `web/ProfilesController` |
| `Auth/*` (Breeze), `UserAuthController`, `SocialController` | `web/AuthController`, `web/SocialController` |
| `CartController`, `OrderController`, `PaymentController`, `PaypalController` | `web/CheckoutController` + `service/PaymentGateways` |
| `User/*` | `web/user/UserCoursesController`, `UserTaskController`, `UserAccountController` |
| `Admin/*` | `admin/*` React console + `web/admin/AdminAuthController` |
| `Api/*` | `web/api/ApiController` |
| Middleware (`auth`, `guest`, `admin`, CSRF, Inertia) | `http/*` interceptors + `inertia/Inertia` |
| Blade views | `src/main/resources/templates/**/*.mustache` |

## 8. Fixes applied during the conversion

* Added about 40 columns that the code used but the migrations never created (for example `courses.sub_title`, `course_start_date`, `schedule_pdf` and `users.phone`), and relaxed columns the code never fills (`courses.category_id`, `includes`).
* The course list now includes `prices`: `Courses.js` reads `prices[0]` and would crash without it. Missing JSON fields are sent as empty objects so `CourseDetails.js` doesn't crash.
* `/payment/success?order_id=…` no longer marks an order as paid; only verified gateway responses do.
* Course highlights and "problems solved" were never saved because of typos in the form field names; these are now saved. `course_based` was being overwritten with the status value; this is fixed.
* Implemented the admin login and the blog admin controllers, which were referenced by the routes but missing.
* The contact-form confirmation is now shown. Fixed broken "Home" breadcrumb links (`index.html`) and references to missing files (`animate.css`, `wow.min.js`).
* Student-area pages no longer throw JavaScript errors for jQuery plugins that were never loaded (slick, magnificPopup, tooltipster). Relative image paths under `/user/` now resolve.
* Genre filtering on `/courses` handles courses with several genres.
* Uploaded certificates are stored locally (`storage/certificates`) instead of S3.
