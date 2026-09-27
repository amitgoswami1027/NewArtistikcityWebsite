package com.artistikcity.view;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.http.Flash;
import com.artistikcity.inertia.Routes;
import com.artistikcity.support.Row;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders Mustache templates (src/main/resources/templates) - the replacement for Blade views.
 *
 * Every page gets a shared context comparable to what Blade views could reach globally:
 * {@code csrf}, {@code auth.user} (student), {@code admin} (admin guard), {@code flash.*},
 * {@code errors.*}, {@code hasErrors}, {@code old.*} and {@code appName}.
 */
@Component
public class Views {

    private final Mustache.Compiler compiler;
    private final Auth auth;
    private final Routes routes;
    private final String appName;
    private final boolean cache;
    private final Map<String, Template> templates = new ConcurrentHashMap<>();

    public Views(Mustache.Compiler compiler, Auth auth, Routes routes, Environment env) {
        this.compiler = compiler;
        this.auth = auth;
        this.routes = routes;
        this.appName = env.getProperty("app.name", "ArtistikCity");
        this.cache = Boolean.parseBoolean(env.getProperty("app.template-cache", "true"));
    }

    /** Render a template with exactly the given model (used for e-mails and PDFs). */
    public String render(String name, Map<String, ?> model) {
        Template t = cache ? templates.computeIfAbsent(name, this::compile) : compile(name);
        return t.execute(model);
    }

    private Template compile(String name) {
        return compiler.loadTemplate(name);
    }

    /** Render a full page with the shared context. */
    public ResponseEntity<String> page(HttpServletRequest request, String name, Map<String, ?> model) {
        return page(request, name, model, HttpStatus.OK);
    }

    public ResponseEntity<String> page(HttpServletRequest request, String name, Map<String, ?> model, HttpStatus status) {
        Map<String, Object> ctx = shared(request);
        ctx.putAll(model);
        return ResponseEntity.status(status)
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .body(render(name, ctx));
    }

    public Map<String, Object> shared(HttpServletRequest request) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("appName", appName);
        ctx.put("csrf", Csrf.token(request));
        Row user = auth.user(request);
        ctx.put("auth", Row.of("user", user, "check", user != null, "guest", user == null));
        Row admin = auth.admin(request);
        if (admin != null) {
            admin = new Row(admin);
            admin.put("isAdmin", "admin".equals(admin.str("admin_type")));
            admin.put("type_label", "admin".equals(admin.str("admin_type")) ? "Admin" : "Teacher");
            admin.put("photo_url", admin.str("profile_photo") == null ? "/assets/images/faces-clipart/pic-1.png"
                    : "/storage/uploads/teachers/" + admin.get("id") + "/" + admin.str("profile_photo"));
        }
        ctx.put("admin", admin);
        // Blade helpers active_class() / is_active_route() / show_class() for the admin sidebar:
        // {{#activeClass}}admin/genres|admin/genres/*{{/activeClass}}
        String path = request.getRequestURI().replaceAll("^/+", "");
        ctx.put("activeClass", lambda(path, "active", ""));
        ctx.put("isActiveRoute", lambda(path, "true", "false"));
        ctx.put("showClass", lambda(path, "show", ""));
        Map<String, Object> flash = new HashMap<>(Flash.current(request));
        ctx.put("flash", flash);
        Map<String, String> errors = Flash.errors(request);
        ctx.put("errors", errors);
        ctx.put("hasErrors", !errors.isEmpty());
        ctx.put("errorList", new java.util.ArrayList<>(errors.values()));
        ctx.put("old", Flash.old(request));
        ctx.put("currentPath", request.getRequestURI());
        ctx.put("year", java.time.Year.now().getValue());
        ViewHelpers.register(ctx);
        return ctx;
    }

    /** Request::is() with "*" wildcards; patterns separated by "|". */
    private static Mustache.Lambda lambda(String path, String yes, String no) {
        return (frag, out) -> {
            boolean match = false;
            for (String p : frag.execute().trim().split("\\|")) {
                String rx = java.util.regex.Pattern.quote(p.trim()).replace("*", "\\E.*\\Q");
                if (!p.isBlank() && path.matches(rx)) {
                    match = true;
                }
            }
            out.write(match ? yes : no);
        };
    }

    public Routes routes() {
        return routes;
    }
}
