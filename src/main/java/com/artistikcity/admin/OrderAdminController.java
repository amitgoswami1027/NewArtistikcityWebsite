package com.artistikcity.admin;

import com.artistikcity.http.NotFoundException;
import com.artistikcity.lifecycle.LifecycleRepository;
import com.artistikcity.service.CertificateService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Course orders: tracking table (/admin/orders), order details, manual status changes and certificates.
 * payment_status: 0 = pending, 1 = paid, 2 = failed, 3 = refunded. Students only get course access while it is 1.
 */
@Controller
public class OrderAdminController {

    static final Map<String, Integer> STATUS = Map.of("pending", 0, "paid", 1, "failed", 2, "refunded", 3);
    private static final String AMOUNT = "cast(nullif(o.price, '') as decimal(12,2))";

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final CertificateService certificates;
    private final LifecycleRepository lifecycle;
    private final Audit audit;

    public OrderAdminController(AdminKit kit, Db db, Json json, CertificateService certificates, LifecycleRepository lifecycle, Audit audit) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.certificates = certificates;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    private Grid grid(HttpServletRequest request) {
        return Grid.from(db, request)
                .columns("o.id, o.order_number, o.created_at, o.updated_at, o.price, o.price_type, o.payment_option, o.payment_status, o.issue_certificate,"
                        + " o.billing_name, o.billing_email, o.billing_country, u.id as user_id, u.name as student, u.email as student_email,"
                        + " c.id as course_id, c.title as course, c.course_type_id")
                .from("orders o left join users u on u.id = o.user_id left join courses c on c.id = o.course_id")
                .search("o.order_number", "o.billing_name", "o.billing_email", "u.name", "u.email", "c.title", "o.payment_id", "o.gateway_ref")
                .sort("date", "o.created_at").sort("amount", AMOUNT).sort("student", "u.name").sort("course", "c.title")
                .defaultSort("date", "desc").tieBreak("o.id desc")
                .filter("status", v -> STATUS.containsKey(v) ? Grid.where("o.payment_status = ?", STATUS.get(v)) : null)
                .filter("course", v -> v.matches("\\d+") ? Grid.where("o.course_id = ?", Long.parseLong(v)) : null)
                .filter("currency", v -> "inr".equals(v) || "usd".equals(v) ? Grid.where("o.price_type = ?", v) : null)
                .filter("method", v -> switch (v) {
                    case "razorpay" -> Grid.where("o.payment_option like 'RazorPay%'");
                    case "paypal" -> Grid.where("o.payment_option like 'PayPal%'");
                    case "manual" -> Grid.where("o.payment_option like 'Manual%'");
                    case "free" -> Grid.where("o.payment_option = 'Free'");
                    default -> null;
                })
                .filter("from", v -> date(v) == null ? null : Grid.where("o.created_at >= ?", date(v).atStartOfDay()))
                .filter("to", v -> date(v) == null ? null : Grid.where("o.created_at < ?", date(v).plusDays(1).atStartOfDay()));
    }

    private static LocalDate date(String v) {
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @GetMapping({"/admin/orders", "/admin/orders/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        return kit.page(request, "revenue", "Admin/Orders", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> grid(request).page();
            p.put("grid", grid);
            Supplier<Object> stats = () -> {
                LocalDateTime month = LocalDate.now().withDayOfMonth(1).atStartOfDay();
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("paid", db.count("select count(*) from orders where payment_status = 1"));
                s.put("pending", db.count("select count(*) from orders where payment_status = 0"));
                s.put("failed", db.count("select count(*) from orders where payment_status = 2"));
                s.put("refunded", db.count("select count(*) from orders where payment_status = 3"));
                s.put("all", db.count("select count(*) from orders"));
                s.put("revenueInr", db.scalar("select coalesce(sum(cast(nullif(price, '') as decimal(12,2))), 0) from orders where payment_status = 1 and price_type = 'inr'"));
                s.put("revenueUsd", db.scalar("select coalesce(sum(cast(nullif(price, '') as decimal(12,2))), 0) from orders where payment_status = 1 and price_type = 'usd'"));
                s.put("monthInr", db.scalar("select coalesce(sum(cast(nullif(price, '') as decimal(12,2))), 0) from orders where payment_status = 1 and price_type = 'inr' and created_at >= ?", month));
                s.put("monthOrders", db.count("select count(*) from orders where payment_status = 1 and created_at >= ?", month));
                s.put("certificatesDue", db.count("select count(*) from orders where payment_status = 1 and coalesce(issue_certificate, 0) = 0"));
                return s;
            };
            p.put("stats", stats);
            p.put("courses", db.select("select id as value, title as label from courses order by title"));
            return p;
        });
    }

    @GetMapping("/admin/orders/export")
    public ResponseEntity<String> export(HttpServletRequest request) {
        if (!kit.can(kit.admin(request), "revenue")) {
            return kit.forbidden();
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Row r : grid(request).all(20000)) {
            rows.add(List.of(r.str("order_number", ""), r.str("created_at", ""), r.str("student", r.str("billing_name", "")),
                    r.str("student_email", r.str("billing_email", "")), r.str("course", ""), r.str("price", ""), r.str("price_type", "").toUpperCase(),
                    r.str("payment_option", ""), label(r.lng("payment_status", 0)), r.lng("issue_certificate", 0) == 1 ? "yes" : "no"));
        }
        return kit.csv("orders-" + LocalDate.now() + ".csv",
                List.of("Order", "Date", "Student", "Email", "Course", "Amount", "Currency", "Method", "Status", "Certificate"), rows);
    }

    static String label(long status) {
        return switch ((int) status) {
            case 1 -> "Paid";
            case 2 -> "Failed";
            case 3 -> "Refunded";
            default -> "Pending";
        };
    }

    /** Legacy modal URL. */
    @GetMapping("/admin/order/details/{id}")
    public ResponseEntity<String> legacyDetails(HttpServletRequest request, @PathVariable("id") long id) {
        return show(request, id);
    }

    @GetMapping("/admin/api/orders/{id}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "revenue")) {
            return kit.forbidden();
        }
        return kit.ok(details(id));
    }

    private Map<String, Object> details(long id) {
        Row o = NotFoundException.orFail(db.find("orders", id));
        Map<String, Object> d = new LinkedHashMap<>();
        Row order = new Row(o);
        order.remove("session_id");
        Object details = json.decode(o.str("payment_details"));
        order.put("payment_details", details == null ? o.get("payment_details") : details);
        order.put("status_label", label(o.lng("payment_status", 0)));
        d.put("order", order);
        d.put("student", o.get("user_id") == null ? null
                : db.first("select id, name, email, phone_number, location, created_at from users where id = ?", o.get("user_id")));
        d.put("course", o.get("course_id") == null ? null
                : db.first("select id, title, slug, course_type_id, course_start_date from courses where id = ?", o.get("course_id")));
        d.put("enrollment", o.get("user_id") == null ? null : db.first("select e.status, e.progress_percentage, e.created_at,"
                + " (select count(*) from lesson_completions lc where lc.enrollment_id = e.id) as completed"
                + " from enrollments e where e.user_id = ? and e.course_id = ?", o.get("user_id"), o.get("course_id")));
        d.put("certificate", db.first("select id, certificate_number, certificate_path, created_at from certificates where order_id = ? order by id desc limit 1", id));
        d.put("payments", db.select("select payment_option, payment_id, payment_status, created_at, updated_at from payment_logs where order_id = ? order by id", id));
        d.put("history", audit.history("order", id));
        return d;
    }

    @PostMapping("/admin/api/orders/{id}/status")
    public ResponseEntity<String> status(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) {
            return kit.forbidden();
        }
        Row o = NotFoundException.orFail(db.find("orders", id));
        Input in = Input.of(request, json);
        String to = String.valueOf(in.get("status"));
        String note = AdminKit.trim(in.get("note"), 500);
        if (!STATUS.containsKey(to)) {
            return kit.invalid(Map.of("status", "Choose a status."));
        }
        int next = STATUS.get(to);
        int current = (int) o.lng("payment_status", 0);
        if (next == current) {
            return kit.invalid(Map.of("status", "The order is already " + label(next).toLowerCase() + "."));
        }
        if ((next == 1 || next == 3) && AdminKit.blank(note)) {
            return kit.invalid(Map.of("note", next == 1 ? "Say how it was paid (for example the bank transfer reference)." : "Add the refund reference or reason."));
        }
        Row v = Row.of("payment_status", next);
        if (next == 1) {
            v.put("payment_option", AdminKit.trim("Manual (" + admin.str("name", "admin") + ")", 50));
            v.put("payment_details", json.encode(Map.of("marked_paid_by", admin.str("email", ""), "note", note, "at", LocalDateTime.now().toString())));
        }
        db.updateById("orders", id, v);
        Long userId = o.lng("user_id");
        if (userId != null) {
            if (next == 1) {
                lifecycle.syncEnrollments(userId);
                db.update("update enrollments set status = 'Active', updated_at = ? where user_id = ? and course_id = ? and status = 'Refunded'",
                        LocalDateTime.now(), userId, o.get("course_id"));
            } else if (current == 1 && db.count("select count(*) from orders where user_id = ? and course_id = ? and payment_status = 1", userId, o.get("course_id")) == 0) {
                db.update("update enrollments set status = 'Refunded', updated_at = ? where user_id = ? and course_id = ?",
                        LocalDateTime.now(), userId, o.get("course_id"));
            }
        }
        audit.log(admin, "order", id, "status: " + label(current) + " → " + label(next), note);
        String message = switch (next) {
            case 1 -> "Marked as paid. The student now has access to the course.";
            case 3 -> "Marked as refunded. Course access was removed.";
            case 2 -> "Marked as failed.";
            default -> "Marked as pending.";
        };
        Map<String, Object> out = details(id);
        out.put("message", message);
        return kit.ok(out);
    }

    @PostMapping({"/admin/api/orders/{id}/certificate"})
    public ResponseEntity<String> certificate(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) {
            return kit.forbidden();
        }
        Row o = NotFoundException.orFail(db.find("orders", id));
        if (o.lng("payment_status", 0) != 1) {
            return kit.fail(HttpStatus.CONFLICT, "Certificates are only issued for paid orders.");
        }
        Row course = db.find("courses", o.get("course_id"));
        if (course == null || course.get("admin_id") == null || db.find("admins", course.get("admin_id")) == null) {
            return kit.fail(HttpStatus.CONFLICT, "Set an instructor on the course first; their name signs the certificate.");
        }
        Row cert;
        try {
            cert = certificates.issue(id);
        } catch (RuntimeException e) {
            return kit.fail(HttpStatus.INTERNAL_SERVER_ERROR, "The certificate could not be created. Please try again.");
        }
        if (cert == null) {
            return kit.fail(HttpStatus.INTERNAL_SERVER_ERROR, "The certificate could not be created.");
        }
        audit.log(admin, "order", id, "certificate issued", cert.str("certificate_number"));
        Map<String, Object> out = details(id);
        out.put("message", "Certificate " + cert.str("certificate_number") + " issued.");
        return kit.ok(out);
    }

    /** Legacy GET link from the old screen. */
    @GetMapping("/admin/order/issue-certificate/{id}")
    public ResponseEntity<String> legacyCertificate(HttpServletRequest request, @PathVariable("id") long id) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header("Location", "/admin/orders").build();
    }
}
