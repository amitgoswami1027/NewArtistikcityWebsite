package com.artistikcity.security;

import com.artistikcity.lifecycle.Personas;
import com.artistikcity.support.Row;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The security audit log ({@code audit_logs}): one append-only, hash-chained stream of everything that matters for
 * security - staff changes (CREATE / UPDATE / DELETE, with before/after snapshots), sign-in events (AUTH), data exports
 * (EXPORT) and refused requests (DENIED).
 *
 * <p>Every row stores the SHA-256 of the row before it ({@code prev_hash}) and of its own fields ({@code row_hash}).
 * Rows are appended one at a time under a row lock on {@code audit_chain_head}, so the chain has no forks. Changing,
 * removing or re-ordering any row afterwards breaks the chain, which {@link #verify()} reports. The application has no
 * code path that updates or deletes these rows.</p>
 *
 * <p>Writing never breaks the request that triggered it: failures are logged and swallowed.</p>
 */
@Component
public class AuditLog {

    public enum Type { CREATE, UPDATE, DELETE, AUTH, EXPORT, DENIED }

    public static final List<String> TYPES = List.of("CREATE", "UPDATE", "DELETE", "AUTH", "EXPORT", "DENIED");
    public static final String GENESIS = "0".repeat(64);

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final int SNAPSHOT_TEXT = 2000;
    private static final List<String> SECRET_COLUMNS = List.of("password", "password_hash", "remember_token", "secret", "token", "token_hash",
            "otp", "otp_hash", "api_key", "credential", "public_key");
    private static final ObjectMapper JSON = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .findAndRegisterModules();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AuditLog(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Who did it. */
    public record Actor(String type, Long id, String email, String role) {
        public static Actor admin(Row admin) {
            if (admin == null) return anonymous();
            return new Actor("admin", admin.lng("id"), admin.str("email", null), Personas.of(admin));
        }

        public static Actor student(Long id, String email) {
            return id == null ? anonymous() : new Actor("student", id, email, "student");
        }

        public static Actor system() {
            return new Actor("system", null, null, "system");
        }

        public static Actor anonymous() {
            return new Actor("anonymous", null, null, null);
        }
    }

    /** One entry; build with {@link #entry(Type, String)} and the chained setters, then {@link #write(Entry)}. */
    public static final class Entry {
        final Type type;
        final String action;
        Actor actor = Actor.anonymous();
        String resourceType;
        String resourceId;
        String summary;
        String before;
        String after;
        String outcome;
        HttpServletRequest request;
        LocalDateTime at;

        Entry(Type type, String action) {
            this.type = type;
            this.action = action;
        }

        public Entry actor(Actor a) { this.actor = a == null ? Actor.anonymous() : a; return this; }
        public Entry resource(String type, Object id) { this.resourceType = type; this.resourceId = id == null ? null : String.valueOf(id); return this; }
        public Entry summary(String s) { this.summary = s; return this; }
        public Entry before(Map<String, ?> m) { this.before = snapshotJson(m); return this; }
        public Entry after(Map<String, ?> m) { this.after = snapshotJson(m); return this; }
        public Entry outcome(String o) { this.outcome = o; return this; }
        public Entry request(HttpServletRequest r) { this.request = r; return this; }
        public Entry at(LocalDateTime t) { this.at = t; return this; }
    }

    public static Entry entry(Type type, String action) {
        return new Entry(type, action);
    }

    /** Appends the entry to the chain. Returns its id, or -1 when it could not be written. */
    public long write(Entry e) {
        try {
            HttpServletRequest r = e.request != null ? e.request : currentRequest();
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("occurred_at", (e.at == null ? LocalDateTime.now() : e.at).truncatedTo(ChronoUnit.MILLIS));
            f.put("actor_type", e.actor.type());
            f.put("actor_id", e.actor.id());
            f.put("actor_email", cut(e.actor.email(), 255));
            f.put("actor_role", cut(e.actor.role(), 30));
            f.put("action_type", e.type.name());
            f.put("action", cut(e.action == null ? e.type.name().toLowerCase() : e.action, 120));
            f.put("resource_type", cut(e.resourceType, 50));
            f.put("resource_id", cut(e.resourceId, 64));
            f.put("summary", cut(e.summary, 1000));
            f.put("before_json", e.before);
            f.put("after_json", e.after);
            f.put("ip", cut(r == null ? null : r.getRemoteAddr(), 45));
            f.put("country", cut(header(r, "CloudFront-Viewer-Country"), 8));
            f.put("city", cut(decode(header(r, "CloudFront-Viewer-City")), 100));
            f.put("user_agent", cut(header(r, "User-Agent"), 500));
            f.put("http_method", r == null ? null : r.getMethod());
            f.put("path", cut(r == null ? null : r.getRequestURI(), 500));
            f.put("outcome", e.outcome != null ? e.outcome : e.type == Type.DENIED ? "DENIED" : "OK");
            Long id = tx.execute(status -> append(f));
            return id == null ? -1 : id;
        } catch (RuntimeException ex) {
            log.warn("Could not write the security audit entry {} {}", e.type, e.action, ex);
            return -1;
        }
    }

    private Long append(Map<String, Object> f) {
        Map<String, Object> head = jdbc.queryForMap("select last_id, last_hash from audit_chain_head where id = 1 for update");
        String prev = String.valueOf(head.get("last_hash"));
        f.put("prev_hash", prev);
        String hash = hash(prev, f);
        f.put("row_hash", hash);
        List<String> cols = new ArrayList<>(f.keySet());
        String sql = "insert into audit_logs (" + String.join(", ", cols) + ") values (" + "?, ".repeat(cols.size() - 1) + "?)";
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < cols.size(); i++) {
                Object v = f.get(cols.get(i));
                ps.setObject(i + 1, v instanceof LocalDateTime t ? Timestamp.valueOf(t) : v);
            }
            return ps;
        }, keys);
        long id = keys.getKey().longValue();
        jdbc.update("update audit_chain_head set last_id = ?, last_hash = ?, updated_at = ? where id = 1",
                id, hash, Timestamp.valueOf(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)));
        return id;
    }

    /** The fields that are hashed, in this order; the id is not (it is checked through the chain order instead). */
    static final List<String> HASHED = List.of("occurred_at", "actor_type", "actor_id", "actor_email", "actor_role", "action_type", "action",
            "resource_type", "resource_id", "summary", "before_json", "after_json", "ip", "country", "city", "user_agent", "http_method", "path", "outcome");

    static String hash(String prev, Map<String, ?> f) {
        StringBuilder sb = new StringBuilder(prev);
        for (String k : HASHED) {
            Object v = f.get(k);
            sb.append('\u001f');
            if (v instanceof LocalDateTime t) sb.append(STAMP.format(t));
            else if (v instanceof Timestamp t) sb.append(STAMP.format(t.toLocalDateTime()));
            else if (v != null) sb.append(v);
        }
        return sha256(sb.toString());
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Result of {@link #verify()}. */
    public record Verification(boolean ok, long checked, Long brokenAt, String problem) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", ok);
            m.put("checked", checked);
            m.put("brokenAt", brokenAt);
            m.put("problem", problem);
            return m;
        }
    }

    /** Re-computes the whole chain from the first row; stops at the first row that does not match. */
    public Verification verify() {
        String prev = GENESIS;
        long lastId = 0;
        long checked = 0;
        String cols = "id, prev_hash, row_hash, " + String.join(", ", HASHED);
        while (true) {
            List<Map<String, Object>> batch = jdbc.queryForList("select " + cols + " from audit_logs where id > ? order by id limit 1000", lastId);
            if (batch.isEmpty()) break;
            for (Map<String, Object> row : batch) {
                long id = ((Number) row.get("id")).longValue();
                Map<String, Object> f = new LinkedHashMap<>(row);
                Object at = f.get("occurred_at");
                if (at instanceof Timestamp t) f.put("occurred_at", t.toLocalDateTime());
                if (!prev.equals(row.get("prev_hash"))) {
                    return new Verification(false, checked, id, "Row " + id + " does not follow the row before it (a row was removed or inserted).");
                }
                if (!hash(prev, f).equals(row.get("row_hash"))) {
                    return new Verification(false, checked, id, "Row " + id + " was changed after it was written.");
                }
                prev = String.valueOf(row.get("row_hash"));
                lastId = id;
                checked++;
            }
        }
        Map<String, Object> head = jdbc.queryForMap("select last_id, last_hash from audit_chain_head where id = 1");
        if (((Number) head.get("last_id")).longValue() != lastId || !prev.equals(head.get("last_hash"))) {
            return new Verification(false, checked, lastId, "The newest rows are missing (the chain ends at row " + lastId
                    + " but the last row written was " + head.get("last_id") + ").");
        }
        return new Verification(true, checked, null, null);
    }

    /** How many DENIED entries this actor collected since the given time (for the revoke rule). */
    public long deniedSince(String actorType, long actorId, LocalDateTime since) {
        Long n = jdbc.queryForObject("select count(*) from audit_logs where action_type = 'DENIED' and actor_type = ? and actor_id = ? and occurred_at >= ?",
                Long.class, actorType, actorId, Timestamp.valueOf(since));
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- helpers

    /** A record as sorted JSON, without secrets and with long texts shortened. Null for null. */
    public static String snapshotJson(Map<String, ?> m) {
        if (m == null) return null;
        Map<String, Object> clean = new TreeMap<>();
        m.forEach((k, v) -> {
            String key = k.toLowerCase();
            if (SECRET_COLUMNS.contains(key) || key.endsWith("_secret") || key.endsWith("_token")) {
                clean.put(k, v == null ? null : "••••••");
            } else if (v instanceof String s && s.length() > SNAPSHOT_TEXT) {
                clean.put(k, s.substring(0, SNAPSHOT_TEXT) + "… (" + s.length() + " characters)");
            } else if (v instanceof LocalDateTime t) {
                clean.put(k, t.withNano(0).toString().replace('T', ' '));
            } else if (v instanceof java.util.Date || v instanceof java.time.temporal.Temporal) {
                clean.put(k, String.valueOf(v));
            } else if (v instanceof byte[]) {
                clean.put(k, "(binary)");
            } else {
                clean.put(k, v);
            }
        });
        try {
            return JSON.writeValueAsString(clean);
        } catch (Exception e) {
            return String.valueOf(clean);
        }
    }

    /** CREATE / UPDATE / DELETE from the free-text action names the admin console has always used. */
    public static Type typeOf(String action) {
        String a = action == null ? "" : action.toLowerCase();
        if (a.contains("delet") || a.contains("remov")) return Type.DELETE;
        if (a.equals("created") || a.contains("added") || a.contains("duplicated") || a.contains("uploaded") || a.startsWith("create")) return Type.CREATE;
        return Type.UPDATE;
    }

    static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
    }

    private static String header(HttpServletRequest r, String name) {
        return r == null ? null : r.getHeader(name);
    }

    private static String decode(String s) {
        if (s == null) return null;
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    private static String cut(String s, int n) {
        return s == null ? null : s.length() > n ? s.substring(0, n) : s;
    }
}
