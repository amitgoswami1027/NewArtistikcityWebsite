package com.artistikcity.rooms;

import com.artistikcity.admin.SiteSettings;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The room photos of the "See it on your wall" preview ({@code /purchase/step-2}), managed in Admin > Room preview.
 * Each photo has a marked hanging area (percent of the photo) and, optionally, how wide that area is in real life, so
 * the artwork is drawn to scale. The page shows the artwork on its own first and the active rooms after it, however
 * many there are (none, one, or a dozen).
 */
@Service
public class RoomScenes {

    public static final int MAX_ACTIVE = 12;
    private static final String SETTINGS_TAG = "room_preview";

    private final Db db;
    private final SiteSettings settings;

    public RoomScenes(Db db, SiteSettings settings) {
        this.db = db;
        this.settings = settings;
    }

    // ------------------------------------------------------------------ reading

    /** Active rooms in their order, as the purchase page uses them. */
    public List<Map<String, Object>> active() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row r : db.select("select * from room_scenes where status = 'ACTIVE' order by sort_order, id limit " + MAX_ACTIVE)) out.add(view(r));
        return out;
    }

    /** Every room, active first (admin). */
    public List<Map<String, Object>> all() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row r : db.select("select * from room_scenes order by case when status = 'ACTIVE' then 0 else 1 end, sort_order, id")) out.add(view(r));
        return out;
    }

    public Row find(long id) {
        return db.first("select * from room_scenes where id = ?", id);
    }

    /** Is the room step on? Off = the purchase page shows the artwork and the frames only. */
    public boolean enabled() {
        return !"0".equals(settings.get(SETTINGS_TAG, "enabled", "1"));
    }

    public static Map<String, Object> view(Row r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("label", r.get("label"));
        m.put("note", r.get("note"));
        m.put("photo", r.get("photo"));
        m.put("width", r.get("photo_width"));
        m.put("height", r.get("photo_height"));
        m.put("area", Map.of("x", num(r.get("area_x")), "y", num(r.get("area_y")), "w", num(r.get("area_w")), "h", num(r.get("area_h"))));
        m.put("areaWidthIn", r.get("area_width_in") == null ? null : num(r.get("area_width_in")));
        m.put("sort", r.get("sort_order"));
        m.put("status", r.get("status"));
        return m;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : o == null ? 0 : Double.parseDouble(String.valueOf(o));
    }

    // ------------------------------------------------------------------ writing

    public record Fields(String label, String note, double x, double y, double w, double h, BigDecimal widthIn, boolean active) {
    }

    /** Checks the fields; returns field errors (empty = fine). */
    public static Map<String, String> validate(Fields f) {
        Map<String, String> e = new LinkedHashMap<>();
        if (f.label() == null || f.label().isBlank()) e.put("label", "Give the room a name, e.g. Living room.");
        else if (f.label().length() > 120) e.put("label", "Keep the name under 120 characters.");
        if (f.note() != null && f.note().length() > 255) e.put("note", "Keep the note under 255 characters.");
        if (f.w() < 5 || f.h() < 5) e.put("area", "Draw the hanging area on the photo (at least a small rectangle).");
        if (f.x() < 0 || f.y() < 0 || f.x() + f.w() > 100.01 || f.y() + f.h() > 100.01) e.put("area", "The hanging area must be inside the photo.");
        if (f.widthIn() != null && (f.widthIn().signum() <= 0 || f.widthIn().compareTo(BigDecimal.valueOf(1200)) > 0)) {
            e.put("widthIn", "Enter the real width of the marked area in inches (1 to 1200), or leave it empty.");
        }
        return e;
    }

    public long create(Fields f, String photo, int width, int height) {
        LocalDateTime now = LocalDateTime.now();
        long next = db.count("select coalesce(max(sort_order), 0) + 1 from room_scenes");
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("label", f.label().trim());
        v.put("note", blankToNull(f.note()));
        v.put("photo", photo);
        v.put("photo_width", width);
        v.put("photo_height", height);
        v.put("area_x", round(f.x()));
        v.put("area_y", round(f.y()));
        v.put("area_w", round(f.w()));
        v.put("area_h", round(f.h()));
        v.put("area_width_in", f.widthIn());
        v.put("sort_order", next);
        v.put("status", f.active() ? "ACTIVE" : "HIDDEN");
        v.put("created_at", now);
        v.put("updated_at", now);
        return db.insertReturningId("room_scenes", v);
    }

    public void update(long id, Fields f, String photo, Integer width, Integer height) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("label", f.label().trim());
        v.put("note", blankToNull(f.note()));
        v.put("area_x", round(f.x()));
        v.put("area_y", round(f.y()));
        v.put("area_w", round(f.w()));
        v.put("area_h", round(f.h()));
        v.put("area_width_in", f.widthIn());
        v.put("status", f.active() ? "ACTIVE" : "HIDDEN");
        if (photo != null) {
            v.put("photo", photo);
            v.put("photo_width", width);
            v.put("photo_height", height);
        }
        v.put("updated_at", LocalDateTime.now());
        db.updateById("room_scenes", id, v);
    }

    public void delete(long id) {
        db.deleteById("room_scenes", id);
    }

    /** New order: the ids as listed become 1, 2, 3 ... */
    public void reorder(List<Long> ids) {
        int i = 1;
        for (Long id : ids) db.update("update room_scenes set sort_order = ?, updated_at = ? where id = ?", i++, LocalDateTime.now(), id);
    }

    public void setEnabled(boolean on) {
        LocalDateTime now = LocalDateTime.now();
        int n = db.update("update settings set meta_value = ?, updated_at = ? where meta_tag = ? and meta_key = 'enabled'", on ? "1" : "0", now, SETTINGS_TAG);
        if (n == 0) {
            db.update("insert into settings (meta_tag, meta_key, meta_value, created_at, updated_at) values (?, 'enabled', ?, ?, ?)", SETTINGS_TAG, on ? "1" : "0", now, now);
        }
        settings.invalidate();
    }

    public long activeCount() {
        return db.count("select count(*) from room_scenes where status = 'ACTIVE'");
    }

    private static BigDecimal round(double d) {
        return BigDecimal.valueOf(d).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ------------------------------------------------------------------ photo size

    /** {width, height} of a JPG / PNG / WebP, or null when it can't be read. */
    public static int[] dimensions(byte[] b) {
        try {
            var img = ImageIO.read(new ByteArrayInputStream(b));
            if (img != null) return new int[]{img.getWidth(), img.getHeight()};
        } catch (IOException | RuntimeException e) {
            // fall through to the WebP header
        }
        if (b.length > 30 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            String chunk = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
            switch (chunk) {
                case "VP8X" -> {
                    int w = 1 + ((b[24] & 0xFF) | (b[25] & 0xFF) << 8 | (b[26] & 0xFF) << 16);
                    int h = 1 + ((b[27] & 0xFF) | (b[28] & 0xFF) << 8 | (b[29] & 0xFF) << 16);
                    return new int[]{w, h};
                }
                case "VP8 " -> {
                    int w = ((b[26] & 0xFF) | (b[27] & 0xFF) << 8) & 0x3FFF;
                    int h = ((b[28] & 0xFF) | (b[29] & 0xFF) << 8) & 0x3FFF;
                    return new int[]{w, h};
                }
                case "VP8L" -> {
                    int bits = (b[21] & 0xFF) | (b[22] & 0xFF) << 8 | (b[23] & 0xFF) << 16 | (b[24] & 0xFF) << 24;
                    return new int[]{(bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1};
                }
                default -> {
                    return null;
                }
            }
        }
        return null;
    }
}
