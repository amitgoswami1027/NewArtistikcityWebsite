package com.artistikcity.shop;

import com.artistikcity.marketplace.MarketplaceException;
import com.artistikcity.marketplace.MarketplaceRepository;
import com.artistikcity.service.Mailer;
import com.artistikcity.support.DbErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Make an offer" on original paintings.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   buyer offers ──► OPEN ──seller accepts──────────────► ACCEPTED ──buyer pays──► PAID
 *                     │  └─seller counters──► COUNTERED ──buyer accepts─┘    └─pay_by passes──► EXPIRED (hold released)
 *                     │                          ├─buyer offers again (next round) ─► OPEN
 *                     │                          └─buyer declines ──► DECLINED
 *                     ├─seller declines ─► DECLINED      ├─buyer withdraws ─► WITHDRAWN
 *                     ├─no answer in time ─► EXPIRED     └─piece sold / unlisted / another offer accepted ─► CLOSED
 * </pre>
 * Accepting puts the painting on hold for the buyer until {@code pay_by} (a {@code cart_reservations} row keyed {@code U:<buyer>}),
 * at the agreed price: {@link ShopRepository#agreedPrice} feeds the cart, so the normal checkout charges it. Accepting runs in one
 * SERIALIZABLE transaction that locks the painting and its offers, so two acceptances (or an acceptance and someone's Buy now) can't
 * both win; the UNIQUE hold row settles the rest.
 *
 * <h3>Who is the seller</h3>
 * Student listings ({@code source = 'STUDENT'}): the student (My studio > Sell your art). Everything else: the studio, i.e. staff with
 * the marketplace capability (Admin > Sales > Marketplace > Offers). Staff can act on every offer.
 *
 * <h3>Settings</h3> ({@code settings} tag {@code offers}): enabled, min_pct (lowest offer as % of the price), expiry_hours (time to
 * answer), pay_hours (time to pay after acceptance), max_rounds, daily_limit (offers per buyer per 24 h). Per painting:
 * {@code paintings.offers_enabled} (NULL = studio originals yes, student listings no) and {@code offer_min_pct}.
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    public static final List<String> LIVE = List.of("OPEN", "COUNTERED", "ACCEPTED");

    /** Offer rules. */
    public record Settings(boolean enabled, int minPct, int expiryHours, int payHours, int maxRounds, int dailyLimit) {
        public static final Settings DEFAULTS = new Settings(true, 70, 72, 48, 3, 5);
    }

    /** Who is acting. */
    public record Actor(String role, Long userId, String name) {
        public static Actor buyer(long userId, String name) {
            return new Actor("BUYER", userId, name);
        }

        public static Actor seller(long userId, String name) {
            return new Actor("SELLER", userId, name);
        }

        public static Actor staff(String name) {
            return new Actor("STAFF", null, name);
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate serializable;
    private final TransactionTemplate tx;
    private final ShopRepository shop;
    private final MarketplaceRepository marketRepo;
    private final Mailer mailer;
    private final Environment env;

    public OfferService(JdbcTemplate jdbc, PlatformTransactionManager tm, ShopRepository shop, MarketplaceRepository marketRepo, Mailer mailer, Environment env) {
        this.jdbc = jdbc;
        this.shop = shop;
        this.marketRepo = marketRepo;
        this.mailer = mailer;
        this.env = env;
        this.tx = new TransactionTemplate(tm);
        this.serializable = new TransactionTemplate(tm);
        this.serializable.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    static long millis(Object v) {
        LocalDateTime t = ShopRepository.ldt(v);
        return t == null ? 0 : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // ================================================================ settings

    public Settings settings() {
        Map<String, String> v = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("select meta_key, meta_value from settings where meta_tag = 'offers'")) {
            v.put(String.valueOf(r.get("meta_key")), r.get("meta_value") == null ? "" : String.valueOf(r.get("meta_value")).trim());
        }
        Settings d = Settings.DEFAULTS;
        return new Settings(!"0".equals(v.getOrDefault("enabled", "1")), num(v.get("min_pct"), d.minPct()), num(v.get("expiry_hours"), d.expiryHours()),
                num(v.get("pay_hours"), d.payHours()), num(v.get("max_rounds"), d.maxRounds()), num(v.get("daily_limit"), d.dailyLimit()));
    }

    private static int num(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** Checks and saves the rules; returns field errors (empty when saved). */
    public Map<String, String> saveSettings(Settings s) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (s.minPct() < 10 || s.minPct() > 99) errors.put("minPct", "Between 10% and 99% of the price.");
        if (s.expiryHours() < 1 || s.expiryHours() > 336) errors.put("expiryHours", "Between 1 and 336 hours (14 days).");
        if (s.payHours() < 1 || s.payHours() > 168) errors.put("payHours", "Between 1 and 168 hours (7 days).");
        if (s.maxRounds() < 1 || s.maxRounds() > 10) errors.put("maxRounds", "Between 1 and 10 rounds.");
        if (s.dailyLimit() < 1 || s.dailyLimit() > 50) errors.put("dailyLimit", "Between 1 and 50 offers a day.");
        if (!errors.isEmpty()) return errors;
        Map<String, String> kv = new LinkedHashMap<>();
        kv.put("enabled", s.enabled() ? "1" : "0");
        kv.put("min_pct", String.valueOf(s.minPct()));
        kv.put("expiry_hours", String.valueOf(s.expiryHours()));
        kv.put("pay_hours", String.valueOf(s.payHours()));
        kv.put("max_rounds", String.valueOf(s.maxRounds()));
        kv.put("daily_limit", String.valueOf(s.dailyLimit()));
        Timestamp now = ts(LocalDateTime.now());
        kv.forEach((k, val) -> {
            if (jdbc.update("update settings set meta_value = ?, updated_at = ? where meta_tag = 'offers' and meta_key = ?", val, now, k) == 0) {
                jdbc.update("insert into settings (meta_tag, meta_key, meta_value, created_at, updated_at) values ('offers', ?, ?, ?, ?)", k, val, now, now);
            }
        });
        return errors;
    }

    /** Per painting: does it take offers, and the lowest offer. */
    public record Terms(boolean open, boolean enabledForPainting, int minPct, BigDecimal minAmount, BigDecimal price, String reason) {
    }

    public Terms terms(Map<String, Object> p, Settings s) {
        BigDecimal price = ShopRepository.dec(p.get("final_price"));
        boolean student = "STUDENT".equals(String.valueOf(p.get("source")));
        Object flag = p.get("offers_enabled");
        boolean on = flag == null ? !student : ShopRepository.bool(flag);
        int pct = p.get("offer_min_pct") instanceof Number n ? n.intValue() : s.minPct();
        BigDecimal min = price == null ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING);
        String stock = String.valueOf(p.get("stock_status"));
        String reason = !s.enabled() ? "Offers are paused right now."
                : !on ? "This piece is sold at its listed price."
                : !List.of("AVAILABLE", "RESERVED").contains(stock) ? "This piece isn't for sale right now."
                : price == null || price.compareTo(BigDecimal.valueOf(1000)) < 0 ? "This piece is sold at its listed price." : null;
        return new Terms(reason == null, on, pct, min, price, reason);
    }

    // ================================================================ reading

    public Map<String, Object> offer(long id) {
        List<Map<String, Object>> r = jdbc.queryForList("select o.*, p.title, p.slug, p.source, p.artist_name, p.artist_user_id, p.artist_admin_id, p.stock_status,"
                + " p.final_price, p.offers_enabled, p.offer_min_pct, " + MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url from painting_offers o join paintings p on p.id = o.painting_id where o.id = ?", id);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<Map<String, Object>> events(long offerId) {
        return jdbc.queryForList("select actor, actor_name, kind, amount_inr, note, created_at from painting_offer_events where offer_id = ? order by id", offerId);
    }

    /** The buyer's offers, newest first. */
    public List<Map<String, Object>> forBuyer(long userId) {
        return views(jdbc.queryForList("select o.*, p.title, p.slug, p.source, p.artist_name, p.artist_user_id, p.stock_status, p.final_price, "
                + MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url from painting_offers o join paintings p on p.id = o.painting_id where o.user_id = ?"
                + " order by case when o.status in ('COUNTERED', 'ACCEPTED') then 0 when o.status = 'OPEN' then 1 else 2 end, o.updated_at desc, o.id desc limit 100", userId), "BUYER");
    }

    /** Offers on a student's listings. */
    public List<Map<String, Object>> forStudentSeller(long userId) {
        return views(jdbc.queryForList("select o.*, p.title, p.slug, p.source, p.artist_name, p.artist_user_id, p.stock_status, p.final_price, "
                + MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url from painting_offers o join paintings p on p.id = o.painting_id"
                + " where p.source = 'STUDENT' and p.artist_user_id = ?"
                + " order by case when o.status = 'OPEN' then 0 when o.status in ('COUNTERED', 'ACCEPTED') then 1 else 2 end, o.updated_at desc, o.id desc limit 200", userId), "SELLER");
    }

    /** Every offer (staff), or one artist's (a teacher who only manages their own pieces). */
    public List<Map<String, Object>> forStaff(Long artistAdminId) {
        return views(jdbc.queryForList("select o.*, p.title, p.slug, p.source, p.artist_name, p.artist_user_id, p.stock_status, p.final_price, "
                + MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url from painting_offers o join paintings p on p.id = o.painting_id"
                + (artistAdminId == null ? "" : " where p.artist_admin_id = ?")
                + " order by case when o.status = 'OPEN' then 0 when o.status in ('COUNTERED', 'ACCEPTED') then 1 else 2 end, o.updated_at desc, o.id desc limit 300",
                artistAdminId == null ? new Object[0] : new Object[]{artistAdminId}), "STAFF");
    }

    /** Live offers of this buyer on one painting (the artwork page shows "Your offer ..."). */
    public Map<String, Object> liveForBuyer(long paintingId, long userId) {
        List<Map<String, Object>> r = forBuyer(userId).stream()
                .filter(o -> ((Number) o.get("paintingId")).longValue() == paintingId && LIVE.contains(String.valueOf(o.get("status")))).toList();
        return r.isEmpty() ? null : r.get(0);
    }

    /** Counts for the menus: offers waiting for this person. */
    public Map<String, Object> counts(long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buyerAction", jdbc.queryForObject("select count(*) from painting_offers where user_id = ? and (status in ('COUNTERED', 'ACCEPTED') or buyer_unread = 1)", Long.class, userId));
        m.put("buyerTotal", jdbc.queryForObject("select count(*) from painting_offers where user_id = ?", Long.class, userId));
        m.put("sellerOpen", jdbc.queryForObject("select count(*) from painting_offers o join paintings p on p.id = o.painting_id where p.source = 'STUDENT'"
                + " and p.artist_user_id = ? and o.status = 'OPEN'", Long.class, userId));
        return m;
    }

    public long staffOpen() {
        Long n = jdbc.queryForObject("select count(*) from painting_offers o join paintings p on p.id = o.painting_id where o.status = 'OPEN' and coalesce(p.source, '') <> 'STUDENT'", Long.class);
        return n == null ? 0 : n;
    }

    List<Map<String, Object>> views(List<Map<String, Object>> rows, String viewer) {
        Settings s = settings();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> o : rows) out.add(view(o, viewer, s));
        return out;
    }

    /** JSON shape shared by the buyer page, the studio and the admin console. */
    public Map<String, Object> view(Map<String, Object> o, String viewer, Settings s) {
        long id = ((Number) o.get("id")).longValue();
        String status = String.valueOf(o.get("status"));
        int round = ((Number) o.get("round_no")).intValue();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("paintingId", o.get("painting_id"));
        m.put("title", o.get("title"));
        m.put("slug", o.get("slug"));
        m.put("image", o.get("image_url"));
        m.put("artist", o.get("artist_name"));
        m.put("student", "STUDENT".equals(String.valueOf(o.get("source"))));
        m.put("status", status);
        m.put("listPrice", o.get("list_price_inr"));
        m.put("currentPrice", o.get("final_price"));
        m.put("amount", o.get("amount_inr"));
        m.put("counter", o.get("counter_inr"));
        m.put("agreed", o.get("agreed_inr"));
        m.put("round", round);
        m.put("maxRounds", s.maxRounds());
        m.put("message", o.get("message"));
        m.put("expiresAt", o.get("expires_at") == null ? null : millis(o.get("expires_at")));
        m.put("payBy", o.get("pay_by") == null ? null : millis(o.get("pay_by")));
        m.put("closedReason", o.get("closed_reason"));
        m.put("orderId", o.get("order_id"));
        m.put("createdAt", millis(o.get("created_at")));
        m.put("updatedAt", millis(o.get("updated_at")));
        if (!"BUYER".equals(viewer)) {
            m.put("buyer", o.get("buyer_name"));
            if ("STAFF".equals(viewer)) m.put("buyerEmail", o.get("buyer_email"));
        }
        m.put("unread", "BUYER".equals(viewer) ? ShopRepository.bool(o.get("buyer_unread")) : ShopRepository.bool(o.get("seller_unread")));
        List<Map<String, Object>> ev = new ArrayList<>();
        for (Map<String, Object> e : events(id)) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("actor", e.get("actor"));
            x.put("who", "BUYER".equals(viewer) && !"BUYER".equals(e.get("actor")) ? (String.valueOf(e.get("actor")).equals("SYSTEM") ? "ArtistikCity" : String.valueOf(o.get("artist_name")))
                    : e.get("actor_name"));
            x.put("kind", e.get("kind"));
            x.put("amount", e.get("amount_inr"));
            x.put("note", e.get("note"));
            x.put("at", millis(e.get("created_at")));
            ev.add(x);
        }
        m.put("events", ev);
        // what this viewer may do now
        List<String> can = new ArrayList<>();
        boolean sellerSide = !"BUYER".equals(viewer);
        switch (status) {
            case "OPEN" -> {
                if (sellerSide) { can.add("accept"); can.add("counter"); can.add("decline"); } else can.add("withdraw");
            }
            case "COUNTERED" -> {
                if (!sellerSide) {
                    can.add("accept");
                    if (round < s.maxRounds()) can.add("reoffer");
                    can.add("decline");
                } else can.add("withdraw");
            }
            case "ACCEPTED" -> {
                if (!sellerSide) { can.add("pay"); can.add("withdraw"); } else if ("STAFF".equals(viewer)) can.add("cancel");
            }
            default -> { }
        }
        m.put("can", can);
        return m;
    }

    // ================================================================ buyer: make an offer

    /** A new offer (round 1). Throws MarketplaceException with a code and a message for the form. */
    public Map<String, Object> create(long paintingId, long userId, String buyerName, String buyerEmail, BigDecimal amount, String message) {
        Settings s = settings();
        Map<String, Object> p = shop.painting(paintingId);
        if (p == null || !MarketplaceRepository.PUBLIC_STATUSES.contains(String.valueOf(p.get("stock_status")))) {
            throw new MarketplaceException(404, "NOT_FOUND", "This artwork is no longer listed.");
        }
        Terms t = terms(p, s);
        if (!t.open()) throw new MarketplaceException(409, "NO_OFFERS", t.reason());
        if (p.get("artist_user_id") instanceof Number a && a.longValue() == userId) {
            throw new MarketplaceException(409, "OWN_PIECE", "This is your own painting.");
        }
        // someone else is checking out right now: no new offers until that hold ends
        Object holder = p.get("holder_key");
        LocalDateTime until = ShopRepository.ldt(p.get("reserved_until"));
        if ("RESERVED".equals(p.get("stock_status")) && until != null && until.isAfter(LocalDateTime.now()) && !("U:" + userId).equals(String.valueOf(holder))) {
            throw new MarketplaceException(409, "HELD_BY_OTHER", "Another collector is checking out this piece right now. Try again later.");
        }
        validateAmount(amount, t);
        String msg = message == null ? null : message.strip();
        if (msg != null && msg.length() > 500) throw new MarketplaceException(422, "INVALID", "Keep your message under 500 characters.", Map.of("field", "message"));
        if (msg != null && msg.isEmpty()) msg = null;
        final String note = msg;
        LocalDateTime now = LocalDateTime.now();
        Long recent = jdbc.queryForObject("select count(*) from painting_offer_events e join painting_offers o on o.id = e.offer_id"
                + " where o.user_id = ? and e.actor = 'BUYER' and e.kind = 'OFFER' and e.created_at > ?", Long.class, userId, ts(now.minusHours(24)));
        if (recent != null && recent >= s.dailyLimit()) {
            throw new MarketplaceException(429, "RATE_LIMIT", "You've made " + s.dailyLimit() + " offers in the last 24 hours. Please try again tomorrow.");
        }
        Long id = tx.execute(st -> {
            // one live offer per buyer and painting (rows locked so a double click can't make two)
            List<Map<String, Object>> live = jdbc.queryForList("select id from painting_offers where painting_id = ? and user_id = ? and status in ('OPEN', 'COUNTERED', 'ACCEPTED') for update",
                    paintingId, userId);
            if (!live.isEmpty()) throw new MarketplaceException(409, "ALREADY", "You already have an offer on this piece. See Your offers.");
            org.springframework.jdbc.support.GeneratedKeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(c -> {
                var ps = c.prepareStatement("insert into painting_offers (painting_id, user_id, buyer_name, buyer_email, list_price_inr, amount_inr, status, round_no, message,"
                        + " expires_at, buyer_unread, seller_unread, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'OPEN', 1, ?, ?, 0, 1, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, paintingId);
                ps.setLong(2, userId);
                ps.setString(3, buyerName);
                ps.setString(4, buyerEmail);
                ps.setBigDecimal(5, t.price());
                ps.setBigDecimal(6, amount);
                ps.setString(7, note);
                ps.setTimestamp(8, ts(now.plusHours(s.expiryHours())));
                ps.setTimestamp(9, ts(now));
                ps.setTimestamp(10, ts(now));
                return ps;
            }, kh);
            long newId = kh.getKey().longValue();
            event(newId, "BUYER", buyerName, "OFFER", amount, note, now);
            return newId;
        });
        Map<String, Object> o = offer(id);
        notifySeller(o, "New offer on “" + o.get("title") + "”", buyerName + " offered ₹" + rupees(amount) + " for “" + o.get("title") + "” (listed at ₹" + rupees(t.price()) + ").",
                "Answer within " + s.expiryHours() + " hours: accept, counter or decline.", amount, "Their offer", note);
        return view(o, "BUYER", s);
    }

    private void validateAmount(BigDecimal amount, Terms t) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new MarketplaceException(422, "INVALID", "Enter your offer in whole rupees.", Map.of("field", "amount"));
        }
        if (amount.compareTo(t.price()) >= 0) {
            throw new MarketplaceException(422, "AT_PRICE", "That's the full price, so there's nothing to negotiate. Use Buy now instead.", Map.of("field", "amount"));
        }
        if (amount.compareTo(t.minAmount()) < 0) {
            throw new MarketplaceException(422, "TOO_LOW", "The artist considers offers from ₹" + rupees(t.minAmount()) + " (" + t.minPct() + "% of the price).",
                    Map.of("field", "amount", "min", t.minAmount()));
        }
    }

    // ================================================================ answers (buyer and seller)

    /**
     * One step in the conversation. {@code action}: accept | counter | decline | withdraw | reoffer | cancel.
     * {@code expectSeller}: the caller is the seller side (student seller or staff).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> act(long offerId, Actor actor, String action, BigDecimal amount, String note) {
        Settings s = settings();
        Map<String, Object> o = offer(offerId);
        if (o == null) throw new MarketplaceException(404, "NOT_FOUND", "That offer no longer exists.");
        boolean buyer = "BUYER".equals(actor.role());
        String status = String.valueOf(o.get("status"));
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (cleanNote != null && cleanNote.length() > 500) throw new MarketplaceException(422, "INVALID", "Keep the note under 500 characters.", Map.of("field", "note"));
        LocalDateTime now = LocalDateTime.now();
        List<String> allowed = (List<String>) view(o, buyer ? "BUYER" : actor.role(), s).get("can");
        if (!allowed.contains(action)) {
            throw new MarketplaceException(409, "STALE", "This offer has moved on (it is now " + status.toLowerCase(Locale.ROOT) + "). Refresh to see the latest.");
        }
        switch (action) {
            case "accept" -> accept(o, actor, s, now);
            case "counter" -> {
                Terms t = terms(o, s);
                BigDecimal offered = ShopRepository.dec(o.get("amount_inr"));
                if (amount == null || amount.stripTrailingZeros().scale() > 0 || amount.compareTo(offered) <= 0 || amount.compareTo(t.price()) >= 0) {
                    throw new MarketplaceException(422, "INVALID", "Counter with a whole-rupee amount above ₹" + rupees(offered) + " and below the price of ₹" + rupees(t.price()) + ".",
                            Map.of("field", "amount"));
                }
                transition(offerId, status, "COUNTERED", "counter_inr = ?, expires_at = ?, buyer_unread = 1, seller_unread = 0", List.of(amount, ts(now.plusHours(s.expiryHours()))), now);
                event(offerId, actor.role(), actor.name(), "COUNTER", amount, cleanNote, now);
                notifyBuyer(offer(offerId), o.get("artist_name") + " made a counter-offer", o.get("artist_name") + " can't do ₹" + rupees(offered)
                        + " but suggests ₹" + rupees(amount) + " for “" + o.get("title") + "”.", "Accept it, offer again or decline within " + s.expiryHours() + " hours.",
                        amount, "Counter-offer", cleanNote, "See the counter-offer");
            }
            case "decline" -> {
                transition(offerId, status, "DECLINED", buyer ? "seller_unread = 1, buyer_unread = 0" : "buyer_unread = 1, seller_unread = 0", List.of(), now);
                event(offerId, actor.role(), actor.name(), "DECLINE", null, cleanNote, now);
                if (buyer) {
                    notifySeller(offer(offerId), "Counter-offer declined", o.get("buyer_name") + " declined your counter-offer for “" + o.get("title") + "”.", null, null, null, cleanNote);
                } else {
                    notifyBuyer(offer(offerId), "Your offer wasn't accepted", "The artist declined your offer of ₹" + rupees(ShopRepository.dec(o.get("amount_inr"))) + " for “" + o.get("title") + "”.",
                            "You can still buy it at the listed price while it's available.", null, null, cleanNote, "See the artwork");
                }
            }
            case "withdraw" -> {
                transition(offerId, status, "WITHDRAWN", buyer ? "seller_unread = 1" : "buyer_unread = 1", List.of(), now);
                event(offerId, actor.role(), actor.name(), "WITHDRAW", null, cleanNote, now);
                if ("ACCEPTED".equals(status)) releaseOfferHold(o);
                if (buyer) notifySeller(offer(offerId), "Offer withdrawn", o.get("buyer_name") + " withdrew their offer for “" + o.get("title") + "”.", null, null, null, cleanNote);
                else notifyBuyer(offer(offerId), "Counter-offer withdrawn", "The artist withdrew the counter-offer for “" + o.get("title") + "”.", null, null, null, cleanNote, "See the artwork");
            }
            case "cancel" -> {
                transition(offerId, status, "CLOSED", "closed_reason = ?, buyer_unread = 1", List.of("Cancelled by the studio"), now);
                event(offerId, actor.role(), actor.name(), "CANCEL", null, cleanNote, now);
                releaseOfferHold(o);
                notifyBuyer(offer(offerId), "Your accepted offer was cancelled", "The studio cancelled the agreed price for “" + o.get("title") + "”.",
                        "If you think this is a mistake, reply to this e-mail.", null, null, cleanNote, "See the artwork");
            }
            case "reoffer" -> {
                Terms t = terms(o, s);
                BigDecimal counter = ShopRepository.dec(o.get("counter_inr"));
                BigDecimal last = ShopRepository.dec(o.get("amount_inr"));
                if (!t.open()) throw new MarketplaceException(409, "NO_OFFERS", t.reason());
                validateAmount(amount, t);
                if (amount.compareTo(last) <= 0 || (counter != null && amount.compareTo(counter) >= 0)) {
                    throw new MarketplaceException(422, "INVALID", "Offer more than your last offer (₹" + rupees(last) + ")" + (counter != null ? " and less than the counter-offer (₹" + rupees(counter) + "), or accept the counter-offer." : "."),
                            Map.of("field", "amount"));
                }
                transition(offerId, status, "OPEN", "amount_inr = ?, counter_inr = null, round_no = round_no + 1, expires_at = ?, seller_unread = 1, buyer_unread = 0",
                        List.of(amount, ts(now.plusHours(s.expiryHours()))), now);
                event(offerId, "BUYER", actor.name(), "OFFER", amount, cleanNote, now);
                notifySeller(offer(offerId), "New offer on “" + o.get("title") + "”", o.get("buyer_name") + " offered ₹" + rupees(amount) + " (round " + (((Number) o.get("round_no")).intValue() + 1) + ").",
                        "Answer within " + s.expiryHours() + " hours.", amount, "Their offer", cleanNote);
            }
            default -> throw new MarketplaceException(400, "INVALID", "Unknown action.");
        }
        return view(offer(offerId), buyer ? "BUYER" : actor.role(), s);
    }

    private void transition(long id, String from, String to, String extraSet, List<Object> extraArgs, LocalDateTime now) {
        List<Object> args = new ArrayList<>();
        args.add(to);
        args.addAll(extraArgs);
        args.add(ts(now));
        args.add(id);
        args.add(from);
        int n = jdbc.update("update painting_offers set status = ?" + (extraSet.isBlank() ? "" : ", " + extraSet) + ", updated_at = ? where id = ? and status = ?", args.toArray());
        if (n == 0) throw new MarketplaceException(409, "STALE", "This offer changed a moment ago. Refresh to see the latest.");
    }

    private void event(long offerId, String actor, String name, String kind, BigDecimal amount, String note, LocalDateTime now) {
        jdbc.update("insert into painting_offer_events (offer_id, actor, actor_name, kind, amount_inr, note, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                offerId, actor, name, kind, amount, note, ts(now));
    }

    /**
     * The deal: holds the original for the buyer until pay_by at the agreed price and closes every other live offer on it.
     * One SERIALIZABLE transaction with the painting and its offers locked.
     */
    void accept(Map<String, Object> o, Actor actor, Settings s, LocalDateTime now) {
        long offerId = ((Number) o.get("id")).longValue();
        long paintingId = ((Number) o.get("painting_id")).longValue();
        long buyerId = ((Number) o.get("user_id")).longValue();
        String holder = "U:" + buyerId;
        LocalDateTime payBy = now.plusHours(s.payHours());
        List<Long> closed = new ArrayList<>();
        BigDecimal[] agreed = new BigDecimal[1];
        try {
            serializable.executeWithoutResult(st -> {
                closed.clear();
                List<Map<String, Object>> pr = jdbc.queryForList("select stock_status, version from paintings where id = ? for update", paintingId);
                if (pr.isEmpty()) throw new MarketplaceException(404, "NOT_FOUND", "This artwork is no longer listed.");
                List<Map<String, Object>> offers = jdbc.queryForList("select id, status, user_id, amount_inr, counter_inr from painting_offers where painting_id = ? for update", paintingId);
                Map<String, Object> me = offers.stream().filter(x -> ((Number) x.get("id")).longValue() == offerId).findFirst().orElse(null);
                String st0 = me == null ? "" : String.valueOf(me.get("status"));
                if (me == null || !List.of("OPEN", "COUNTERED").contains(st0)) {
                    throw new MarketplaceException(409, "STALE", "This offer changed a moment ago. Refresh to see the latest.");
                }
                if (offers.stream().anyMatch(x -> "ACCEPTED".equals(x.get("status")))) {
                    throw new MarketplaceException(409, "TAKEN", "Another offer on this piece was accepted a moment ago.");
                }
                String stock = String.valueOf(pr.get(0).get("stock_status"));
                List<Map<String, Object>> holds = jdbc.queryForList("select id, session_or_user_id, expires_at from cart_reservations where painting_id = ?", paintingId);
                if (!holds.isEmpty()) {
                    Map<String, Object> h = holds.get(0);
                    boolean live = ShopRepository.ldt(h.get("expires_at")).isAfter(now);
                    if (live && !holder.equals(String.valueOf(h.get("session_or_user_id")))) {
                        throw new MarketplaceException(409, "HELD_BY_OTHER", "Someone is checking out this piece right now. Try again when their hold ends (at most 15 minutes).");
                    }
                    jdbc.update("delete from cart_reservations where id = ?", h.get("id"));
                    if (!live) stock = "RESERVED".equals(stock) ? "AVAILABLE" : stock;
                }
                if ("SOLD".equals(stock)) throw new MarketplaceException(410, "SOLD", "This piece has been sold.");
                if (!List.of("AVAILABLE", "RESERVED").contains(stock)) throw new MarketplaceException(409, "UNAVAILABLE", "This piece isn't for sale right now.");
                jdbc.update("update paintings set stock_status = 'RESERVED', version = version + 1, updated_at = ? where id = ?", ts(now), paintingId);
                jdbc.update("insert into cart_reservations (session_or_user_id, painting_id, user_id, reserved_at, expires_at) values (?, ?, ?, ?, ?)",
                        holder, paintingId, buyerId, ts(now), ts(payBy));
                agreed[0] = "COUNTERED".equals(st0) ? ShopRepository.dec(me.get("counter_inr")) : ShopRepository.dec(me.get("amount_inr"));
                jdbc.update("update painting_offers set status = 'ACCEPTED', agreed_inr = ?, pay_by = ?, expires_at = null, buyer_unread = ?, seller_unread = ?, updated_at = ? where id = ?",
                        agreed[0], ts(payBy), "BUYER".equals(actor.role()) ? 0 : 1, "BUYER".equals(actor.role()) ? 1 : 0, ts(now), offerId);
                event(offerId, actor.role(), actor.name(), "ACCEPT", agreed[0], null, now);
                for (Map<String, Object> x : offers) {
                    long xid = ((Number) x.get("id")).longValue();
                    if (xid != offerId && List.of("OPEN", "COUNTERED").contains(String.valueOf(x.get("status")))) {
                        jdbc.update("update painting_offers set status = 'CLOSED', closed_reason = 'The artist accepted another offer', buyer_unread = 1, updated_at = ? where id = ?", ts(now), xid);
                        event(xid, "SYSTEM", null, "CLOSE", null, "The artist accepted another offer.", now);
                        closed.add(xid);
                    }
                }
            });
        } catch (MarketplaceException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e instanceof DataIntegrityViolationException || DbErrors.isDuplicateKey(e) || DbErrors.isConcurrencyConflict(e)) {
                log.info("Offer {} lost an acceptance race: {}", offerId, e.getClass().getSimpleName());
                throw new MarketplaceException(409, "JUST_TAKEN", "Someone else got to this piece a moment ago. Refresh to see the latest.");
            }
            throw e;
        }
        marketRepo.syncStudentListing(paintingId);
        Map<String, Object> now1 = offer(offerId);
        String hours = s.payHours() + " hours";
        if ("BUYER".equals(actor.role())) {
            notifySeller(now1, "Counter-offer accepted", o.get("buyer_name") + " accepted ₹" + rupees(agreed[0]) + " for “" + o.get("title") + "”.",
                    "The piece is held for them for " + hours + " while they pay.", agreed[0], "Agreed price", null);
        }
        notifyBuyer(now1, "Your offer was accepted", "“" + o.get("title") + "” is yours for ₹" + rupees(agreed[0]) + ".",
                "It's held only for you for " + hours + ". Pay before then, or it goes back on sale.", agreed[0], "Agreed price", null, "Pay now");
        for (Long xid : closed) {
            Map<String, Object> x = offer(xid);
            notifyBuyer(x, "Offer closed", "The artist accepted another offer for “" + x.get("title") + "”, so yours is closed.",
                    "If that buyer doesn't pay in time, the piece goes back on sale.", null, null, null, "See the artwork");
        }
    }

    /** Gives back the hold an accepted offer had (withdrawn, cancelled, expired). */
    void releaseOfferHold(Map<String, Object> o) {
        long paintingId = ((Number) o.get("painting_id")).longValue();
        String holder = "U:" + ((Number) o.get("user_id")).longValue();
        tx.executeWithoutResult(st -> {
            int n = jdbc.update("delete from cart_reservations where painting_id = ? and session_or_user_id = ?", paintingId, holder);
            if (n > 0) {
                jdbc.update("update paintings set stock_status = 'AVAILABLE', version = version + 1, updated_at = ? where id = ? and stock_status = 'RESERVED'",
                        ts(LocalDateTime.now()), paintingId);
            }
        });
        marketRepo.syncStudentListing(paintingId);
    }

    // ================================================================ housekeeping

    /**
     * Every minute: unanswered offers expire, unpaid accepted offers expire (hold released), a reminder 12 h before pay_by,
     * and offers on pieces that sold or left the gallery close (PAID for the buyer who bought it).
     */
    @Scheduled(cron = "30 */1 * * * *")
    public void housekeeping() {
        try {
            sweep();
        } catch (RuntimeException e) {
            log.warn("Offer housekeeping failed", e);
        }
    }

    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> r : jdbc.queryForList("select id, status from painting_offers where status in ('OPEN', 'COUNTERED') and expires_at < ?", ts(now))) {
            long id = ((Number) r.get("id")).longValue();
            if (jdbc.update("update painting_offers set status = 'EXPIRED', buyer_unread = 1, updated_at = ? where id = ? and status = ?", ts(now), id, r.get("status")) == 0) continue;
            event(id, "SYSTEM", null, "EXPIRE", null, "OPEN".equals(r.get("status")) ? "No answer in time." : "The counter-offer wasn't answered in time.", now);
            Map<String, Object> o = offer(id);
            notifyBuyer(o, "Your offer expired", "OPEN".equals(r.get("status")) ? "The artist didn't answer your offer for “" + o.get("title") + "” in time."
                    : "The counter-offer for “" + o.get("title") + "” expired.", "You can make a new offer or buy it at the listed price while it's available.", null, null, null, "See the artwork");
        }
        syncSold();
        for (Map<String, Object> r : jdbc.queryForList("select id from painting_offers where status = 'ACCEPTED' and pay_by < ?", ts(now))) {
            long id = ((Number) r.get("id")).longValue();
            Map<String, Object> o = offer(id);
            if (jdbc.update("update painting_offers set status = 'EXPIRED', buyer_unread = 1, seller_unread = 1, updated_at = ? where id = ? and status = 'ACCEPTED'", ts(now), id) == 0) continue;
            event(id, "SYSTEM", null, "EXPIRE", null, "Not paid in time; the piece is back on sale.", now);
            releaseOfferHold(o);
            notifyBuyer(o, "Your agreed price expired", "The time to pay for “" + o.get("title") + "” at ₹" + rupees(ShopRepository.dec(o.get("agreed_inr"))) + " ran out.",
                    "The piece is back on sale.", null, null, null, "See the artwork");
            notifySeller(o, "Accepted offer not paid", o.get("buyer_name") + " didn't pay for “" + o.get("title") + "” in time. It's back on sale.", null, null, null, null);
        }
        for (Map<String, Object> r : jdbc.queryForList("select id from painting_offers where status = 'ACCEPTED' and reminded_at is null and pay_by < ? and pay_by > ?",
                ts(now.plusHours(12)), ts(now))) {
            long id = ((Number) r.get("id")).longValue();
            if (jdbc.update("update painting_offers set reminded_at = ? where id = ? and reminded_at is null", ts(now), id) == 0) continue;
            Map<String, Object> o = offer(id);
            notifyBuyer(o, "12 hours left to pay", "Your agreed price for “" + o.get("title") + "” ends soon.", "Pay before the hold ends, or the piece goes back on sale.",
                    ShopRepository.dec(o.get("agreed_inr")), "Agreed price", null, "Pay now");
        }
    }

    /** Offers on pieces that sold or were unlisted: PAID for the buyer who bought it, CLOSED for the rest. */
    public void syncSold() {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = jdbc.queryForList("select o.id, o.status, o.user_id, o.painting_id, p.stock_status from painting_offers o join paintings p on p.id = o.painting_id"
                + " where o.status in ('OPEN', 'COUNTERED', 'ACCEPTED') and p.stock_status not in ('AVAILABLE', 'RESERVED')");
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            List<Map<String, Object>> paid = jdbc.queryForList("select o.order_id from shop_orders o join shop_order_items i on i.order_id = o.order_id"
                    + " where o.user_id = ? and i.painting_id = ? and i.format = 'ORIGINAL' and i.status = 'OK' and o.status in ('PAID', 'PACKED', 'SHIPPED', 'DELIVERED')"
                    + " order by o.created_at desc limit 1", r.get("user_id"), r.get("painting_id"));
            if ("ACCEPTED".equals(r.get("status")) && !paid.isEmpty()) {
                jdbc.update("update painting_offers set status = 'PAID', order_id = ?, buyer_unread = 0, seller_unread = 1, updated_at = ? where id = ? and status = 'ACCEPTED'",
                        paid.get(0).get("order_id"), ts(now), id);
                event(id, "SYSTEM", null, "PAID", null, "Paid: order " + paid.get(0).get("order_id") + ".", now);
                Map<String, Object> o = offer(id);
                notifySeller(o, "Accepted offer paid", o.get("buyer_name") + " paid ₹" + rupees(ShopRepository.dec(o.get("agreed_inr"))) + " for “" + o.get("title") + "”.",
                        "It's now in your orders to pack and ship.", ShopRepository.dec(o.get("agreed_inr")), "Paid", null);
            } else {
                String reason = "SOLD".equals(r.get("stock_status")) ? "The piece was sold" : "The piece is no longer listed";
                if (jdbc.update("update painting_offers set status = 'CLOSED', closed_reason = ?, buyer_unread = 1, updated_at = ? where id = ? and status = ?",
                        reason, ts(now), id, r.get("status")) == 0) continue;
                event(id, "SYSTEM", null, "CLOSE", null, reason + ".", now);
                Map<String, Object> o = offer(id);
                notifyBuyer(o, "Offer closed", reason + ", so your offer for “" + o.get("title") + "” is closed.", null, null, null, null, "Browse the marketplace");
            }
        }
    }

    /** The buyer opened Your offers: nothing new for them now. */
    public void markBuyerRead(long userId) {
        jdbc.update("update painting_offers set buyer_unread = 0 where user_id = ? and buyer_unread = 1", userId);
    }

    public void markSellerRead(List<Long> ids) {
        for (Long id : ids) jdbc.update("update painting_offers set seller_unread = 0 where id = ?", id);
    }

    /** Who sells this piece: a student user id, or null for the studio. */
    public static Long studentSeller(Map<String, Object> o) {
        return "STUDENT".equals(String.valueOf(o.get("source"))) && o.get("artist_user_id") instanceof Number n ? n.longValue() : null;
    }

    // ================================================================ e-mail

    String base() {
        String u = env.getProperty("app.url", "");
        return u == null ? "" : u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private void notifyBuyer(Map<String, Object> o, String subject, String line1, String line2, BigDecimal amount, String amountLabel, String note, String button) {
        try {
            Map<String, Object> m = model(o, String.valueOf(o.get("buyer_name")), subject, line1, line2, amount, amountLabel, note);
            String url = "Browse the marketplace".equals(button) ? base() + "/marketplace" : "See the artwork".equals(button) ? base() + "/marketplace/" + o.get("slug") : base() + "/user/offers";
            m.put("url", url);
            m.put("button", button);
            m.put("footnote", "You are getting this because you made an offer on ArtistikCity. Your offers: " + base() + "/user/offers");
            mailer.send(String.valueOf(o.get("buyer_email")), subject, "emails/offer", m);
        } catch (RuntimeException e) {
            log.warn("Could not e-mail the buyer about offer {}", o.get("id"), e);
        }
    }

    private void notifySeller(Map<String, Object> o, String subject, String line1, String line2, BigDecimal amount, String amountLabel, String note) {
        try {
            Long student = studentSeller(o);
            String to;
            String name;
            String url;
            if (student != null) {
                List<Map<String, Object>> u = jdbc.queryForList("select name, email from users where id = ?", student);
                if (u.isEmpty() || u.get(0).get("email") == null) return;
                to = String.valueOf(u.get(0).get("email"));
                name = String.valueOf(u.get(0).get("name"));
                url = base() + "/dashboard/sell#offers";
            } else {
                to = env.getProperty("app.admin-email", "");
                name = "studio";
                url = base() + "/admin/dashboard/marketplace?tab=offers";
            }
            if (to == null || to.isBlank()) return;
            Map<String, Object> m = model(o, name, subject, line1, line2, amount, amountLabel, note);
            m.put("url", url);
            m.put("button", "Answer the offer");
            m.put("footnote", "You are getting this because you sell this artwork on ArtistikCity.");
            mailer.send(to, subject, "emails/offer", m);
        } catch (RuntimeException e) {
            log.warn("Could not e-mail the seller about offer {}", o.get("id"), e);
        }
    }

    private Map<String, Object> model(Map<String, Object> o, String name, String heading, String line1, String line2, BigDecimal amount, String amountLabel, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("heading", heading);
        m.put("name", name);
        List<String> lines = new ArrayList<>();
        if (line1 != null) lines.add(line1);
        if (line2 != null) lines.add(line2);
        m.put("lines", lines);
        m.put("title", o.get("title"));
        m.put("listPrice", rupees(ShopRepository.dec(o.get("list_price_inr"))));
        m.put("amount", amount == null ? null : rupees(amount));
        m.put("amountLabel", amountLabel);
        m.put("note", note);
        return m;
    }

    static String rupees(BigDecimal v) {
        if (v == null) return "0";
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN")).format(v.setScale(0, RoundingMode.HALF_UP));
    }
}
