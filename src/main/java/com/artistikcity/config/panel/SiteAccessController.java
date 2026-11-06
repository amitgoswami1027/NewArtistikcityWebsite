package com.artistikcity.config.panel;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
import com.artistikcity.http.SiteLock;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin > System parameters > Site access ({@code /admin/parameters}, also {@code /admin/setup/site-access}): the
 * "Global Site Access Password" and the switch that puts the whole public site behind it (see {@link SiteLock} and
 * {@link com.artistikcity.http.PreviewLockFilter}).
 * <ul>
 *   <li>{@code GET /admin/api/setup/site-access}: {enabled, hasPassword, active, changedAt, changedBy, serverLock, minLength}.
 *       The password and its hash never leave the server.</li>
 *   <li>{@code POST /admin/api/setup/site-access} {@code {enabled: bool, password?: "", confirm?: ""}}: 422 with a message per
 *       field when the password is too short or the two don't match, or when protection is switched on with no password.</li>
 * </ul>
 * Capability {@code setup} (super admins). Saving is in the security log ("password changed", never the value).
 */
@Controller
public class SiteAccessController {

    private final AdminKit kit;
    private final Json json;
    private final Audit audit;
    private final SiteLock lock;
    private final boolean serverLock;

    public SiteAccessController(AdminKit kit, Json json, Audit audit, SiteLock lock, @Value("${app.preview.password:}") String serverPassword) {
        this.kit = kit;
        this.json = json;
        this.audit = audit;
        this.lock = lock;
        this.serverLock = serverPassword != null && !serverPassword.isBlank();
    }

    @GetMapping({"/admin/parameters", "/admin/parameters/", "/admin/setup/site-access"})
    public ResponseEntity<String> page(HttpServletRequest request) {
        return kit.page(request, ConfigSetupController.CAP, "Admin/Setup/SiteAccess", () -> Map.of("access", payload(lock.state())));
    }

    @GetMapping("/admin/api/setup/site-access")
    public ResponseEntity<String> get(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, ConfigSetupController.CAP)) return kit.forbidden();
        return kit.ok(payload(lock.state()));
    }

    @PostMapping("/admin/api/setup/site-access")
    public ResponseEntity<String> save(HttpServletRequest request) throws IOException {
        Row admin = kit.admin(request);
        if (!kit.can(admin, ConfigSetupController.CAP)) return kit.forbidden();
        Map<String, Object> body = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        if (body == null || !(body.get("enabled") instanceof Boolean enabled)) {
            return kit.fail(HttpStatus.BAD_REQUEST, "Send JSON: {\"enabled\": true|false, \"password\": \"...\", \"confirm\": \"...\"}.");
        }
        String password = body.get("password") instanceof String p && !p.isEmpty() ? p : null;
        String confirm = body.get("confirm") instanceof String c ? c : "";
        SiteLock.State before = lock.state();

        Map<String, String> errors = new LinkedHashMap<>();
        if (password != null) {
            String problem = SiteLock.problem(password);
            if (problem != null) errors.put("password", problem);
            else if (!password.equals(confirm)) errors.put("confirm", "The two passwords don't match.");
            else if (lock.matches(password, before)) errors.put("password", "That is already the site password. Enter a new one, or leave both boxes empty.");
        } else if (!confirm.isEmpty()) {
            errors.put("password", "Enter the new password here too.");
        }
        if (enabled && password == null && !before.hasPassword()) {
            errors.put("password", "Set a password before you switch protection on.");
        }
        if (!errors.isEmpty()) return kit.invalid(errors);
        if (enabled == before.enabled() && password == null) {
            return kit.ok(Map.of("message", "Nothing changed.", "access", payload(before)));
        }

        SiteLock.State after = lock.save(enabled, password, admin.str("name", "") + " (" + admin.str("email", "") + ")");
        String what = (enabled != before.enabled() ? (enabled ? "protection switched on" : "protection switched off") : "")
                + (password != null ? (enabled != before.enabled() ? "; " : "") + "password changed" : "");
        audit.change(admin, "site_access", 0, "updated", what,
                Map.of("enabled", before.enabled(), "password", before.hasPassword() ? "set" : "not set"),
                Map.of("enabled", after.enabled(), "password", password != null ? "changed" : (after.hasPassword() ? "set" : "not set")));

        String message;
        if (after.active() && !before.active()) message = "Site password protection is on. Visitors now need the password; you stay in while you're signed in to the admin.";
        else if (!after.enabled() && before.enabled()) message = "Site password protection is off. The site is open to everyone.";
        else if (password != null && after.active()) message = "Password changed. Everyone who used the old one must enter the new one.";
        else message = password != null ? "Password saved. Switch protection on when you're ready." : "Saved.";
        return kit.ok(Map.of("message", message, "access", payload(after)));
    }

    private Map<String, Object> payload(SiteLock.State s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", s.enabled());
        m.put("hasPassword", s.hasPassword());
        m.put("active", s.active());
        m.put("changedAt", s.changedAt().isBlank() ? null : s.changedAt());
        m.put("changedBy", s.changedBy().isBlank() ? null : s.changedBy());
        m.put("serverLock", serverLock);
        m.put("minLength", SiteLock.MIN_LENGTH);
        return m;
    }
}
