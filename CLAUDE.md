# ArtistikCity

Online art school: live courses, workshops, a student studio, an original-art marketplace and art commissions.
This is a Java port of a Laravel 8 + Inertia + React app. The React pages were kept as they were, and the backend
speaks the Inertia protocol to them.

- **Backend:** Java 17, Spring Boot 3.3, plain JDBC through the `Db` helper. There is no JPA/Hibernate.
- **Database:** Microsoft SQL Server. Locally, H2 in `MODE=MSSQLServer`.
- **UI:** React 17 + Inertia (`frontend/`), plus Mustache server-side templates for checkout, static pages, the admin console and e-mails.
- **Platform:** the developer works on Windows. The `.bat` and `.ps1` launchers are the user-facing entry points.

## Commands

```bash
# Run locally on the in-memory demo DB (schema.sql + seed.sql reloaded every start, test payments on)
mvn spring-boot:run -Dspring-boot.run.profiles=dev        # http://localhost:8080
# Windows one-click equivalent (downloads JDK 17 + Maven into .tools\ if missing)
START-ArtistikCity.bat

mvn -B package -DskipTests                                # -> target/artistikcity-1.0.0.jar
java -jar target/artistikcity-1.0.0.jar --spring.profiles.active=dev   # run from the repo root

# Full stack (SQL Server 2022 + app) in Docker
docker compose up -d --build
docker compose run --rm -e RESET_DB=true db-init          # reload demo data

# Rebuild the React bundle -> src/main/resources/static/js/app.js
cd frontend && npm install && npm run dev                 # or: npm run prod / npm run watch
```

There are no automated tests (`src/test` does not exist). To verify a change, run the dev profile and check the pages
by hand. Use the demo logins below.

**Demo logins** (password `password`): student `student@artistikcity.com` at `/login`, admin `admin@artistikcity.com`
at `/admin/login`, teacher `vaishali@artistikcity.com` at `/admin/login`.

## Layout

```
src/main/java/com/artistikcity/
  config/       WebConfig (interceptors, /storage/** mapping), MustacheConfig
  inertia/      Inertia (server adapter) and Routes (named-route table, published to the browser as Ziggy)
  http/         Auth (web + admin guards), SessionInterceptor, Csrf, GuardInterceptors, Flash, Redirects, exceptions
  support/      Db, Row, Input, Validator, Paginator, Storage, Json, Slugs, Str
  view/         Views (Mustache rendering) and ViewHelpers
  service/      CourseService, PostService, CertificateService, PaymentGateways (Razorpay/PayPal), Mailer
  web/          public site controllers;  web/user/ student area;  web/admin/ admin panel;  web/api/ JSON API
  lifecycle/    student studio, credentials and admin console overview (learn -> submit -> sell)
  marketplace/  1-of-1 original-art marketplace (listings, cart reservations, admin review)
  commission/   commission-art requests, pricing and payments
src/main/resources/
  templates/    Mustache: inertia/ (root HTML for React), pages/, orders/, admin/, emails/, certificates/
  static/       compiled assets. static/js/app.js is BUILD OUTPUT, so do not edit it by hand
  ziggy/        route.js used by the React route() helper
frontend/resources/js/   React source: Pages/, Components/, Layouts/, Interface/
database/       schema.sql (drops + recreates everything), seed.sql (demo data), migrations/ (idempotent, dated)
storage/        uploaded files, served at /storage/**  (storage/certificates is git-ignored)
```

## Conventions and gotchas

- **SQL must run on both SQL Server and H2 (MSSQLServer mode).** Use `TOP n` / `OFFSET … FETCH`, put reserved words
  in `[brackets]`, and avoid vendor-specific functions. Always pass parameters through `db.select/first/update(sql, params…)`,
  and never concatenate input into SQL.
- `Db` returns `Row` maps with **lower-case column names**. Dates are serialized as Laravel strings
  (`yyyy-MM-dd HH:mm:ss`), and the React pages depend on that exact JSON shape.
- **Schema changes go in three places:** add a dated, idempotent script to `database/migrations/` (for existing SQL
  Server databases), and also update `database/schema.sql` (fresh installs and the dev profile). Update `seed.sql` if demo data needs it.
- **New routes:** add the Spring mapping **and** register the name/URI in `inertia/Routes.java`, so that React's
  `route('name')` and the Java `routes.path("name", params…)` both work. Route names match the old Laravel names.
- **Rendering:** for React pages, use `inertia.render(request, "Folder/Page", props)`. The page lives at
  `frontend/resources/js/Pages/Folder/Page.js`, and the bundle must be rebuilt after JS changes.
  For server-rendered pages, use `views.page(request, "admin/…", model)` with a `.mustache` file under `templates/`.
- Forms using PATCH or DELETE send `_method` (the hidden-method filter is on). Every POST needs the CSRF token: `_token`
  field or `X-XSRF-TOKEN`/`X-CSRF-TOKEN` header.
- Auth has two session guards, `web` (students) and `admin` (admins/teachers). Passwords are bcrypt and compatible with Laravel `$2y$`.
- Payments only mark an order paid after a **verified gateway response**. The dev profile / `PAYMENT_TEST_MODE=true`
  enables simulated payments when no keys are set.
- Configuration comes from environment variables (see README §4 and `.env.example`). Never commit `.env` or real keys.
- `logs/`, `target/`, `.tools/` and `frontend/node_modules/` are local-only and git-ignored.
