package com.artistikcity.ledger;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Grid;
import com.artistikcity.config.Settings;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Admin > Sales > Payouts & ledger ({@code /admin/finance}): finance report, artist balances, weekly payout batches
 * (staff pay by bank transfer and mark the batch paid), disputes and the raw ledger. Capability {@code revenue}.
 */
@Controller
public class FinanceAdminController {

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final LedgerService ledger;
    private final Settings settings;
    private final com.artistikcity.selling.PayoutDetails payoutDetails;
    private final com.artistikcity.admin.Audit audit;

    public FinanceAdminController(AdminKit kit, Db db, Json json, LedgerService ledger, Settings settings,
                                  com.artistikcity.selling.PayoutDetails payoutDetails, com.artistikcity.admin.Audit audit) {
        this.payoutDetails = payoutDetails;
        this.audit = audit;
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.ledger = ledger;
        this.settings = settings;
    }

    private Grid entries(HttpServletRequest request) {
        return Grid.from(db, request).columns("e.*").from("ledger_entries e")
                .search("e.source_ref", "e.payee_name", "e.memo", "e.idem_key")
                .sort("date", "e.created_at").sort("amount", "e.amount").defaultSort("date", "desc").tieBreak("e.id desc")
                .filter("account", v -> List.of("ESCROW", "PLATFORM", "ARTIST").contains(v) ? Grid.where("e.account = ?", v) : null)
                .filter("type", v -> List.of("SALE", "COMMISSION", "ARTIST_SHARE", "REFUND", "PAYOUT", "DISPUTE_LOSS", "ADJUSTMENT").contains(v) ? Grid.where("e.entry_type = ?", v) : null)
                .filter("source", v -> List.of("MARKETPLACE", "SHOP", "COURSE", "PAYOUT").contains(v) ? Grid.where("e.source = ?", v) : null);
    }

    @GetMapping({"/admin/finance", "/admin/finance/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        return kit.page(request, "revenue", "Admin/Finance", () -> {
            try {
                ledger.sync();
            } catch (RuntimeException e) {
                // the page still shows what is there
            }
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> entries(request).page();
            p.put("grid", grid);
            p.put("totals", ledger.totals());
            p.put("monthly", ledger.monthly(12));
            p.put("balances", ledger.balances());
            p.put("batches", db.select("select b.*, a.name as paid_by_name from payout_batches b left join admins a on a.id = b.paid_by order by b.id desc limit 30"));
            p.put("disputes", db.select("select * from ledger_disputes order by case when status = 'OPEN' then 0 else 1 end, id desc limit 100"));
            p.put("rules", Map.of("commissionPercent", settings.commissionPercent(), "payoutAfterDays", settings.payoutAfterDays(), "payStudioArtists", settings.payStudioArtists()));
            return p;
        });
    }

    @GetMapping("/admin/api/finance/batches/{id}")
    public ResponseEntity<String> batch(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "revenue")) return kit.forbidden();
        Row b = db.first("select * from payout_batches where id = ?", id);
        if (b == null) return kit.fail(HttpStatus.NOT_FOUND, "No such batch.");
        return kit.ok(Map.of("batch", b, "lines", db.select("select * from payout_lines where batch_id = ? order by amount desc", id)));
    }

    /**
     * Where to pay a student artist: the full payout details they saved in My studio > Sell your art. Every look is
     * in the security log (who, whose details; never the details themselves).
     */
    @GetMapping("/admin/api/finance/payees/{userId}/payout-details")
    public ResponseEntity<String> payoutDetails(HttpServletRequest request, @PathVariable("userId") long userId) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) return kit.forbidden();
        Row u = db.first("select id, name, email, phone_number from users where id = ?", userId);
        if (u == null) return kit.fail(HttpStatus.NOT_FOUND, "No such student.");
        Map<String, Object> d = payoutDetails.full(userId);
        audit.log(admin, "payout_details", userId, "viewed", "Payout details of " + u.str("name", "#" + userId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("student", Map.of("id", userId, "name", u.str("name", ""), "email", u.str("email", ""), "phone", u.str("phone_number", "")));
        out.put("details", d);
        return kit.ok(out);
    }

    @PostMapping("/admin/api/finance/batches")
    public ResponseEntity<String> createBatch(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) return kit.forbidden();
        ledger.sync();
        Map<String, Object> b = ledger.createBatch(admin);
        if (b == null) return kit.invalid(Map.of("batch", "Nothing to pay right now, or a draft batch is already open."));
        return kit.ok(Map.of("message", "Draft batch #" + b.get("id") + ": " + b.get("line_count") + " artist(s), ₹" + b.get("total") + ".", "batch", b));
    }

    @PostMapping("/admin/api/finance/batches/{id}/paid")
    public ResponseEntity<String> paid(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) return kit.forbidden();
        Input in = Input.of(request, json);
        String ref = AdminKit.trim(in.get("reference"), 200);
        if (AdminKit.blank(ref)) return kit.invalid(Map.of("reference", "Add the bank transfer reference (or batch reference)."));
        Map<String, Object> r = ledger.markPaid(id, admin, ref);
        if (r == null) return kit.invalid(Map.of("reference", "This batch is not a draft any more."));
        return kit.ok(Map.of("message", "Batch marked paid: " + r.get("lines") + " artist(s), ₹" + r.get("total") + "."));
    }

    @PostMapping("/admin/api/finance/batches/{id}/void")
    public ResponseEntity<String> voidBatch(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) return kit.forbidden();
        return ledger.voidBatch(id, admin) ? kit.ok(Map.of("message", "Draft voided.")) : kit.invalid(Map.of("batch", "Only a draft can be voided."));
    }

    @PostMapping("/admin/api/finance/disputes")
    public ResponseEntity<String> openDispute(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) return kit.forbidden();
        Input in = Input.of(request, json);
        String source = String.valueOf(in.get("source"));
        String ref = AdminKit.trim(in.get("ref"), 60);
        if (!List.of("MARKETPLACE", "SHOP", "COURSE").contains(source) || AdminKit.blank(ref)) return kit.invalid(Map.of("ref", "Choose the order kind and enter its order number."));
        boolean exists = switch (source) {
            case "MARKETPLACE" -> db.count("select count(*) from marketplace_orders where order_id = ?", ref) > 0;
            case "SHOP" -> db.count("select count(*) from shop_orders where order_id = ?", ref) > 0;
            default -> ref.matches("\\d+") && db.count("select count(*) from orders where id = ?", Long.parseLong(ref)) > 0;
        };
        if (!exists) return kit.invalid(Map.of("ref", "No such order."));
        Long id = ledger.openDispute(source, ref, null, null, AdminKit.trim(in.get("reason"), 300), BigDecimal.ZERO, admin);
        return kit.ok(Map.of("message", "Dispute #" + id + " opened. The artist's share of that order is on hold."));
    }

    @PostMapping("/admin/api/finance/disputes/{id}/close")
    public ResponseEntity<String> closeDispute(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "revenue")) return kit.forbidden();
        Input in = Input.of(request, json);
        String outcome = String.valueOf(in.get("outcome"));
        if (!ledger.closeDispute(id, outcome, admin, AdminKit.trim(in.get("note"), 500))) return kit.invalid(Map.of("outcome", "Choose won or lost (open disputes only)."));
        return kit.ok(Map.of("message", "WON".equals(outcome) ? "Dispute won: payouts resume." : "Dispute lost: the sale was reversed in the ledger."));
    }

    @GetMapping("/admin/finance/export")
    public ResponseEntity<String> export(HttpServletRequest request) {
        if (!kit.can(kit.admin(request), "revenue")) return kit.forbidden();
        List<List<Object>> rows = new ArrayList<>();
        for (Row r : entries(request).all(100000)) {
            rows.add(List.of(r.str("created_at", ""), r.str("entry_type", ""), r.str("account", ""), r.str("source", ""), r.str("source_ref", ""),
                    r.str("payee_type", ""), r.str("payee_id", ""), r.str("payee_name", ""), r.str("amount", ""), r.str("available_at", ""), r.str("memo", ""), r.str("idem_key", "")));
        }
        return kit.csv("ledger-" + LocalDate.now() + ".csv", List.of("Time", "Type", "Account", "Source", "Order", "Payee type", "Payee id", "Payee", "Amount (INR)",
                "Payable from", "Memo", "Key"), rows);
    }
}
