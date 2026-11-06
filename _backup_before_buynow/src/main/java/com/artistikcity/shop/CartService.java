package com.artistikcity.shop;

import com.artistikcity.http.Auth;
import com.artistikcity.marketplace.Holders;
import com.artistikcity.marketplace.MarketplaceException;
import com.artistikcity.marketplace.MarketplaceRepository;
import com.artistikcity.marketplace.MarketplaceService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The collector's cart. One cart per owner: a signed-in collector is "U:{userId}", a guest "S:{token}" (the same keys the
 * 15-minute holds use, see {@link Holders}). When a guest signs in, their guest cart is merged into their account's cart,
 * so nothing is lost. Prices are never stored in the cart: every read re-prices each line from the current price list,
 * and originals are checked against the live hold engine.
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final ShopRepository repo;
    private final Auth auth;
    private final MarketplaceService market;
    private final MarketplaceRepository marketRepo;

    public CartService(ShopRepository repo, Auth auth, MarketplaceService market, MarketplaceRepository marketRepo) {
        this.repo = repo;
        this.auth = auth;
        this.market = market;
        this.marketRepo = marketRepo;
    }

    // ================================================================ owner / cart id

    /** The cart id for this visitor, or null when they have none yet (and {@code create} is false). */
    public Long cartId(HttpServletRequest request, boolean create) {
        Long userId = auth.userId(request);
        List<String> keys = Holders.all(request, auth);
        String guestKey = keys.stream().filter(k -> k.startsWith("S:")).findFirst().orElse(null);
        if (userId != null) {
            String userKey = "U:" + userId;
            Map<String, Object> mine = repo.cartByOwner(userKey);
            Map<String, Object> guest = guestKey == null ? null : repo.cartByOwner(guestKey);
            if (mine == null && guest == null && !create) return null;
            long id = mine != null ? ((Number) mine.get("id")).longValue() : repo.createCart(userKey, userId);
            if (guest != null) {
                repo.merge(((Number) guest.get("id")).longValue(), id);
                log.info("Merged guest cart into the cart of user {}", userId);
            }
            return id;
        }
        if (guestKey == null && !create) return null;
        String key = guestKey != null ? guestKey : Holders.primary(request, auth);
        Map<String, Object> cart = repo.cartByOwner(key);
        if (cart != null) return ((Number) cart.get("id")).longValue();
        return create ? repo.createCart(key, null) : null;
    }

    public String ownerKey(HttpServletRequest request) {
        Long userId = auth.userId(request);
        return userId != null ? "U:" + userId : Holders.primary(request, auth);
    }

    /** Items in the cart, for the header badge. Never creates a session. */
    public int count(HttpServletRequest request) {
        Long id = cartId(request, false);
        return id == null ? 0 : repo.count(id);
    }

    // ================================================================ changes

    /**
     * Adds a line. An original is reserved for the visitor first (one 15-minute hold at a time; {@code switchHold}
     * moves their hold from another original), so it can't be sold to someone else while it sits in the cart.
     */
    public void add(HttpServletRequest request, long paintingId, ShopPricing.Selection sel, boolean switchHold) {
        Map<String, Object> p = repo.painting(paintingId);
        if (p == null || !MarketplaceRepository.PUBLIC_STATUSES.contains(String.valueOf(p.get("stock_status")))) {
            throw new MarketplaceException(404, "NOT_FOUND", "This artwork is no longer listed.");
        }
        ShopPricing.Quote q = ShopPricing.quote(ShopRepository.art(p), repo.options(), sel);
        if ("ORIGINAL".equals(sel.format()) && !q.ok() && "SOLD".equals(p.get("stock_status"))) {
            throw new MarketplaceException(410, "SOLD", q.error());
        }
        if (!q.ok()) throw new MarketplaceException(422, "INVALID", q.error());
        long cartId = cartId(request, true);
        if ("ORIGINAL".equals(sel.format())) {
            market.reserve(paintingId, Holders.primary(request, auth), Holders.all(request, auth), auth.userId(request), switchHold);
            marketRepo.syncStudentListing(paintingId);
            // only one original line per cart: the hold engine allows one hold at a time
            for (Map<String, Object> i : repo.items(cartId)) {
                if ("ORIGINAL".equals(i.get("format")) && ((Number) i.get("painting_id")).longValue() != paintingId) {
                    repo.deleteItem(cartId, ((Number) i.get("id")).longValue());
                }
            }
        }
        repo.upsertItem(cartId, paintingId, q.selection(), q.maxQty());
    }

    public void setQuantity(HttpServletRequest request, long itemId, int qty) {
        Long cartId = cartId(request, false);
        Map<String, Object> item = cartId == null ? null : repo.item(cartId, itemId);
        if (item == null) throw new MarketplaceException(404, "NOT_FOUND", "That item is no longer in your cart.");
        if (qty <= 0) {
            remove(request, itemId);
            return;
        }
        Map<String, Object> p = repo.painting(((Number) item.get("painting_id")).longValue());
        ShopPricing.Quote q = ShopPricing.quote(ShopRepository.art(p), repo.options(), selection(item, qty));
        int max = q.ok() ? q.maxQty() : 1;
        repo.setQuantity(cartId, itemId, Math.max(1, Math.min(qty, max)));
    }

    /** Removes a line; removing an original also gives its hold back to the gallery. */
    public void remove(HttpServletRequest request, long itemId) {
        Long cartId = cartId(request, false);
        Map<String, Object> item = cartId == null ? null : repo.item(cartId, itemId);
        if (item == null) return;
        repo.deleteItem(cartId, itemId);
        if ("ORIGINAL".equals(item.get("format"))) {
            market.release(((Number) item.get("painting_id")).longValue(), Holders.all(request, auth));
        }
    }

    /** Stores a coupon code on the cart. Unknown codes are refused; known ones are kept even if they don't apply yet. */
    public ShopPricing.Coupon applyCoupon(HttpServletRequest request, String code) {
        ShopPricing.Coupon c = repo.coupon(code);
        if (c == null || !c.active()) throw new MarketplaceException(422, "COUPON", "We don't recognise that code. Check the spelling and try again.");
        LocalDateTime now = LocalDateTime.now();
        if (c.expiresAt() != null && now.isAfter(c.expiresAt())) throw new MarketplaceException(422, "COUPON", "This code has expired.");
        if (c.maxUses() != null && c.usedCount() >= c.maxUses()) throw new MarketplaceException(422, "COUPON", "This code has been used up.");
        repo.setCoupon(cartId(request, true), c.code());
        return c;
    }

    public void clearCoupon(HttpServletRequest request) {
        Long id = cartId(request, false);
        if (id != null) repo.setCoupon(id, null);
    }

    static ShopPricing.Selection selection(Map<String, Object> item, int qty) {
        return new ShopPricing.Selection(String.valueOf(item.get("format")), String.valueOf(item.get("size_code")),
                String.valueOf(item.get("finish")), String.valueOf(item.get("frame")), qty);
    }

    // ================================================================ priced view

    /** A cart line with its live price and whether it can be bought right now. */
    public record PricedLine(Long itemId, Map<String, Object> painting, ShopPricing.Quote quote, String status, String message,
                             Long holdExpiresAt, BigDecimal discount) {
        public boolean ok() {
            return "ok".equals(status);
        }
    }

    public record Priced(List<PricedLine> lines, BigDecimal subtotal, BigDecimal discount, BigDecimal shipping, BigDecimal total,
                         boolean needsShipping, boolean hasPrints, ShopPricing.Coupon coupon, String couponError, String country) {
    }

    /**
     * Prices the visitor's cart (plus an optional draft line from the purchase pages, not yet in the cart).
     * @param country delivery country for the shipping estimate (India when unknown)
     */
    public Priced price(HttpServletRequest request, String country, Map<String, Object> draftPainting, ShopPricing.Selection draft) {
        Long cartId = cartId(request, false);
        List<String> keys = Holders.all(request, auth);
        List<ShopPricing.Option> options = repo.options();
        List<PricedLine> lines = new ArrayList<>();
        String couponCode = null;
        if (cartId != null) {
            Map<String, Object> cart = repo.list("select coupon_code from shop_carts where id = ?", cartId).stream().findFirst().orElse(Map.of());
            couponCode = cart.get("coupon_code") == null ? null : String.valueOf(cart.get("coupon_code"));
            for (Map<String, Object> item : repo.items(cartId)) {
                Map<String, Object> p = repo.painting(((Number) item.get("painting_id")).longValue());
                if (p == null) continue;
                lines.add(line(((Number) item.get("id")).longValue(), p, selection(item, ((Number) item.get("quantity")).intValue()), options, keys));
            }
        }
        if (draftPainting != null && draft != null) {
            lines.add(line(null, draftPainting, draft, options, keys));
        }
        return total(lines, repo.coupon(couponCode), country);
    }

    PricedLine line(Long itemId, Map<String, Object> p, ShopPricing.Selection sel, List<ShopPricing.Option> options, List<String> keys) {
        ShopPricing.Art art = ShopRepository.art(p);
        ShopPricing.Quote q = ShopPricing.quote(art, options, sel);
        String status = "ok";
        String message = null;
        Long holdUntil = null;
        String stock = String.valueOf(p.get("stock_status"));
        if (!MarketplaceRepository.PUBLIC_STATUSES.contains(stock)) {
            status = "unavailable";
            message = "This artwork has been taken out of the gallery.";
        } else if (!q.ok()) {
            status = "unavailable";
            message = q.error();
        } else if ("ORIGINAL".equals(sel.format())) {
            long id = ((Number) p.get("id")).longValue();
            MarketplaceService.Hold hold = market.activeHold(id, keys);
            if (hold != null) {
                holdUntil = hold.expiresAtMillis();
            } else if (itemId != null) {
                boolean heldByOther = "RESERVED".equals(stock) && p.get("reserved_until") != null
                        && ShopRepository.ldt(p.get("reserved_until")) != null && ShopRepository.ldt(p.get("reserved_until")).isAfter(LocalDateTime.now());
                status = heldByOther ? "unavailable" : "hold_expired";
                message = heldByOther ? "Another collector is holding this original right now." : "Your 15-minute hold ended. Reserve it again to buy it.";
            }
        }
        return new PricedLine(itemId, p, q, status, message, holdUntil, BigDecimal.ZERO);
    }

    Priced total(List<PricedLine> lines, ShopPricing.Coupon coupon, String country) {
        String c = country == null || country.isBlank() ? "India" : country;
        List<PricedLine> ok = lines.stream().filter(PricedLine::ok).toList();
        BigDecimal subtotal = ok.stream().map(l -> l.quote().lineTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ShopPricing.Line> dl = ok.stream().map(l -> new ShopPricing.Line(((Number) l.painting().get("id")).longValue(),
                String.valueOf(l.painting().get("artist_name")), l.quote().lineTotal())).toList();
        ShopPricing.Discount d = ShopPricing.discount(coupon, dl, LocalDateTime.now());
        List<PricedLine> out = new ArrayList<>();
        int k = 0;
        for (PricedLine l : lines) {
            if (l.ok()) {
                out.add(new PricedLine(l.itemId(), l.painting(), l.quote(), l.status(), l.message(), l.holdExpiresAt(), d.perLine().get(k++)));
            } else {
                out.add(l);
            }
        }
        boolean hasPrints = ok.stream().anyMatch(l -> List.of("CANVAS", "POSTER").contains(l.quote().selection().format()));
        boolean needsShipping = ok.stream().anyMatch(l -> !"DIGITAL".equals(l.quote().selection().format()));
        BigDecimal shipping = BigDecimal.ZERO;
        if (hasPrints) {
            ShopPricing.Option o = ShopPricing.option(repo.options(), "SHIPPING", "india".equalsIgnoreCase(c) ? "PRINTS_INDIA" : "PRINTS_INTL");
            shipping = o == null ? BigDecimal.ZERO : o.price();
        }
        BigDecimal total = subtotal.subtract(d.total()).add(shipping).max(BigDecimal.ZERO);
        return new Priced(out, subtotal, d.total(), shipping, total, needsShipping, hasPrints, coupon,
                coupon == null ? null : d.error(), c);
    }

    // ================================================================ JSON

    public Map<String, Object> json(Priced priced) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> lines = new ArrayList<>();
        int count = 0;
        for (PricedLine l : priced.lines()) {
            if (l.itemId() == null) continue;   // the draft line isn't part of the cart
            lines.add(lineJson(l));
            count += l.quote().selection().qty();
        }
        m.put("lines", lines);
        m.put("count", count);
        m.put("totals", totalsJson(priced));
        return m;
    }

    public static Map<String, Object> lineJson(PricedLine l) {
        Map<String, Object> p = l.painting();
        ShopPricing.Quote q = l.quote();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.itemId());
        m.put("paintingId", p.get("id"));
        m.put("slug", p.get("slug"));
        m.put("title", p.get("title"));
        m.put("artist", p.get("artist_name"));
        m.put("image", p.get("image_url"));
        m.put("format", q.selection().format());
        m.put("size", q.selection().size());
        m.put("finish", q.selection().finish());
        m.put("frame", q.selection().frame());
        m.put("qty", q.selection().qty());
        m.put("maxQty", q.maxQty());
        m.put("label", q.ok() ? q.label() : formatName(q.selection().format()));
        m.put("unitPrice", q.unitPrice());
        m.put("lineTotal", q.lineTotal());
        m.put("discount", l.discount());
        m.put("width", q.widthIn());
        m.put("height", q.heightIn());
        m.put("status", l.status());
        m.put("message", l.message());
        m.put("holdExpiresAt", l.holdExpiresAt());
        return m;
    }

    public static Map<String, Object> totalsJson(Priced priced) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("subtotal", priced.subtotal());
        t.put("discount", priced.discount());
        t.put("shipping", priced.shipping());
        t.put("total", priced.total());
        t.put("needsShipping", priced.needsShipping());
        t.put("hasPrints", priced.hasPrints());
        t.put("country", priced.country());
        t.put("blocked", priced.lines().stream().filter(l -> l.itemId() != null).anyMatch(l -> !l.ok()));
        if (priced.coupon() != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("code", priced.coupon().code());
            c.put("description", priced.coupon().description());
            c.put("error", priced.couponError());
            t.put("coupon", c);
        } else {
            t.put("coupon", null);
        }
        return t;
    }

    public static String formatName(String format) {
        return switch (format) {
            case "ORIGINAL" -> "Original painting";
            case "CANVAS" -> "Limited edition canvas";
            case "POSTER" -> "Fine art poster";
            case "DIGITAL" -> "Digital download";
            default -> "Artwork";
        };
    }

    /** Daily: forget guest carts nobody touched for 30 days. */
    @Scheduled(cron = "0 17 3 * * *")
    public void cleanup() {
        int n = repo.deleteStaleGuestCarts();
        if (n > 0) log.info("Removed {} stale guest cart(s)", n);
    }
}
