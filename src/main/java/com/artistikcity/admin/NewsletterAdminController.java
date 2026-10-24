package com.artistikcity.admin;

import com.artistikcity.http.NotFoundException;
import com.artistikcity.support.Db;
import com.artistikcity.support.HtmlCleaner;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Newsletter: subscribers (search, add in bulk, unsubscribe, delete, export) and campaigns
 * (draft with preview, test send, send to everyone). Page: /admin/newsletter.
 */
@Controller
public class NewsletterAdminController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@,;<>]+@[^\\s@,;<>]+\\.[^\\s@,;<>]+$");

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final NewsletterService newsletter;
    private final Audit audit;

    public NewsletterAdminController(AdminKit kit, Db db, Json json, NewsletterService newsletter, Audit audit) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.newsletter = newsletter;
        this.audit = audit;
    }

    private Grid subscribers(HttpServletRequest request) {
        return Grid.from(db, request)
                .columns("n.id, n.email, n.source, n.unsubscribed_at, n.created_at, (select u.name from users u where lower(u.email) = lower(n.email) limit 1) as student")
                .from("newsletters n")
                .search("n.email")
                .sort("email", "n.email").sort("date", "n.created_at").defaultSort("date", "desc").tieBreak("n.id desc")
                .filter("state", v -> "active".equals(v) ? Grid.where("n.unsubscribed_at is null")
                        : "unsubscribed".equals(v) ? Grid.where("n.unsubscribed_at is not null") : null);
    }

    @GetMapping({"/admin/newsletter", "/admin/newsletter/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        return kit.page(request, "settings", "Admin/Newsletter", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> subscribers(request).page();
            p.put("grid", grid);
            Supplier<Object> campaigns = () -> db.select("select c.id, c.subject, c.preheader, c.body, c.status, c.recipients, c.sent_count, c.failed_count,"
                    + " c.sent_at, c.created_at, c.updated_at, a.name as author from newsletter_campaigns c left join admins a on a.id = c.created_by order by c.id desc limit 100");
            p.put("campaigns", campaigns);
            Supplier<Object> counts = () -> Map.of(
                    "active", db.count("select count(*) from newsletters where unsubscribed_at is null"),
                    "unsubscribed", db.count("select count(*) from newsletters where unsubscribed_at is not null"),
                    "last30", db.count("select count(*) from newsletters where created_at >= ?", LocalDate.now().minusDays(30).atStartOfDay()));
            p.put("counts", counts);
            p.put("adminEmail", kit.admin(request).str("email", ""));
            return p;
        });
    }

    @GetMapping("/admin/newsletter/export")
    public ResponseEntity<String> export(HttpServletRequest request) {
        if (!kit.can(kit.admin(request), "settings")) {
            return kit.forbidden();
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Row r : subscribers(request).all(100000)) {
            rows.add(List.of(r.str("email"), r.get("unsubscribed_at") == null ? "subscribed" : "unsubscribed", r.str("source", ""), r.str("created_at", "")));
        }
        return kit.csv("subscribers-" + LocalDate.now() + ".csv", List.of("Email", "Status", "Source", "Subscribed"), rows);
    }

    // ------------------------------------------------------------ subscribers

    @PostMapping("/admin/api/newsletter/subscribers")
    public ResponseEntity<String> add(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        String raw = Input.of(request, json).get("emails");
        Set<String> emails = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String part : (raw == null ? "" : raw).split("[\\s,;]+")) {
            String e = part.trim().toLowerCase();
            if (e.isEmpty()) {
                continue;
            }
            if (e.length() > 255 || !EMAIL.matcher(e).matches()) {
                invalid.add(part.trim());
            } else {
                emails.add(e);
            }
        }
        if (emails.isEmpty()) {
            return kit.invalid(Map.of("emails", invalid.isEmpty() ? "Enter at least one e-mail address." : "These don't look like e-mail addresses: " + String.join(", ", invalid)));
        }
        if (emails.size() > 5000) {
            return kit.invalid(Map.of("emails", "Add up to 5,000 addresses at a time."));
        }
        int added = 0;
        int existing = 0;
        for (String e : emails) {
            Row row = db.first("select id, unsubscribed_at from newsletters where lower(email) = ?", e);
            if (row == null) {
                db.insert("newsletters", Row.of("email", e, "source", "admin"));
                added++;
            } else {
                existing++;
            }
        }
        audit.log(admin, "newsletter", 0, "subscribers added", added + " added");
        String msg = added + (added == 1 ? " subscriber" : " subscribers") + " added"
                + (existing > 0 ? ", " + existing + " already on the list" : "")
                + (invalid.isEmpty() ? "." : ". Skipped invalid: " + String.join(", ", invalid));
        return kit.ok(Map.of("message", msg, "added", added, "existing", existing, "invalid", invalid));
    }

    @PostMapping("/admin/api/newsletter/subscribers/{id}/status")
    public ResponseEntity<String> setStatus(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "settings")) {
            return kit.forbidden();
        }
        NotFoundException.orFail(db.find("newsletters", id));
        boolean subscribed = Input.of(request, json).bool("subscribed");
        db.updateById("newsletters", id, Row.of("unsubscribed_at", subscribed ? null : LocalDateTime.now()));
        return kit.ok(Map.of("message", subscribed ? "Subscribed again." : "Unsubscribed."));
    }

    @PostMapping("/admin/api/newsletter/subscribers/delete")
    public ResponseEntity<String> delete(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Object ids = Input.of(request, json).raw("ids");
        int n = 0;
        if (ids instanceof List<?> list) {
            for (Object o : list) {
                if (String.valueOf(o).matches("\\d+(\\.0)?")) {
                    n += db.update("delete from newsletters where id = ?", (long) Double.parseDouble(String.valueOf(o)));
                }
            }
        }
        audit.log(admin, "newsletter", 0, "subscribers deleted", n + " deleted");
        return kit.ok(Map.of("message", n + (n == 1 ? " subscriber" : " subscribers") + " deleted."));
    }

    // -------------------------------------------------------------- campaigns

    private Row campaign(long id) {
        return NotFoundException.orFail(db.find("newsletter_campaigns", id));
    }

    @PostMapping("/admin/api/newsletter/campaigns")
    public ResponseEntity<String> saveCampaign(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Input in = Input.of(request, json);
        Map<String, String> errors = new LinkedHashMap<>();
        String subject = AdminKit.trim(in.get("subject"), 255);
        if (AdminKit.blank(subject)) {
            errors.put("subject", "Write a subject line.");
        }
        String body = HtmlCleaner.clean(in.get("body"));
        if (HtmlCleaner.text(body, 10).isBlank() && (body == null || !body.contains("<img"))) {
            errors.put("body", "Write the message.");
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        Row v = Row.of("subject", subject, "preheader", AdminKit.trim(in.get("preheader"), 255), "body", body);
        Long id = in.lng("id");
        if (id == null) {
            v.put("status", "draft");
            v.put("created_by", admin.get("id"));
            id = db.insert("newsletter_campaigns", v);
        } else {
            Row c = campaign(id);
            if (!"draft".equals(c.str("status")) && !"failed".equals(c.str("status"))) {
                return kit.fail(HttpStatus.CONFLICT, "This newsletter was already sent and can't be changed. Duplicate it to send a new version.");
            }
            db.updateById("newsletter_campaigns", id, v);
        }
        return kit.ok(Map.of("campaign", db.find("newsletter_campaigns", id), "message", "Draft saved."));
    }

    @DeleteMapping("/admin/api/newsletter/campaigns/{id}")
    public ResponseEntity<String> deleteCampaign(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "settings")) {
            return kit.forbidden();
        }
        Row c = campaign(id);
        if ("sending".equals(c.str("status"))) {
            return kit.fail(HttpStatus.CONFLICT, "It is being sent right now.");
        }
        db.deleteById("newsletter_campaigns", id);
        return kit.ok(Map.of("message", "Deleted."));
    }

    @GetMapping("/admin/api/newsletter/campaigns/{id}")
    public ResponseEntity<String> showCampaign(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "settings")) {
            return kit.forbidden();
        }
        return kit.ok(Map.of("campaign", campaign(id)));
    }

    /** The e-mail as subscribers will see it (shown in an iframe in the composer). */
    @PostMapping("/admin/api/newsletter/preview")
    public ResponseEntity<String> preview(HttpServletRequest request) {
        if (!kit.can(kit.admin(request), "settings")) {
            return kit.forbidden();
        }
        Input in = Input.of(request, json);
        Row c = Row.of("subject", in.get("subject"), "preheader", in.get("preheader"), "body", HtmlCleaner.clean(in.get("body")));
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).header("Content-Security-Policy", "script-src 'none'")
                .body(newsletter.render(c, "subscriber@example.com"));
    }

    @PostMapping("/admin/api/newsletter/campaigns/{id}/test")
    public ResponseEntity<String> test(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Row c = campaign(id);
        String to = Input.of(request, json).get("email");
        to = AdminKit.blank(to) ? admin.str("email") : to.trim().toLowerCase();
        if (to == null || !EMAIL.matcher(to).matches()) {
            return kit.invalid(Map.of("email", "Enter the e-mail address for the test."));
        }
        Row test = new Row(c);
        test.put("subject", "[Test] " + c.str("subject"));
        boolean ok = newsletter.sendOne(test, to);
        return ok ? kit.ok(Map.of("message", "Test sent to " + to + "."))
                : kit.fail(HttpStatus.BAD_GATEWAY, "The mail server refused the test message. Check the mail settings.");
    }

    @PostMapping("/admin/api/newsletter/campaigns/{id}/send")
    public ResponseEntity<String> send(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        campaign(id);
        if (db.count("select count(*) from newsletters where unsubscribed_at is null") == 0) {
            return kit.fail(HttpStatus.CONFLICT, "There are no subscribers to send to yet.");
        }
        int n = newsletter.start(id);
        if (n < 0) {
            return kit.fail(HttpStatus.CONFLICT, "This newsletter was already sent.");
        }
        audit.log(admin, "newsletter_campaign", id, "sending", n + " recipients");
        return kit.ok(Map.of("campaign", db.find("newsletter_campaigns", id), "message", "Sending to " + n + (n == 1 ? " subscriber" : " subscribers") + "…"));
    }

    @PostMapping("/admin/api/newsletter/campaigns/{id}/duplicate")
    public ResponseEntity<String> duplicate(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Row c = campaign(id);
        long copy = db.insert("newsletter_campaigns", Row.of("subject", AdminKit.trim(c.str("subject"), 245) + " (copy)", "preheader", c.get("preheader"),
                "body", c.get("body"), "status", "draft", "created_by", admin.get("id")));
        return kit.ok(Map.of("campaign", db.find("newsletter_campaigns", copy), "message", "Copied as a new draft."));
    }
}
