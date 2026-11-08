# Landing Page Management

Configurable, conversion-focused landing pages, managed end to end from one section of the admin console
(**Admin › Landing Page Management**, `/admin/landing`). Package `com.artistikcity.landing`; migrations
`database/migrations/2026-11-08_landing_pages.sql` and `2026-11-09_landing_site_pages.sql`.

**Every landing page has its own address, `/landing/{slug}`** (`LandingPages.PREFIX`). The slug is generated from the page
name when none is typed (`LandingPages.freeSlug`: lower-case, dashes, made unique with -2, -3 …) and the full link is shown in
the page list and the editor header (with Copy). A landing page never takes over an existing page of the site:
`/how-it-works`, `/course/{slug}` and `/commission/step-1` always show the site's own pages. Unpublishing takes
`/landing/{slug}` offline (404).

Two kinds of page:

* **Course pages** (`kind = COURSE`) sell one course, with their own price, payment methods and checkout. The checkout runs
  **inside the landing page** (a dialog), so the visitor never leaves it: see *In-page checkout and quick sign-in* below.
  `/landing/{slug}/enroll` only opens the page with the dialog open (`#enroll`).
* **Information pages** (`kind = SITE`, `course_id` empty) have no course, price, payment or checkout; their buttons lead
  anywhere on the site (`cta.primaryHref`). Instructors can't see or make them. (`landing_pages.site_route` from the first
  version of this feature is no longer used.)

## 1. What it reuses (nothing is duplicated)

| Existing piece | How landing pages use it |
|---|---|
| `courses`, `course_prices`, modules/lessons, `admins` (artists) | The page sells one course; empty content falls back to the course's own data. Curriculum always comes from the course. |
| `orders` | A landing purchase is a normal course order with extra columns (`landing_page_id`, `coupon_code`, `list_amount`, `discount_amount`, `tax_amount`, `payment_method`, `payment_provider`, `utm_*`, `landing_visitor`, `billing_phone`). Course access is unchanged: `payment_status = 1`. |
| `web/CheckoutController` (+ `payments/PaymentReconciler`) | Payment completion, Razorpay webhook, PayPal return and the reconciler. For landing orders the expected amount is the one fixed on the order (`LandingHooks.expected`), and `LandingHooks.onPaid` runs after every verified payment (coupon redemption, funnel event, method check). |
| `payments/RefundQueue`, `seats/SeatService`, `payments/TestPayments` | Refunds from the Orders tab, seat holds for capped live courses, test buyers / simulated payments. |
| Configuration Panel (`config_overrides`) | Gateway keys stay global and secret. Landing pages only choose *which* connected providers and methods to offer. |
| `admin/Audit`, `security/AuditLog` | Every money, publishing, permission and refund change is audited. |
| `support/Storage`, `support/HtmlCleaner`, `testimonials`, `course_reviews`, `faqs` | Media library, safe custom HTML, approved testimonials / reviews only, site FAQ as fallback. |

## 2. Architecture

```
Global payment infrastructure        Landing Page Management (per page)            Public
(Configuration Panel: keys,          landing_payment_settings / _providers /       /landing/{slug}           (server-rendered, Mustache)
 secrets, connectivity)       ──▶    _methods, landing_pricing, landing_checkout,  └ checkout dialog (static/js/landing-checkout.js)
                                     landing_coupons, draft_config + versions  ──▶ /landing/checkout/{id}/*  (JSON: state, quote, order, pay)
                                                                                    /auth/email-code|google|otp|passkey (quick sign-in)
                                                                                    └─▶ CheckoutController completion / webhook / reconciler
```

* **Rendering** (`LandingPublic` → `LandingView` → `templates/landing/page.mustache`, `static/css/landing.css`, `static/js/landing.js`):
  server-rendered HTML (no React bundle on the public page), one small script, inline SVG icons, responsive images
  (`/img/w{width}/uploads/**`, `LandingImages`), videos only load when clicked, `<details>` accordions, design tokens from the
  page config as CSS variables. `LandingCheckoutController` maps `GET /landing/{slug}` to `LandingPublic.render`. A section that
  fails to build (unexpected data) is skipped and logged ("section … skipped") instead of failing the page.
* **Templates and sections** (`LandingCatalog`): 9 templates: course pages (Artist Showcase, Premium Masterclass, Creative
  Workshop, Minimal Editorial, Video-First Course, Conversion-Focused Sales Page, Watercolour Wash) and information pages (Platform
  Showcase, Commission Studio); 20 section types (hero with a *collage* layout, intro, outcomes, curriculum, artist
  (several artists allowed), gallery (+ before/after slider, lightbox; course photos or marketplace artworks), benefits,
  video, testimonials, pricing, FAQ, related courses, final CTA (several buttons), custom HTML, **feature cards**
  (optionally from the course's own lists), **steps / journey**, **course list** (live catalogue, each linking to its
  landing page), **artists** (profiles from Admin › Instructors, linking to `/teacher/{slug}`), **commission request**
  (step 1 of the commission wizard) and **commission prices** ("from" prices computed from `CommissionPricing`)), each
  with layout variants. Curriculum, pricing and related courses need a course and are refused on information pages. Every
  section has an optional small label (`kicker`); items can carry a link (site path, https or `#section-id`); design has
  a `texture` (none, paper grain, watercolour wash). Adding a template or section =
  one entry in `LandingCatalog` + its markup in `page.mustache`; old configs keep working because `LandingConfig.normalize`
  fills missing fields with defaults (`schema` = 1).
* **Content config** (`landing_pages.draft_config`, JSON) is versioned: Save draft → Publish creates
  `landing_page_versions` row N; rollback re-publishes an old version as N+1; preview renders any version
  (`/admin/landing/preview/{id}[?version=]`). Two editors: optimistic check on `draft_saved_at` (409 + "save anyway").
  Scheduling: `publish_at`, published by `LandingPages.publishDue` every minute.
* **Money settings** are normal tables, live as soon as they're saved (and audited), so the price shown on the page, the
  checkout and the gateway amount always come from **one** calculation: `LandingCommerce.quote` → `LandingPricing.quote`
  (pure, unit-tested). The browser never sends a price; the pay step re-prices and answers 409 `repriced` when an offer
  ended or a coupon ran out in between.

### Commission request section

Renders the wizard's four styles (same ids, titles and texts as `CommissionStore.THEMES`, checked against
`CommissionPricing.THEMES` by a unit test), its six-step progress bar and the Next button. `landing.js` writes the choice
into the wizard's own storage (sessionStorage `artistikcity.commission.v1`, other fields kept) and opens
`/commission/step-2`; steps 2-4, the photo upload, preview, sign-in and payment at checkout are the existing React pages,
unchanged. Coming back to step 1 shows the saved choice. No payment or deposit is taken on the landing page.

### Showcase pages (LandingShowcase)

On its first start with the migration applied, the app creates three pages through the module itself
(`LandingPages.create` → `saveDraft` → `publish`, version note "Showcase content (installed automatically)"):
*How it works* (Platform Showcase, `/landing/how-it-works`), *Watercolour Landscapes for Beginners* (Watercolour Wash,
`/landing/<the course's slug>`; the course is found by its slug or by a title with "watercolour" and "landscape") and
*Commission artwork* (Commission Studio, `/landing/commission`). It runs once per database (`settings` `landing_pages` /
`showcase_v1`, which also records each page's outcome; a Watercolour page skipped because the course wasn't found is
tried again on later starts), never touches an address that already has a landing page, and updates an existing
Watercolour page only when it has no unpublished edits (as a new version, so the earlier one is one click away under
Versions). A page whose publish is refused (for example no live payment method) stays a draft and the outcome says why.
Prices, durations, curriculum, reviews, artists, courses and artwork are read from the database when the page is shown.
Switch it off with `app.landing.showcase=false`.

### In-page checkout and quick sign-in

Everything a buyer does happens on the landing page itself, in a dialog (`#lp-co` in `page.mustache`, script
`static/js/landing-checkout.js`, styles `.lp-co*` in `landing.css`; a side sheet on wide screens, full screen on phones).
Every button that links to the page's `/enroll` and the header *Log in* (`data-signin`) open it; nothing navigates away.

1. **Sign in (guests only), the lightest way available.** Standard building blocks, offered only when they can reach
   people on this server (`QuickSignInController.options`):
   * **Google**: Google Identity Services: the *Continue with Google* button plus one-tap. The browser gets a signed ID
     token from Google and posts it to `POST /auth/google/credential`; it is checked against Google's keys, the audience
     (`social.google.client-id`) and a single-use nonce. Needs the site's address under *Authorized JavaScript origins*
     of the Google OAuth client.
   * **E-mail code**: `POST /auth/email-code/send` then `/verify`: a six-digit code by e-mail (`auth/EmailCodeService`,
     table `auth_email_codes`, HMAC only, 10 minutes, 5 tries, 30 s between codes, hourly / daily / network limits). A new
     address gets a passwordless account; its name is taken from the first order. Offered when a mail server is set
     (`MAIL_HOST`), or on a development machine (the code is then shown to a browser on the same computer).
   * **Phone code**: the existing `/auth/otp/send|verify|complete` (SMS; Android Chrome fills the code in by itself
     through WebOTP, the message ends with `@host #code`). Offered when an SMS provider is set.
   * **Passkey**: the existing `/auth/passkey/options|verify`, when the browser supports it.
   * *Other ways to sign in* (password, Facebook) still go to `/login`, which returns to the page.
2. **Details and payment**: `GET /landing/checkout/{id}/state` gives the course, price, the page's payment options and
   checkout fields; then the usual `order` → `pay`. Razorpay opens its own window on top of the page; PayPal approves on
   PayPal's page and comes back to `/landing/{slug}/enroll/done`, which reopens the page at the result.
3. **Result**: success, waiting (polled), failed (try again) - in the same dialog.

### Test payments

Admin › Landing Page Management › *Payments & checkout* › **Test payments** (`LandingTestPayments`, capability
`landing.payments`):

* **Test buyers**: accounts that may simulate payments (`users.test_buyer`). Add an existing account by e-mail, or a new
  address: a passwordless test account is created, which signs in on a landing page with an e-mail code. Signed in on a
  page, a test buyer sees *Simulate payment* in the checkout dialog.
* **Simulator**: pick a page (drafts too), a test buyer, one of the page's payment options, an optional coupon and the
  outcome (*Paid*, *Declined*, *Bank still confirming*). The server runs the real checkout code (price, option check,
  order, seat, then the outcome; *Paid* goes through the same completion as a gateway payment: enrolment, confirmation
  e-mail, landing follow-up) and lists every step.
* **Recent test orders** with **Undo**: a paid test order is marked refunded and the access and seat are released (like a
  confirmed refund); an open one is closed. Nothing is deleted.

Simulated orders have `is_test = 1` and `payment_option = 'Simulated (test)'`: they stay out of revenue, analytics, the
ledger, payouts and the reconciler, and they never use up a coupon. Every action is audited.

## 3. Payment capability model

Three layers decide what a buyer sees; the server checks all of them on every pay request (`LandingPayments.choices`):

1. **What a provider can do** – `LandingCatalog.PROVIDERS`: Razorpay (INR: UPI, UPI QR, UPI intent, GPay, PhonePe, Paytm,
   credit/debit cards, card networks, net banking, wallets, EMI, no-cost EMI, international cards), PayPal (USD: PayPal,
   international cards). Cashfree and PayU are listed with their methods but `integrated = false`: they can't be switched on
   until an adapter exists.
2. **What the page allows** – mode `GLOBAL` (follows the defaults in *Payments & checkout*) or `CUSTOM` (this page only):
   providers on/off with priority and a default, methods on/off with display order. Saving refuses a method no switched-on
   provider supports, a provider that isn't connected, no method at all, and card networks without a card method.
3. **What works right now** – the provider is connected (keys set, or test mode in development).

A page can only be **published or scheduled when at least one method is live** (or the course is free). Razorpay's payment
sheet is limited to the chosen method with Checkout `config.display` (`LandingPayments.razorpayDisplay`); after payment the
method Razorpay reports is stored on the order, and one outside the page's settings is written to the security log (the
payment is kept: money is never refused after the fact).

## 4. Admin section (all under Landing Page Management)

| Tab / step | What it does |
|---|---|
| Overview | KPIs (pages by status, views, unique visitors, enrolments, conversion, revenue, successful / failed payments, AOV), daily charts, best pages / courses / campaigns; filters: date range (today … custom), page, course, artist, campaign. Test payments are left out. |
| All landing pages | Search, status filter, sort, pagination; preview, duplicate, publish / unpublish / schedule / archive / delete. |
| Create landing page | Wizard: course, name, address, campaign, description, template. |
| Editor (per page, 9 steps) | Basics · Template · Sections & content (drag-and-drop order, show/hide, hide on phones, layout, background, all texts, images from the media library, items, buttons, sticky enrol bar) · Design (palette or custom colours, heading font, corners) · Pricing & offers · **Payment settings** · Checkout · SEO & marketing · Preview & publish (checklist, desktop / phone preview, publish, schedule, versions with rollback, activity). |
| Templates & sections | The catalogue with previews. |
| Payments & checkout | **Test payments** (test buyers, simulator, recent test orders with undo); every page's payment mode at a glance + the global defaults; provider status and "Test connection". |
| Coupons & promotions | Percentage / flat, campaign, pages and/or courses (or all pages), minimum amount, total and per-person limits, first-time buyers only, start / end, active. Checked on the server at quote, order and pay time; redemptions recorded once per order. |
| Orders & transactions | Landing orders with page, course, artist, amount, currency, method, provider, reference, payment and enrolment status; filters; detail drawer with payment log and funnel events; full refund through the gateway (reason required, audited). |
| Analytics & reports | Funnel (views → CTA → checkout → payment started → purchase), payment-method and provider performance (success rate, conversion), devices; campaign filter (UTM campaign, coupon campaign). |
| Media library | Upload (≥ 600 px), alt text, reuse of course and artist photos; images in use can't be removed. |
| Settings & roles | Defaults for new pages (template, WhatsApp, access, refund policy, tax, checkout fields) and **Team access**. |

## 5. Permissions

Capabilities (checked on every API call, `lifecycle/Personas`): `landing`, `landing.content`, `landing.publish`,
`landing.pricing`, `landing.payments`, `landing.orders`, `landing.refunds`. Super admin: all. Instructor: `landing`,
`landing.content`, own courses only. Moderator: `landing`, `landing.content`. Per person (Settings › Team access, super
admin only, audited; stored in `admins.extra_capabilities`): ready-made roles **Content manager**, **Marketing manager**
(+ pricing and coupons), **Finance / payments** (orders, refunds), **Publisher**, or single permissions.

## 6. Public page, SEO and tracking

* Mobile-first: artwork first on phones, sticky enrol bar (price + Enroll) once the hero scrolls away, safe-area padding,
  44 px or larger touch targets, no horizontal scroll; keyboard-accessible accordions, lightbox and slider; WCAG AA contrast.
* SEO per page: title, description, canonical, Open Graph, `noindex`, sitemap inclusion (`/sitemap.xml`, `web/SitemapController`),
  Course structured data (rating only from real reviews).
* Marketing: GA4, GTM and Meta Pixel ids per page, loaded only after the visitor accepts cookies; UTM attribution stored on
  the order; WhatsApp button; optional lead capture: a "join the newsletter" box with a consent tick at the end of the page (never a pop-up), stored in `newsletters` with source `landing:<page id>`. Funnel events (`landing_events`) use a random cookie id,
  hashed: no personal data. Bots and staff are not counted.
* No fake urgency: offers show their real end date and end by themselves; struck prices only when an original price was set.

## 7. APIs

Admin (JSON, admin session + CSRF): `GET /admin/api/landing/overview`, `GET|POST /admin/api/landing/pages`,
`GET|DELETE /admin/api/landing/pages/{id}`, `PUT .../{id}/basics|draft|template|pricing|payments|checkout`,
`POST .../{id}/publish|schedule|unpublish|archive|restore|duplicate`, `POST .../{id}/versions/{vid}/restore`,
`GET /admin/api/landing/payments`, `PUT .../payments/defaults`, `POST .../payments/test`, `GET|POST /admin/api/landing/coupons`,
`PUT|DELETE .../coupons/{id}`, `GET /admin/api/landing/orders[/{id}]`, `POST .../orders/{id}/refund`,
`GET|POST /admin/api/landing/media`, `PUT|DELETE .../media/{id}`, `GET|PUT /admin/api/landing/settings`,
`GET /admin/api/landing/team`, `PUT /admin/api/landing/team/{adminId}`, `GET /admin/api/landing/test-payments`,
`POST .../test-payments/buyers` `{email, name?, on}`, `POST .../test-payments/run` `{pageId, buyerId, method, outcome, coupon?}`,
`POST .../test-payments/orders/{id}/undo`.
Create an information page: `POST /admin/api/landing/pages` with `kind: "SITE"` and an information-page template; leave
`slug` out to have it generated from `name` (the answer carries the `slug`).
Public: `GET /landing/{slug}`, `GET /landing/{slug}/enroll` and `/enroll/done?order_id=` (both send the browser back to the
page with the checkout open: `#enroll`, `?order=`; old `/course/{slug}/enroll[/done]` links redirect there),
`GET /landing/checkout/{pageId}/state[?order=]`, `POST /landing/checkout/{pageId}/quote|order|pay|failed`, `GET /landing/checkout/order/{orderId}/status`, `POST /landing/e`
(CTA / method events), `GET /sitemap.xml`. Quick sign-in: `GET /auth/quick/options`, `POST /auth/email-code/send|verify`,
`POST /auth/google/credential`, and the existing `/auth/otp/*`, `/auth/passkey/*`.

## 8. Setup and deployment

1. Apply `database/migrations/2026-11-08_landing_pages.sql`, `2026-11-09_landing_site_pages.sql`, `2026-11-10_landing_collation.sql`
   and `2026-11-11_landing_quick_signin.sql` (the launcher and
   `deploy/db-migrate.sh` do it; safe to run twice; compatible with the previous release). On the next start the three
   showcase pages are created and published (see above).
2. Gateway keys: Admin › System parameters › Configuration Panel (`razorpay.key|secret|webhook-secret`, `paypal.*`). No new
   environment variables. Razorpay: enable the methods you offer (UPI, EMI, international cards) on the Razorpay account too.
3. Search engines: submit `https://<your domain>/sitemap.xml` in Google Search Console (robots.txt is static and has no
   absolute sitemap line).
4. Open Admin › Landing Page Management, check *Payments & checkout › Global defaults*, then create pages.
5. Quick sign-in: e-mail codes need `MAIL_HOST` (e.g. Amazon SES); phone codes need an SMS provider (Configuration Panel);
   Google one-tap needs `social.google.client-id` and the site's address (`https://www.yourdomain.com`) under *Authorized
   JavaScript origins* of that OAuth client in Google Cloud Console.

## 9. Known limitations

* There is no public "all artists" page; the *Discover Artists* buttons scroll to the Artists section, whose cards open
  each artist's profile.

* Cashfree and PayU are catalogued but not integrated (no adapter yet), so they can't be switched on.
* Instalments: EMI / no-cost EMI are offered through Razorpay's own EMI; there is no in-house instalment plan.
* A/B testing, campaign-specific variants of one page and a visual drag-on-canvas builder are not in this release (pages are
  built from the section library; campaign pages are separate pages with their own address).
* Partial refunds: refunds are full refunds through the existing refunds queue.
* Lighthouse could not be run in the build environment; the page was measured with equivalent checks (see the report). Run
  Lighthouse on staging before launch.
