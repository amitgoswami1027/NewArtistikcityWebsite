package com.artistikcity.web.admin;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.http.Redirects;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.ValidationException;
import com.artistikcity.support.Validator;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * Admin / teacher login (Admin\Auth\LoginController - referenced by the Laravel routes but missing from
 * the original repository, so it is implemented here).
 */
@Controller
public class AdminAuthController {

    private final Views views;
    private final Auth auth;
    private final Json json;
    private final Validator validator;
    private final Redirects redirect;

    public AdminAuthController(Views views, Auth auth, Json json, Validator validator, Redirects redirect) {
        this.views = views;
        this.auth = auth;
        this.json = json;
        this.validator = validator;
        this.redirect = redirect;
    }

    @GetMapping({"/admin", "/admin/login"})
    public ResponseEntity<String> loginForm(HttpServletRequest request) {
        if (auth.admin(request) != null) {
            return redirect.route(request, "admin.dashboard");
        }
        return views.page(request, "admin/login", Map.of());
    }

    @PostMapping("/admin/login")
    public ResponseEntity<String> login(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "email", "required|email", "password", "required");
        if (!auth.attemptAdmin(request, in.get("email"), in.get("password"))) {
            throw ValidationException.withMessage("email", "These credentials do not match our records.");
        }
        return redirect.route(request, "admin.dashboard");
    }

    @PostMapping("/admin/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        auth.logoutAdmin(request);
        return redirect.route(request, "admin.login");
    }

    @GetMapping("/admin/password/reset")
    public ResponseEntity<String> passwordRequest(HttpServletRequest request) {
        Flash.put(request, "info", "Please contact the site administrator to reset an admin password.");
        return redirect.route(request, "admin.login");
    }
}
