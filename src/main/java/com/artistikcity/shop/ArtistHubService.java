package com.artistikcity.shop;

import com.artistikcity.admin.SiteSettings;
import com.artistikcity.marketplace.MarketplaceException;
import com.artistikcity.service.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Meet the artist": contact links for a listing and the in-app conversation between a collector and the artist
 * (or the studio on their behalf). A thread belongs to one collector (owner key) and one artwork. The artist side
 * replies from the admin console; both sides get an e-mail when the other writes, so nobody has to keep the page open.
 */
@Service
public class ArtistHubService {

    private static final Logger log = LoggerFactory.getLogger(ArtistHubService.class);
    public static final int MAX_BODY = 2000;

    private final JdbcTemplate jdbc;
    private final SiteSettings site;
    private final Mailer mailer;
    private final Environment env;

    public ArtistHubService(JdbcTemplate jdbc, SiteSettings site, Mailer mailer, Environment env) {
        this.jdbc = jdbc;
        this.site = site;
        this.mailer = mailer;
        this.env = env;
    }

    private static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    // ================================================================ contact links

    /** WhatsApp, Instagram and e-mail for a listing: the artist's own when set, otherwise the studio's. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> contact(Map<String, Object> p) {
        Map<String, Object> info = site.publicInfo();
        Map<String, Object> contact = info.get("contact") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> social = info.get("social") instanceof Map<?, ?> m2 ? (Map<String, Object>) m2 : Map.of();
        String phone = firstNonBlank(str(p.get("artist_whatsapp")), str(contact.get("whatsapp")), str(contact.get("phone")));
        String insta = handle(firstNonBlank(str(p.get("artist_instagram")), str(social.get("instagram"))));
        String email = firstNonBlank(str(p.get("artist_email")), str(contact.get("email")), env.getProperty("app.admin-email", ""));
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("whatsapp", whatsappNumber(phone));
        c.put("instagram", insta);
        c.put("email", email == null || email.isBlank() ? null : email);
        c.put("own", str(p.get("artist_whatsapp")) != null || str(p.get("artist_instagram")) != null || str(p.get("artist_email")) != null);
        return c;
    }

    /** Digits only, with India's country code added to a bare 10-digit mobile number; null when it isn't a phone number. */
    static String whatsappNumber(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.length() == 10) d = "91" + d;
        if (d.length() == 11 && d.startsWith("0")) d = "91" + d.substring(1);
        return d.length() >= 11 && d.length() <= 15 ? d : null;
    }

    /** "@name", "name" or an instagram.com URL -> "name". */
    static String handle(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("instagram\\.com/([A-Za-z0-9._]{1,30})").matcher(s);
        if (m.find()) return m.group(1);
        s = s.startsWith("@") ? s.substring(1) : s;
        return s.matches("[A-Za-z0-9._]{1,30}") ? s : null;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... v) {
        for (String s : v) if (s != null && !s.isBlank()) return s;
        return null;
    }

    // ================================================================ threads (collector side)

    public Map<String, Object> threadFor(String ownerKey, long paintingId) {
        List<Map<String, Object>> r = jdbc.queryForList("select * from artist_threads where owner_key = ? and painting_id = ? order by id desc limit 1", ownerKey, paintingId);
        return r.isEmpty() ? null : r.get(0);
    }

    /** The collector's conversation about an artwork (marks the artist's replies as read). */
    public Map<String, Object> conversation(List<String> ownerKeys, long paintingId) {
        Map<String, Object> t = null;
        for (String k : ownerKeys) {
            t = threadFor(k, paintingId);
            if (t != null) break;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (t == null) {
            out.put("thread", null);
            out.put("messages", List.of());
            return out;
        }
        long id = ((Number) t.get("id")).longValue();
        jdbc.update("update artist_threads set buyer_unread = 0 where id = ?", id);
        out.put("thread", Map.of("id", id, "status", t.get("status"), "name", t.get("buyer_name"), "email", t.get("buyer_email")));
        out.put("messages", messages(id));
        return out;
    }

    public List<Map<String, Object>> messages(long threadId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : jdbc.queryForList("select m.id, m.sender, m.body, m.created_at, a.name as admin_name from artist_thread_messages m"
                + " left join admins a on a.id = m.admin_id where m.thread_id = ? order by m.id", threadId)) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", m.get("id"));
            x.put("sender", m.get("sender"));
            x.put("body", m.get("body"));
            x.put("by", "ARTIST".equals(m.get("sender")) ? (m.get("admin_name") == null ? "The artist" : m.get("admin_name")) : null);
            x.put("at", ShopOrderService.millis(m.get("created_at")));
            out.add(x);
        }
        return out;
    }

    /**
     * A collector's message: opens a thread for this artwork (or continues it) and e-mails the artist side with the
     * artwork details. {@code context} is what they were looking at, e.g. "Limited edition canvas · 24 × 18 in · ₹5,999".
     */
    public long post(String ownerKey, Long userId, Map<String, Object> painting, String name, String email, String body, String context, String baseUrl) {
        String text = body == null ? "" : body.trim();
        if (text.length() < 2) throw new MarketplaceException(422, "EMPTY", "Write your message first.");
        if (text.length() > MAX_BODY) throw new MarketplaceException(422, "TOO_LONG", "Keep your message under " + MAX_BODY + " characters.");
        long paintingId = ((Number) painting.get("id")).longValue();
        Map<String, Object> t = threadFor(ownerKey, paintingId);
        Timestamp now = now();
        long threadId;
        boolean first = t == null;
        if (t == null) {
            if (name == null || name.trim().length() < 2) throw new MarketplaceException(422, "NAME", "Tell the artist your name.");
            if (email == null || !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) throw new MarketplaceException(422, "EMAIL", "Enter your e-mail so the artist can reply.");
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            String ctx = context == null ? null : context.length() > 250 ? context.substring(0, 250) : context;
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("insert into artist_threads (painting_id, user_id, owner_key, buyer_name, buyer_email, context, status,"
                        + " staff_unread, buyer_unread, last_message_at, created_at) values (?, ?, ?, ?, ?, ?, 'OPEN', 0, 0, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, paintingId);
                ps.setObject(2, userId);
                ps.setString(3, ownerKey);
                ps.setString(4, trim(name.trim(), 255));
                ps.setString(5, trim(email.trim(), 255));
                ps.setString(6, ctx);
                ps.setTimestamp(7, now);
                ps.setTimestamp(8, now);
                return ps;
            }, keys);
            threadId = keys.getKeys() != null && keys.getKeys().get("id") != null ? ((Number) keys.getKeys().get("id")).longValue() : keys.getKey().longValue();
            t = jdbc.queryForList("select * from artist_threads where id = ?", threadId).get(0);
        } else {
            threadId = ((Number) t.get("id")).longValue();
            if (context != null && !context.isBlank()) jdbc.update("update artist_threads set context = ? where id = ?", trim(context, 250), threadId);
        }
        jdbc.update("insert into artist_thread_messages (thread_id, sender, admin_id, body, created_at) values (?, 'BUYER', null, ?, ?)", threadId, text, now);
        jdbc.update("update artist_threads set staff_unread = staff_unread + 1, status = 'OPEN', last_message_at = ? where id = ?", now, threadId);
        notifyArtist(t, painting, text, context, first, baseUrl);
        return threadId;
    }

    private void notifyArtist(Map<String, Object> t, Map<String, Object> p, String text, String context, boolean first, String baseUrl) {
        try {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("first", first);
            model.put("buyerName", t.get("buyer_name"));
            model.put("buyerEmail", t.get("buyer_email"));
            model.put("message", text);
            model.put("context", context == null || context.isBlank() ? t.get("context") : context);
            model.put("title", p.get("title"));
            model.put("artist", p.get("artist_name"));
            model.put("medium", p.get("medium"));
            model.put("size", p.get("height_inches") + " × " + p.get("width_inches") + " in");
            model.put("price", p.get("final_price"));
            model.put("status", p.get("stock_status"));
            model.put("artworkUrl", baseUrl + "/marketplace/" + p.get("slug"));
            model.put("replyUrl", baseUrl + "/admin/dashboard/shop?tab=messages&thread=" + t.get("id"));
            String subject = (first ? "New question about “" : "New message about “") + p.get("title") + "” from " + t.get("buyer_name");
            for (String to : artistRecipients(p)) mailer.send(to, subject, "emails/artist-inquiry", model);
        } catch (RuntimeException e) {
            log.warn("Could not e-mail the artist about thread {}", t.get("id"), e);
        }
    }

    /** Who hears about collectors' messages: the listing's own contact e-mail, the instructor who listed it, and the studio. */
    Set<String> artistRecipients(Map<String, Object> p) {
        Set<String> to = new LinkedHashSet<>();
        String own = str(p.get("artist_email"));
        if (own != null) to.add(own);
        if (p.get("artist_admin_id") instanceof Number n) {
            jdbc.queryForList("select email from admins where id = ?", n.longValue()).stream().map(r -> str(r.get("email"))).filter(e -> e != null).forEach(to::add);
        }
        String studio = env.getProperty("app.admin-email", "");
        if (!studio.isBlank()) to.add(studio);
        return to;
    }

    // ================================================================ staff side

    /** Threads for the console; instructors ({@code artistAdminId}) only see their own artworks. */
    public List<Map<String, Object>> threads(Long artistAdminId) {
        String sql = "select t.*, p.title, p.slug, p.artist_name, (select i.image_url from painting_images i where i.painting_id = p.id order by i.is_primary desc, i.sort_order, i.id limit 1) as image_url,"
                + " (select m.body from artist_thread_messages m where m.thread_id = t.id order by m.id desc limit 1) as last_body,"
                + " (select m.sender from artist_thread_messages m where m.thread_id = t.id order by m.id desc limit 1) as last_sender"
                + " from artist_threads t join paintings p on p.id = t.painting_id";
        List<Map<String, Object>> rows = artistAdminId == null
                ? jdbc.queryForList(sql + " order by t.last_message_at desc limit 200")
                : jdbc.queryForList(sql + " where p.artist_admin_id = ? order by t.last_message_at desc limit 200", artistAdminId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String k : List.of("id", "painting_id", "title", "slug", "artist_name", "image_url", "buyer_name", "buyer_email", "context", "status",
                    "staff_unread", "last_body", "last_sender")) {
                m.put(k, r.get(k));
            }
            m.put("guest", String.valueOf(r.get("owner_key")).startsWith("S:"));
            m.put("last_message_at", ShopOrderService.millis(r.get("last_message_at")));
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> thread(long id) {
        List<Map<String, Object>> r = jdbc.queryForList("select t.*, p.title, p.slug, p.artist_admin_id from artist_threads t join paintings p on p.id = t.painting_id where t.id = ?", id);
        return r.isEmpty() ? null : r.get(0);
    }

    public void markReadByStaff(long id) {
        jdbc.update("update artist_threads set staff_unread = 0 where id = ?", id);
    }

    public void reply(long threadId, long adminId, String adminName, String body, String baseUrl) {
        String text = body == null ? "" : body.trim();
        if (text.length() < 2) throw new MarketplaceException(422, "EMPTY", "Write your reply first.");
        if (text.length() > MAX_BODY) throw new MarketplaceException(422, "TOO_LONG", "Keep your reply under " + MAX_BODY + " characters.");
        Map<String, Object> t = thread(threadId);
        if (t == null) throw new MarketplaceException(404, "NOT_FOUND", "Conversation not found.");
        Timestamp now = now();
        jdbc.update("insert into artist_thread_messages (thread_id, sender, admin_id, body, created_at) values (?, 'ARTIST', ?, ?, ?)", threadId, adminId, text, now);
        jdbc.update("update artist_threads set buyer_unread = buyer_unread + 1, staff_unread = 0, last_message_at = ? where id = ?", now, threadId);
        try {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("buyerName", t.get("buyer_name"));
            model.put("title", t.get("title"));
            model.put("from", adminName);
            model.put("message", text);
            model.put("url", baseUrl + "/marketplace/" + t.get("slug") + "?chat=1");
            mailer.send(String.valueOf(t.get("buyer_email")), "Reply from the artist about “" + t.get("title") + "”", "emails/artist-reply", model);
        } catch (RuntimeException e) {
            log.warn("Could not e-mail the reply for thread {}", threadId, e);
        }
    }

    public void setStatus(long threadId, String status) {
        jdbc.update("update artist_threads set status = ? where id = ?", "CLOSED".equals(status) ? "CLOSED" : "OPEN", threadId);
    }

    private static String trim(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
