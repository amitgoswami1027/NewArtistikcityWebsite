# Landing Page Management

Configurable, conversion-focused course landing pages, managed end to end from one section of the admin console
(**Admin › Landing Page Management**, `/admin/landing`). Package `com.artistikcity.landing`; migration
`database/migrations/2026-11-08_landing_pages.sql`.

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
(Configuration Panel: keys,          landing_payment_settings / _providers /       /course/{slug}            (server-rendered, Mustache)
 secrets, connectivity)       ──▶    _methods, landing_pricing, landing_checkout,  /course/{slug}/enroll     (React checkout)
                                     landing_coupons, draft_config + versions  ──▶ /landing/checkout/{id}/*  (JSON: quote, order, pay)
                                                                                    └─▶ CheckoutController completion / webhook / reconciler
```

* **Rendering** (`LandingPublic` → `LandingView` → `templates/landing/page.mustache`, `static/css/landing.css`, `static/js/landing.js`):
  server-rendered HTML (no React bundle on the public page), one small script, inline SVG icons, responsive images
  (`/img/w{width}/uploads/**`, `LandingImages`), videos only load when clicked, `<details>` accordions, design tokens from the
  page config as CSS variables. `CoursesController` asks `LandingPublic` first, so a published page whose address equals the
  course slug replaces the course page; unpublishing brings the course page back.
* **Templates and sections** (`LandingCatalog`): 6 templates (Artist Showcase, Premium Masterclass, Creative Workshop,
  Minimal Editorial, Video-First Course, Conversion-Focused Sales Page) and 14 section types (hero, intro, outcomes,
  curriculum, artist (several artists allowed), gallery (+ before/after slider, lightbox), benefits, video, testimonials,
  pricing, FAQ, related courses, final CTA, custom HTML, …), each with layout variants. Adding a template or section =
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
| Payments & checkout | Every page's payment mode at a glance + the global defaults; provider status and "Test connection". |
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
`GET /admin/api/landing/team`, `PUT /admin/api/landing/team/{adminId}`.
Public: `GET /course/{slug}`, `GET /course/{slug}/enroll`, `GET /course/{slug}/enroll/done`,
`POST /landing/checkout/{pageId}/quote|order|pay|failed`, `GET /landing/checkout/order/{orderId}/status`, `POST /landing/e`
(CTA / method events), `GET /sitemap.xml`.

## 8. Setup and deployment

1. Apply `database/migrations/2026-11-08_landing_pages.sql` (the launcher and `deploy/db-migrate.sh` do it; safe to run twice;
   compatible with the previous release).
2. Gateway keys: Admin › System parameters › Configuration Panel (`razorpay.key|secret|webhook-secret`, `paypal.*`). No new
   environment variables. Razorpay: enable the methods you offer (UPI, EMI, international cards) on the Razorpay account too.
3. Search engines: submit `https://<your domain>/sitemap.xml` in Google Search Console (robots.txt is static and has no
   absolute sitemap line).
4. Open Admin › Landing Page Management, check *Payments & checkout › Global defaults*, then create pages.

## 9. Known limitations

* Cashfree and PayU are catalogued but not integrated (no adapter yet), so they can't be switched on.
* Instalments: EMI / no-cost EMI are offered through Razorpay's own EMI; there is no in-house instalment plan.
* A/B testing, campaign-specific variants of one page and a visual drag-on-canvas builder are not in this release (pages are
  built from the section library; campaign pages are separate pages with their own address).
* Partial refunds: refunds are full refunds through the existing refunds queue.
* Lighthouse could not be run in the build environment; the page was measured with equivalent checks (see the report). Run
  Lighthouse on staging before launch.
