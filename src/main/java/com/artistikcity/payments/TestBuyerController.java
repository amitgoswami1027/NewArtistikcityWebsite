package com.artistikcity.payments;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.util.Map;

/** Admin > People: mark an account as a test buyer (may simulate payments on the live site). Super admins only. */
@Controller
public class TestBuyerController {

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Audit audit;
    private final TestPayments testPayments;

    public TestBuyerController(AdminKit kit, Db db, Json json, Audit audit, TestPayments testPayments) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.audit = audit;
        this.testPayments = testPayments;
    }

    /** JSON {on: true|false}. */
    @PostMapping("/admin/console/api/students/{id}/test-buyer")
    public ResponseEntity<String> set(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "people.manage")) return kit.forbidden();
        Row user = db.first("select id, name, email from users where id = ?", id);
        if (user == null) return kit.fail(HttpStatus.NOT_FOUND, "Account not found.");
        boolean on = Input.of(request, json).bool("on");
        Row before = audit.snapshot("users", id);
        db.update("update users set test_buyer = ?, updated_at = ? where id = ?", on ? 1 : 0, LocalDateTime.now(), id);
        audit.change(admin, "user", id, on ? "test buyer on" : "test buyer off",
                user.str("name", "") + " <" + user.str("email", "") + ">" + (on ? " may now simulate payments" : " pays normally again"),
                before, audit.snapshot("users", id));
        return kit.ok(Map.of("id", id, "testBuyer", on, "enabled", testPayments.testBuyersEnabled()));
    }
}
