package com.artistikcity.service;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Blog posts with the accessors of the Post model (created_at format, posted_by, categories). */
@Service
public class PostService {

    private static final DateTimeFormatter D_M_Y = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final Db db;

    public PostService(Db db) {
        this.db = db;
    }

    /** getCreatedAtAttribute(): Carbon::parse($value)->format('d M Y'). */
    public static String formatDate(String dateTime) {
        if (dateTime == null || dateTime.length() < 10) {
            return dateTime;
        }
        return LocalDate.parse(dateTime.substring(0, 10)).format(D_M_Y);
    }

    /** Applies the model accessors in place; optionally loads the categories relation. */
    public Row present(Row post, boolean withCategories) {
        post.put("created_at", formatDate(post.str("created_at")));
        if (post.containsKey("posted_by")) {
            // getPostedByAttribute(): a collection with the author
            post.put("posted_by", db.select("select id, name, slug, profile_photo from admins where id = ?", post.get("posted_by")));
        }
        if (withCategories) {
            List<Row> cats = new ArrayList<>();
            for (Row c : db.select("select c.*, cp.category_id as pivot_category_id, cp.post_id as pivot_post_id, cp.created_at as pivot_created_at"
                    + " from categories c join category_post cp on cp.category_id = c.id where cp.post_id = ? order by c.id", post.get("id"))) {
                Row pivot = Row.of("category_id", c.remove("pivot_category_id"), "post_id", c.remove("pivot_post_id"),
                        "created_at", c.remove("pivot_created_at"));
                c.put("post_category", pivot);
                cats.add(c);
            }
            post.put("categories", cats);
        }
        return post;
    }

    public List<Row> present(List<Row> posts, boolean withCategories) {
        for (Row p : posts) {
            present(p, withCategories);
        }
        return posts;
    }
}
