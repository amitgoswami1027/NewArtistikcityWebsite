# Marketplace shop: prints, room preview, artist hub, cart

Every artwork in the marketplace can be bought in four ways, configured in a short purchase flow modelled on the
commission wizard, collected in a persistent cart and paid in one checkout.

| Format | What it is | Price from | Quantity | Shipping |
|---|---|---|---|---|
| Original painting | The 1-of-1 artwork. Choosing it reserves it for 15 minutes. | the listing's final price | 1 | included |
| Limited edition canvas | Numbered giclée on canvas, stretched (+ size surcharge) or rolled | price list, per size | up to 10, within the edition | flat per order |
| Fine art poster | Archival paper, matte or glossy | price list, per size | up to 10 | flat per order |
| Digital download | High-resolution file, personal-use licence | price list | 1 | none |

Frames (black wood, natural oak, white canvas float) are priced per inch of outside perimeter. Float frames are only
offered for canvas (and originals on canvas or panel); posters take black or oak; a framed canvas is always stretched.

## The flow: four screens

Once a collector decides to buy, there is nothing between them and the painting but these four screens:

```
1  /marketplace/{slug}             Choose: everything about the piece; in the buy box the format (original, numbered canvas,
                                   poster, download), size, finish, quantity, live price, and "Talk to the artist"
                                   (chat, WhatsApp, Instagram, e-mail)                     Pages/Marketplace/Show + Components/Shop/BuyPanel
2  /purchase/step-2                Room preview: the piece in photographed rooms, one-click room switch, size and frame,
                                   "Continue to payment" puts it in the cart              Pages/Purchase/Step2Room    (skipped for downloads)
3  /purchase/checkout              Payment: contact -> delivery -> payment accordion, inline validation,
                                   sticky order summary with coupon and totals           Pages/Purchase/Checkout
4  /purchase/orders/{id}           Confirmation: order summary, edition numbers, downloads Pages/Purchase/ThankYou
```

- The choice (the "draft") is saved in the HTTP session by `POST /api/v1/purchase/draft`, so the room preview survives a
  refresh and screen 1 opens on the visitor's last choice. Choosing the original and pressing Continue places the 15-minute
  hold (`MarketplaceService.reserve`, one hold per collector; `switch: true` moves it).
- Old links keep working: `/purchase/step-1?artwork=…&format=…` redirects to the artwork page with that format selected,
  `/purchase/preview` to the room preview (or the checkout for a download).
- Coming back from the checkout to change the frame replaces the line the flow added (remembered per artwork in
  `sessionStorage`) instead of adding a second one; adding the same choice twice never doubles it (server upsert).
- Prices on screen update instantly from `Components/Shop/shop.js`, which mirrors `shop/ShopPricing.java`. The server
  always re-prices: draft, cart and checkout never trust a price from the browser.
- Checkout sections open one at a time. Each field is checked when the visitor leaves it (same rules as the server); a
  section that is complete closes itself when its last field is left (or on Enter / Continue) and shows a one-line summary
  with Edit. The country decides shipping and the payment method (India: Razorpay in INR; elsewhere PayPal in USD).
- The old single-original checkout (`/marketplace/{slug}/checkout`) puts the held original in the cart and forwards to
  `/purchase/checkout`.

## Room preview (`Components/Shop/RoomStage.js`, `Components/Shop/rooms.js`, `static/assets/rooms/`)

Four photographed interiors: minimalist living room (sofa, sun through a window), industrial loft (brick, concrete, pendant),
bohemian bedroom (terracotta wall, rattan headboard, kilim) and a gallery wall (track spots). The photos are rendered with a
physically based path tracer from 3D scenes built to real sizes, with one fixed camera (50 in high, 230 in from the wall,
38° vertical field of view), so the scale is known exactly: `pxPerInch` photo pixels per inch on the back wall.
`rooms.js` holds each room's calibration (wall rectangle, floor line, default hanging line, main light direction).

The stage is layered, everything in photo pixels scaled to the box:

1. the room photo;
2. the artwork (print + CSS frame moulding), sized from the selected size and frame, hung by the usual rule (bottom edge
   a hand's width above the furniture; centre at 57 in on the gallery wall), draggable with the pointer or the arrow keys
   and kept on the wall; its cast shadow follows the room's main light and the frame depth;
3. the room's light on the artwork: `<room>-shade.jpg` (multiply: lamp falloff, darker corners) and `<room>-glow.jpg`
   (screen: a sun patch falling across it), cropped to where the artwork hangs;
4. `<room>-front.webp`, the furniture cut out from the render's depth, drawn over the artwork so a piece dragged low slips
   behind the sofa.

Switching rooms is one click on the thumbnails (`RoomSwitcher`) and immediate; all room images are preloaded. The same stage
is the "View in a room" tab on the artwork page, showing the format and size picked in the buy box.

To change or add a room, edit the scene (furniture sizes in inches) in the renderer, render at 1200 × 800, and run its
publish step, which writes the images to `static/assets/rooms/` and regenerates `rooms.js`.

## Meet the artist (`Components/Shop/ArtistHub.js`, `shop/ArtistHubService.java`)

- WhatsApp (`wa.me` with a pre-filled message naming the artwork and option), Instagram DM (`ig.me/m/<handle>`) and e-mail
  (`mailto:` with subject and body). The listing's own `artist_whatsapp / artist_instagram / artist_email` are used when set
  (Admin > Marketplace > Paintings > Prints), otherwise the studio's contact details from Site settings.
- "Ask the artist a question" and the live chat window share one conversation per collector and artwork
  (`artist_threads`, `artist_thread_messages`). Every collector message e-mails the artist side (the listing's e-mail, the
  instructor who listed it, and `ADMIN_EMAIL`) with the artwork's details and the option being looked at. Replies are written
  in Admin > Marketplace > Messages; the collector sees them in the chat (it polls every 10 s while open) and by e-mail.
  Rate limit: 12 messages per 15 minutes per collector.

## Cart

- Tables `shop_carts` (one per owner: `U:<userId>` or a guest's `S:<session token>`, the same keys the holds use) and
  `shop_cart_items` (painting, format, size, finish, frame, quantity; never a price). A guest cart is merged into the
  account's cart when the guest signs in, so it follows the collector to every device.
- API (CSRF-checked JSON; every answer is the whole re-priced cart):
  `GET /api/v1/cart?country=`, `POST /api/v1/cart/items`, `PATCH /api/v1/cart/items/{id}`, `DELETE /api/v1/cart/items/{id}`,
  `POST /api/v1/cart/coupon` (15 tries per 10 minutes), `DELETE /api/v1/cart/coupon`.
- Lines are re-checked on every read: `ok`, `hold_expired` (the original's hold ended: "Reserve again") or `unavailable`.
  Checkout refuses while any line isn't `ok` (409 `CART_CHANGED`).
- Front end: `Components/Shop/CartStore.js` is a small module-level store (`useCart()`: `add`, `setQty`, `remove`,
  `applyCoupon`, `removeCoupon`, `open`, `count`, …) shared by every component without a provider. The header bag icon and
  slide-out drawer are `CartButton` / `CartDrawer` (`Components/Shop/CartDrawer.js`); the drawer renders into `<body>`.
  The header count comes from the shared Inertia prop `cart.count`.

## Coupons

`shop_coupons`: percent (max 90 %) or flat, optionally only for one artwork or one artist, minimum order, maximum uses,
start/end dates. The discount is spread over the eligible lines (stored per order line). Usage is counted when an order is
paid. Managed in Admin > Marketplace > Coupons (super admin).

## Orders and payment

- `POST /api/v1/purchase/checkout` prices the cart, stores a `PAYMENT_PENDING` `shop_orders` row with its
  `shop_order_items` snapshot, moves the original's hold to the order owner and extends it for the payment window, then
  starts Razorpay (India, INR), PayPal (international, USD at `COMMISSION_INR_PER_USD`) or the test payment.
- `ShopOrderService.finalizeSale` runs after a verified payment (Razorpay signature via `/api/v1/purchase/confirm`, PayPal
  capture via `/purchase/paypal/return`, or the existing webhook `/api/v1/marketplace/webhook`, which now also resolves shop
  orders). It is idempotent and SERIALIZABLE: originals become SOLD (a late payment still wins if nobody else holds it),
  canvas editions are numbered (`edition_no`), downloads get a token. A line that can no longer be supplied is marked
  `REFUND_REQUIRED` and its amount added to `refund_due_inr`; the rest of the order goes ahead. Digital-only orders go
  straight to `DELIVERED`. The paid lines and the coupon are removed from the cart, and a receipt (`emails/shop-order`) goes
  to the buyer and the studio.
- Fulfilment in Admin > Marketplace > Orders: packed → shipped (tracking required) → delivered, full refund (optionally putting
  the original back on sale), and "partial refund paid".

## Digital downloads

The high-resolution file is uploaded per listing (Admin > Marketplace > Paintings > Prints; JPEG, PNG, TIFF or PDF up to 200 MB) into
private storage: `storage-private/hires/...` locally, key prefix `private/` on S3 (the app's IAM role may read/write it;
CloudFront has no route to it). Buyers download through `GET /shop/download/{48-hex token}`: valid 30 days, 10 downloads,
only while the order is paid.

## Admin: Sales > Marketplace (`/admin/dashboard/marketplace`)

One console for everything sold in the marketplace (`Pages/Admin/Marketplace.js`; the shop sections are
`Components/Admin/ShopSections.js`, their data and actions `shop/ShopAdminController`). The old Art shop page
`/admin/dashboard/shop?tab=…` redirects to the matching tab here, and the sidebar badge counts both.

| Tab | Who | What |
|---|---|---|
| Review queue | super admin, moderator | approve or send back new originals |
| Paintings | everyone (instructors: their own) | list, edit, publish, archive, feature; **Prints** button: formats sold, edition size, high-resolution file, artist contact links (super admin, moderator) |
| Live holds | super admin, moderator | see and release 15-minute holds |
| Orders | everyone (instructors: orders with their artworks); actions need marketplace.orders | cart orders (fulfil, refund) and, below, earlier orders from the single-original checkout |
| Messages | everyone (instructors: their artworks) | reply to collectors, close / reopen |
| Prices | super admin | sizes, long side, prices, stretching surcharge, frame rate per inch, prints shipping |
| Coupons | super admin | create, edit, switch off, delete (used codes are switched off instead) |

## Your orders (`/user/orders`, package `orders/`)

One list of everything a student has bought: courses and workshops (`orders`), marketplace cart orders (`shop_orders`, one
entry per artwork line), originals from the older single-artwork checkout (`marketplace_orders`) and commissions
(`commission_orders`). Abandoned payment attempts are left out, except unpaid course orders, which the student can still pay.

- Each order system is an `OrderSource` bean (`OrderSources.Courses / Shop / LegacyMarketplace / Commissions`) that maps its
  rows to `UnifiedOrder`; `UnifiedOrderService` runs them in parallel, sorts newest first and isolates failures (a broken
  source is logged and the rest still show). A new order system only needs a new `OrderSource`.
- `UnifiedOrder`: `orderId, orderType (course | marketplace | commission), category (Digital Course, Workshop, Original Painting,
  Canvas Print, Poster Print, Digital Download, Commission), title, subtitle, variant, imageUrl, purchaseDate (ISO),
  amount, currency, status (pending | processing | shipped | completed | failed | refunded), statusLabel`, and the links that
  apply: `accessUrl` (Start learning), `trackingUrl` + `trackingNumber` (Track delivery; the courier's own tracking page),
  `certificateUrl` (paid originals with a certificate and numbered canvases), `downloadUrl`, `detailUrl`. Absent links are
  left out of the JSON.
- `GET /api/v1/user/orders?type=&q=` answers `{orders, counts, total}` (401 signed out). `type` is one of `all, course,
  workshop, marketplace, original, print, download, commission`; `q` matches order number, title, artist, size, status.
  The page (`Pages/Account/Orders.js`) gets all orders with the page and filters in the browser with the same rules, keeping
  `?type=&q=` in the address bar.
- `GET /marketplace/certificates/{orderId}/{itemId|original}`: printable certificate of authenticity (template
  `certificates/artwork-coa.mustache`), for the buyer or an admin, once paid.

## Database

Migration `database/migrations/2026-10-28_marketplace_shop.sql` (also in `schema.sql`): new columns on `paintings`
(`shop_formats`, `edition_size`, `editions_sold`, `hires_path`, `hires_name`, `artist_whatsapp`, `artist_instagram`,
`artist_email`) and the tables `shop_price_options` (with starter prices), `shop_carts`, `shop_cart_items`, `shop_coupons`,
`shop_orders`, `shop_order_items`, `artist_threads`, `artist_thread_messages`.

Starter prices (edit them in the console): canvas 12 / 24 / 36 / 48 in at ₹2,499 / ₹4,999 / ₹8,999 / ₹13,999 (+₹600–2,200
stretched), posters 12 / 18 / 24 / 36 in at ₹799 / ₹1,199 / ₹1,699 / ₹2,699, digital ₹999, frames ₹22 / ₹28 / ₹30 per inch,
prints shipping ₹149 in India and ₹1,999 worldwide.

## Tests

- `src/test/java/com/artistikcity/shop/ShopPricingTest.java`: print sizes, prices, frames, editions, coupons, contact helpers.
- `src/test/java/com/artistikcity/orders/UnifiedOrdersEndpointTest.java`: `GET /api/v1/user/orders` through MockMvc with mock
  course and marketplace sources: 200, aggregation newest first, the UnifiedOrder JSON shape, filters, 401 signed out, and a
  failing source not hiding the others.
- `tests/e2e/` (Playwright; start the app with the dev profile, then `npm install && npx playwright install chromium && npm test`):
  - `room-preview.spec.js`: switching the room backdrop updates the visualiser in the same click (photo, furniture layer,
    light, selected thumbnail) at the same scale; size and frame change the artwork; drag and keyboard stay on the wall;
    mobile layout.
  - `purchase-flow.spec.js`: screen 1 (format, live price, talk to the artist), old URLs, the accordion checkout with inline
    validation and auto-advance, test payment, confirmation, the order under Your orders.
  - `orders.spec.js`: the unified endpoint (200, course + marketplace, newest first, shape), 401, badges and actions, filters
    and search.
