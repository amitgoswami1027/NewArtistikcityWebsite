package com.artistikcity.web.user;

import com.artistikcity.marketplace.MarketplaceRepository;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wish list (saved courses / workshops and marketplace originals) and the counts behind the
 * "My studio" menu in the site header.
 */
@Service
public class WishlistService {

    public static final String COURSE = "course";
    public static final String PAINTING = "painting";

    private final Db db;

    public WishlistService(Db db) {
        this.db = db;
    }

    /** True when the item exists and may be saved (published course, publicly listed original). */
    public boolean exists(String type, long id) {
        if (COURSE.equals(type)) {
            return db.count("select count(*) from courses where id = ? and status = 1", id) > 0;
        }
        if (PAINTING.equals(type)) {
            return db.count("select count(*) from paintings where id = ? and stock_status in ('AVAILABLE', 'RESERVED', 'SOLD')", id) > 0;
        }
        return false;
    }

    public boolean isSaved(Long userId, String type, long id) {
        return userId != null && db.count("select count(*) from wishlist_items where user_id = ? and item_type = ? and item_id = ?",
                userId, type, id) > 0;
    }

    /** Saves or removes the item; returns whether it is saved afterwards. */
    public boolean set(long userId, String type, long id, boolean saved) {
        if (saved) {
            // insert ignore: a double click never creates two rows (unique key)
            db.update("insert ignore into wishlist_items (user_id, item_type, item_id, created_at) values (?, ?, ?, ?)",
                    userId, type, id, LocalDateTime.now());
        } else {
            db.update("delete from wishlist_items where user_id = ? and item_type = ? and item_id = ?", userId, type, id);
        }
        return saved;
    }

    public int count(long userId) {
        // only items that still exist and are visible
        return (int) db.count("select count(*) from wishlist_items w where w.user_id = ? and ("
                + " (w.item_type = 'course' and exists (select 1 from courses c where c.id = w.item_id and c.status = 1))"
                + " or (w.item_type = 'painting' and exists (select 1 from paintings p where p.id = w.item_id"
                + " and p.stock_status in ('AVAILABLE', 'RESERVED', 'SOLD'))))", userId);
    }

    /** Orders the student placed: paid course orders, original artworks and commissions. */
    public int orderCount(long userId) {
        return (int) (db.count("select count(*) from orders where user_id = ? and payment_status = 1", userId)
                + db.count("select count(*) from marketplace_orders where user_id = ? and status not in ('PAYMENT_PENDING', 'EXPIRED')", userId)
                + db.count("select count(*) from commission_orders where user_id = ? and order_status <> 'PAYMENT_PENDING'", userId));
    }

    /** Header menu data for the signed-in student (shared Inertia prop {@code nav}). */
    public Map<String, Object> nav(long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orders", orderCount(userId));
        m.put("wishlist", count(userId));
        return m;
    }

    /** Wish list page rows, newest first. */
    public List<Map<String, Object>> items(long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Row> courses = db.select("select w.created_at as saved_at, c.id, c.title, c.sub_title, c.slug, c.course_type_id, c.age_group,"
                + " c.course_start_date, " + CourseService.photoSubquery("c.id") + ","
                + " (select cp.price_inr from course_prices cp where cp.course_id = c.id order by cp.id limit 1) as price_inr,"
                + " (select cp.price_usd from course_prices cp where cp.course_id = c.id order by cp.id limit 1) as price_usd,"
                + " (select m.name from mediums m where m.id = c.medium_id) as medium"
                + " from wishlist_items w join courses c on c.id = w.item_id"
                + " where w.user_id = ? and w.item_type = 'course' and c.status = 1", userId);
        for (Row c : courses) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", COURSE);
            m.put("id", c.get("id"));
            m.put("title", c.get("title"));
            m.put("subtitle", c.get("sub_title"));
            m.put("kind", c.lng("course_type_id", 0) == 2 ? "Workshop" : "Live course");
            m.put("medium", c.get("medium"));
            m.put("detail", c.get("age_group"));
            m.put("start_date", c.get("course_start_date"));
            m.put("image", c.get("photo") == null ? null : "/storage/uploads/courses/" + c.get("id") + "/" + c.get("photo"));
            m.put("price_inr", c.get("price_inr"));
            m.put("price_usd", c.get("price_usd"));
            m.put("url", "/course/" + c.get("slug"));
            m.put("status", "AVAILABLE");
            m.put("saved_at", c.get("saved_at"));
            out.add(m);
        }
        List<Row> paintings = db.select("select w.created_at as saved_at, p.id, p.slug, p.title, p.medium, p.artist_name, p.final_price, p.currency,"
                + " p.height_inches, p.width_inches, p.stock_status, " + MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url"
                + " from wishlist_items w join paintings p on p.id = w.item_id"
                + " where w.user_id = ? and w.item_type = 'painting' and p.stock_status in ('AVAILABLE', 'RESERVED', 'SOLD')", userId);
        for (Row p : paintings) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", PAINTING);
            m.put("id", p.get("id"));
            m.put("title", p.get("title"));
            m.put("subtitle", p.get("artist_name") == null ? null : "by " + p.get("artist_name"));
            m.put("kind", "Original artwork");
            m.put("medium", p.get("medium"));
            m.put("detail", p.get("height_inches") == null ? null : p.get("height_inches") + " × " + p.get("width_inches") + " in");
            m.put("image", p.get("image_url"));
            m.put("price", p.get("final_price"));
            m.put("currency", p.get("currency"));
            m.put("url", "/marketplace/" + p.get("slug"));
            m.put("status", p.get("stock_status"));
            m.put("saved_at", p.get("saved_at"));
            out.add(m);
        }
        out.sort((a, b) -> String.valueOf(b.get("saved_at")).compareTo(String.valueOf(a.get("saved_at"))));
        return out;
    }
}
