# ArtistikCity – Java / Spring Boot + React + SQL Server

This is the Java port of the original **Laravel 8 + Inertia.js + React** application in `Artistik-City/`.

| Layer | Before (Laravel) | Now |
|---|---|---|
| Backend | PHP 7/8, Laravel 8, Eloquent | **Java 17+, Spring Boot 3.3**, plain JDBC (`Db` helper) |
| Database | MySQL | **Microsoft SQL Server** (T-SQL schema in `database/`) |
| Public site + student area UI | React 17 pages via Inertia.js | **Unchanged React pages.** The Java backend speaks the Inertia protocol and publishes the Ziggy `route()` list |
| Server-rendered pages (checkout, static pages, admin, e-mails) | Blade | **Mustache** templates (same HTML/CSS) |
| Auth | Laravel guards `web` + `admin`, bcrypt | Session auth with the same two guards. **Existing Laravel bcrypt hashes still work** |
| PDF certificates | dompdf | OpenHTMLtoPDF |
| Payments | razorpay-php, paypal/rest-api-sdk-php | Java `HttpClient` REST clients (Razorpay, PayPal v1 Payments API) |
| Social login | Socialite | OAuth2 authorization-code flow (Google, Facebook) |

---

## 1. Requirements

* **JDK 17 or newer** and **Maven 3.8+**
* **SQL Server 2016+** (Express / Developer / Azure SQL all work)
* Node.js 18+ (only if you want to rebuild the React bundle; a compiled `js/app.js` is already included)

## 🐳 Run everything in Docker

The stack is split into containers:

| Service | Image | What it does |
|---|---|---|
| `db` | `mcr.microsoft.com/mssql/server:2022-latest` | SQL Server 2022 (Developer edition). Data lives in the `mssql-data` volume. Exposed on `localhost,1433`. |
| `db-init` | built from `docker/db-init.Dockerfile` | Runs once. It creates the `artistikcity` database and loads `database/schema.sql` + `seed.sql` if the tables don't exist yet, then exits. |
| `app` | built from `Dockerfile` (Maven build, then a Java 17 runtime image) | Spring Boot backend plus the compiled React UI on http://localhost:8080. Uploads live in the `app-storage` volume. |
| `frontend` (optional) | `node:18` | Rebuilds the React bundle: `docker compose --profile frontend run --rm frontend`, then `docker compose up -d --build app`. |

**Start:** with Docker Desktop running, double-click **`DOCKER-START.bat`**, or run:

```bash
docker compose up -d --build
```

The first run downloads about 2 GB of images and builds the app, which takes 5–15 minutes. Later starts take seconds.

| Task | Command |
|---|---|
| Logs | `docker compose logs -f app` (or `db-init`, `db`) |
| Stop, keeping data | `DOCKER-STOP.bat` or `docker compose down` |
| Stop and delete the database and uploads | `docker compose down -v` |
| Reload demo data | `docker compose run --rm -e RESET_DB=true db-init` |
| Change the password, ports or payment keys | copy `.env.example` to `.env` and edit it, then `docker compose up -d` |

If SQL Server exits right away, check Docker Desktop > Settings > Resources. SQL Server needs at least 2 GB of memory, and the `SA_PASSWORD` must be at least 8 characters with upper case, lower case, digits and symbols.

## ★ One-click setup on Windows (without Docker)

Double-click one of these files in the project folder. Each one downloads a portable Java 17 and Maven into `.tools\` if you don't have them (no admin rights needed), builds the app, prepares the database, starts the site and opens http://localhost:8080.

| File | Database |
|---|---|
| **`START-ArtistikCity.bat`** | In-memory database in SQL Server mode. Nothing to install; demo data is reloaded on every start. **Start here.** |
| `START-with-SQLServer.bat` | Your own SQL Server / SQL Server Express on `localhost:1433`, using Windows login. The `artistikcity` database is created and filled with demo data on the first run. |
| `START-with-Docker-SQLServer.bat` | SQL Server 2022 in Docker Desktop (container `artistikcity-sql`, SA password `Artistik#2026Pass`). |

The first run takes a few minutes, because it downloads Java (about 190 MB) and the Maven libraries (about 150 MB). Later starts take about 20 seconds. Close the window or press **Ctrl+C** to stop.

You can pass options from a command prompt, either to a `.bat` file or directly to `setup-and-run.ps1`:

```bat
START-with-SQLServer.bat -SqlUser sa -SqlPassword "YourPassword"      :: SQL login instead of Windows login
START-with-SQLServer.bat -SqlInstance SQLEXPRESS -Trusted             :: named instance (SQL Server Browser must run)
START-with-SQLServer.bat -ResetDatabase                               :: drop and recreate tables + demo data
START-ArtistikCity.bat -Port 9090 -SkipBuild -NoBrowser
```

**Troubleshooting**

| Problem | Fix |
|---|---|
| "Could not connect to SQL Server" | Open SQL Server Configuration Manager > SQL Server Network Configuration > Protocols > enable **TCP/IP**, then restart the SQL Server service. SQL Express uses a dynamic port: use `-SqlInstance SQLEXPRESS` or set the port to 1433. |
| Login failed for `sa` | Enable *SQL Server and Windows Authentication mode* (server Properties > Security) and enable the `sa` login, or use `-Trusted`. |
| Port 8080 in use | `START-ArtistikCity.bat -Port 8081` |
| Build fails | Copy the error text. A corporate proxy may block Maven Central; set the proxy in `%USERPROFILE%\.m2\settings.xml`. |

## 2. Quick start without SQL Server (dev profile)

Runs on an in-memory H2 database in **SQL Server compatibility mode**, created from `database/schema.sql` and `database/seed.sql` on every start.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Open http://localhost:8080

The dev profile also enables **test payments**: when no Razorpay keys are set, the payment step shows *"Complete test payment"*.

## 3. Run against SQL Server

1. Create the database, for example in SSMS or `sqlcmd`:
   ```sql
   CREATE DATABASE artistikcity;
   ```
2. Create the tables and demo data (run from the project folder):
   ```bash
   sqlcmd -S localhost -U sa -P <password> -d artistikcity -i database/schema.sql
   sqlcmd -S localhost -U sa -P <password> -d artistikcity -i database/seed.sql
   ```
   Alternatively, start the app **once** with `DB_INIT=always`. Note that `schema.sql` drops and recreates every table.
3. Configure the connection with environment variables (or edit `src/main/resources/application.properties`):
   ```bash
   # Windows (PowerShell)
   $env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=artistikcity;encrypt=true;trustServerCertificate=true"
   $env:DB_USERNAME="sa"
   $env:DB_PASSWORD="YourPassword"
   mvn spring-boot:run
   ```
4. Or build a jar: `mvn package`, then `java -jar target/artistikcity-1.0.0.jar`. Run it from the project folder so that `database/` and `storage/` are found, or set `STORAGE_DIR`.

### Demo logins (from `seed.sql`, password `password`)

| Area | URL | E-mail |
|---|---|---|
| Student | `/login` | `student@artistikcity.com` |
| Admin panel | `/admin/login` | `admin@artistikcity.com` |
| Teacher (instructor) | `/admin/login` | `vaishali@artistikcity.com` |

### Migrating existing MySQL data

`schema.sql` keeps the Laravel table and column names, and adds the columns the PHP code used but the migrations never created. Export the MySQL data (for example with SSMA for MySQL, or CSV export and `BULK INSERT`) into the new tables. Existing user and admin passwords keep working. Also copy Laravel's `storage/app/public/*` (`uploads/…`, `certificates/…`) into this project's `storage/` folder.

## 4. Configuration (environment variables)

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | SQL Server connection | localhost / `sa` |
| `DB_INIT` | `always` = run schema + seed on start | `never` |
| `PORT` | HTTP port | 8080 |
| `STORAGE_DIR` | Uploaded files (served at `/storage/**`) | `./storage` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM_ADDRESS` | SMTP. If empty, e-mails are only logged | – |
| `MAIL_ADMIN_EMAIL` | Receives contact-form messages | admin@artistik.com |
| `RAZORPAY_KEY`, `RAZORPAY_SECRET` | INR payments | – |
| `PAYPAL_CLIENT_ID`, `PAYPAL_SECRET`, `PAYPAL_MODE` (`sandbox`/`live`) | USD payments | sandbox |
| `PAYMENT_TEST_MODE` | `true` = simulated payments when no keys are set | false |
| `GOOGLE_CLIENT_ID/SECRET`, `FACEBOOK_CLIENT_ID/SECRET` | Social login; callback URL is `{site}/social-login/{provider}/callback` | – |

## 5. Front-end (React)

The React/Inertia source is in `frontend/resources/js`, unchanged from the Laravel project. To rebuild the bundle:

```bash
cd frontend
npm install
npm run prod      # or: npm run dev / npm run watch
```

The build writes `src/main/resources/static/js/app.js` (Laravel Mix runs standalone; no PHP is needed).

## 6. Project layout

```
database/schema.sql, seed.sql      SQL Server DDL + demo data
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
  web/admin/     admin panel: dashboard, courses/workshops, free courses, people, orders,
                 taxonomies/testimonials/blog (resource CRUD), blog posts
  web/api/       JSON API used by the React footer/newsletter and the admin course editor
src/main/resources/
  templates/inertia/   root HTML for the React app ("app" and "user" layouts)
  templates/pages|orders|admin|emails|certificates   Mustache versions of the Blade views
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
| `Admin/*` | `web/admin/*` (includes the admin login and blog controllers that were missing from the Laravel repo) |
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
