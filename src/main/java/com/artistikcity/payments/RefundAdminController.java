package com.artistikcity.payments;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Grid;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Admin > Sales > Refunds ({@code /admin/payments/refunds}): the refunds queue (staff confirm each refund, which calls
 * the gateway, or dismiss it with a reason) and the reconciliation log, with "Check gateways now". Capability
 * {@code revenue} (super admins).
 */
@Controller
public class RefundAdminController {

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final RefundQueue refunds;
    private final PaymentReconciler reconciler;

    public RefundAdminController(AdminKit kit, Db db, Json json, RefundQueue refunds, PaymentReconciler reconciler) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.refunds = refunds;
        this.reconciler = reconciler;
    }

    private Grid grid(HttpServletRequest request) {
        return Grid.from(db, request)
                .columns("r.*, a.name as decided_by_name")
                .from("payment_refunds r left join admins a on a.id = r.decided_by")
                .search("r.source_ref", "r.payment_ref", "r.buyer_email", "r.detail", "r.gateway_refund_id")
                .sort("date", "r.created_at").sort("amount", "r.amount").defaultSort("date", "desc").tieBreak("r.id desc")
                .filter("status", v -> List.of("PENDING", "PROCESSING", "REFUNDED", "FAILED", "DISMISSED").contains(v) ? Grid.where("r.status = ?", v)
                        : "open".equals(v) ? Grid.where("r.status in ('PENDING', 'FAILED')") : null)
                .filter("source", v -> List.of("COURSE", "MARKETPLACE", "SHOP").contains(v) ? Grid.where("r.source = ?", v) : null);
    }

    @GetMapping({"/admin/payments/refunds", "/admin/payments/refunds/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        return kit.page(request, "revenue", "Admin/Refunds", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> grid(request).page();
            p.put("grid", grid);
            Supplier<Object> stats = () -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("open", db.count("select count(*) from payment_refunds where status in ('PENDING', 'FAILED')"));
                s.put("failed", db.count("select count(*) from payment_refunds where status = 'FAILED'"));
                s.put("openInr", db.scalar("select coalesce(sum(amount), 0) from payment_refunds where status in ('PENDING', 'FAILED') and currency = 'INR'"));
                s.put("openUsd", db.scalar("select coalesce(sum(amount), 0) from payment_refunds where status in ('PENDING', 'FAILED') and currency = 'USD'"));
                s.put("refunded30", db.count("select count(*) from payment_refunds where status = 'REFUNDED' and decided_at >= ?", java.time.LocalDateTime.now().minusDays(30)));
                return s;
            };
            p.put("stats", stats);
            Supplier<Object> recon = () -> db.select("select * from payment_reconciliations order by id desc limit 100");
            p.put("reconciliations", recon);
            return p;
        });
    }

    @PostMapping("/admin/api/payments/refunds/{id}/refund")
    public ResponseEntity<String> refund(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) return kit.forbidden();
        Input in = Input.of(request, json);
        if (!in.bool("confirm")) return kit.invalid(Map.of("confirm", "Tick the box to confirm the money goes back to the buyer."));
        RefundQueue.Decision d = refunds.refund(id, admin, AdminKit.trim(in.get("note"), 500));
        return answer(d, id);
    }

    @PostMapping("/admin/api/payments/refunds/{id}/dismiss")
    public ResponseEntity<String> dismiss(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) return kit.forbidden();
        Input in = Input.of(request, json);
        RefundQueue.Decision d = refunds.dismiss(id, admin, AdminKit.trim(in.get("note"), 500));
        if (!d.ok() && "NOTE".equals(d.status())) return kit.invalid(Map.of("note", d.message()));
        return answer(d, id);
    }

    @PostMapping("/admin/api/payments/reconcile")
    public ResponseEntity<String> reconcile(HttpServletRequest request) {
        if (!kit.can(kit.admin(request), "revenue")) return kit.forbidden();
        return kit.ok(reconciler.run().toMap());
    }

    private ResponseEntity<String> answer(RefundQueue.Decision d, long id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", d.ok());
        body.put("status", d.status());
        body.put("message", d.message());
        body.put("refund", refunds.one(id));
        return d.ok() ? kit.ok(body) : kit.fail(org.springframework.http.HttpStatus.CONFLICT, d.message());
    }
}
