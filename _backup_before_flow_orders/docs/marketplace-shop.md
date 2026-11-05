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

## The flow

```
/marketplace/{slug}                 "Reserve this original" or "Prefer a print?"
  └─ /purchase/step-1?artwork={slug}&format=…   format, size, finish, quantity      (Pages/Purchase/Step1Variant)
       └─ /purchase/step-2                      room visualiser, size + frame        (Pages/Purchase/Step2Room)   skipped for downloads
            └─ /purchase/preview                summary, cost estimate, coupon,     (Pages/Purchase/Preview)
                 │                              Meet the artist
                 └─ /purchase/checkout          whole cart, contact, address, pay   (Pages/Purchase/Checkout)
                      └─ /purchase/orders/{id}  receipt, edition numbers, downloads (Pages/Purchase/ThankYou)
```

- The step's choice (the "draft") is saved in the HTTP session by `POST /api/v1/purchase/draft`, so steps 2 and 3 survive a
  refresh. Choosing the original on step 1 places the 15-minute hold (`MarketplaceService.reserve`, one hold per collector;
  `switch: true` moves it).
- Prices on screen update instantly from `Components/Shop/shop.js`, which mirrors `shop/ShopPricing.java`. The server
  always re-prices: draft, cart and checkout never trust a price from the browser.
- The old single-original checkout (`/marketplace/{slug}/checkout`) now puts the held original in the cart and forwards to
  `/purchase/checkout`.

## Room visualiser (`Components/Shop/RoomStage.js`)

Four scenes (modern living room, minimalist bedroom, professional office, blank gallery wall), each a wall 160 in wide and
96 in high drawn at 6.25 units per inch, with furniture of real sizes (84 in sofa, 64 in queen bed, 60 in desk, a 5 ft 7 in
person). The artwork is an HTML box between the wall layer and the furniture layer, sized from the selected print size plus
frame moulding, so it is honest to scale. It can be dragged (pointer events) or moved with the arrow keys, and its shadow
falls away from the scene's light source (lamp, window, desk lamp, track spots) and deepens with the frame depth.

## Meet the artist (`Components/Shop/ArtistHub.js`, `shop/ArtistHubService.java`)

- WhatsApp (`wa.me` with a pre-filled message naming the artwork and option), Instagram DM (`ig.me/m/<handle>`) and e-mail
  (`mailto:` with subject and body). The listing's own `artist_whatsapp / artist_instagram / artist_email` are used when set
  (Admin > Art shop > Listings), otherwise the studio's contact details from Site settings.
- "Ask the artist a question" and the live chat window share one conversation per collector and artwork
  (`artist_threads`, `artist_thread_messages`). Every collector message e-mails the artist side (the listing's e-mail, the
  instructor who listed it, and `ADMIN_EMAIL`) with the artwork's details and the option being looked at. Replies are written
  in Admin > Art shop > Artist messages; the collector sees them in the chat (it polls every 10 s while open) and by e-mail.
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
paid. Managed in Admin > Art shop > Coupons (super admin).

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
- Fulfilment in Admin > Art shop > Orders: packed → shipped (tracking required) → delivered, full refund (optionally putting
  the original back on sale), and "partial refund paid".

## Digital downloads

The high-resolution file is uploaded per listing (Admin > Art shop > Listings; JPEG, PNG, TIFF or PDF up to 200 MB) into
private storage: `storage-private/hires/...` locally, key prefix `private/` on S3 (the app's IAM role may read/write it;
CloudFront has no route to it). Buyers download through `GET /shop/download/{48-hex token}`: valid 30 days, 10 downloads,
only while the order is paid.

## Admin: Sales > Art shop (`/admin/dashboard/shop`)

| Tab | Who | What |
|---|---|---|
| Orders | marketplace (instructors: orders with their artworks); actions need marketplace.orders | fulfil, refund |
| Artist messages | marketplace (instructors: their artworks) | reply, close / reopen |
| Price list | edit: super admin | sizes, long side, prices, stretching surcharge, frame rate per inch, prints shipping |
| Coupons | super admin | create, edit, switch off, delete (used codes are switched off instead) |
| Listings | super admin, moderator | formats sold, edition size, high-resolution file, artist contact links |

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
- End to end (Playwright, dev profile with test payments): variants, room visualiser, artist hub, coupon, drawer, original
  hold, checkout, editions, digital download privacy, admin tabs, guest-cart merge, hold rules, 390 px layouts.
