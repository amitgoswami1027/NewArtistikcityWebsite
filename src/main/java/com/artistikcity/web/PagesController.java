package com.artistikcity.web;

import com.artistikcity.http.Flash;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.Mailer;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Validator;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/** Static pages and the contact form (PagesController.php). */
@Controller
public class PagesController {

    private final Views views;
    private final Inertia inertia;
    private final Db db;
    private final Json json;
    private final Validator validator;
    private final Mailer mailer;
    private final Redirects redirect;
    private final String adminEmail;
    private final com.artistikcity.admin.SiteSettings site;

    public PagesController(Views views, Inertia inertia, Db db, Json json, Validator validator, Mailer mailer,
                           Redirects redirect, Environment env, com.artistikcity.admin.SiteSettings site) {
        this.site = site;
        this.views = views;
        this.inertia = inertia;
        this.db = db;
        this.json = json;
        this.validator = validator;
        this.mailer = mailer;
        this.redirect = redirect;
        this.adminEmail = env.getProperty("app.admin-email", "admin@artistik.com");
    }

    @GetMapping("/home")
    public ResponseEntity<String> home(HttpServletRequest request) {
        return inertia.render(request, "Home", Map.of("mediums", db.select("select * from mediums order by id")));
    }

    /** New "How it works" page (React / Inertia). */
    @GetMapping("/how-it-works")
    public ResponseEntity<String> howItWorks(HttpServletRequest request) {
        return inertia.render(request, "HowItWorks", Map.of());
    }

    @GetMapping("/customer-support")
    public ResponseEntity<String> customerSupport(HttpServletRequest request) {
        return views.page(request, "pages/customer-support", Map.of());
    }

    /** FAQ from Admin > FAQ, grouped by category in the admin's order. */
    @GetMapping("/faq")
    public ResponseEntity<String> faq(HttpServletRequest request) {
        java.util.Map<String, java.util.List<Row>> byCategory = new java.util.LinkedHashMap<>();
        try {
            for (Row f : db.select("select question, answer, category from faqs where status = 1 order by sort_order, id")) {
                byCategory.computeIfAbsent(f.str("category", ""), k -> new java.util.ArrayList<>()).add(f);
            }
        } catch (com.artistikcity.support.Db.DbException e) {
            // table missing until the migration runs: show the empty state
        }
        java.util.List<Map<String, Object>> groups = new java.util.ArrayList<>();
        byCategory.forEach((cat, items) -> {
            Map<String, Object> g = new java.util.LinkedHashMap<>();
            g.put("category", cat.isBlank() ? null : cat);
            g.put("items", items);
            groups.add(g);
        });
        return views.page(request, "pages/faq", Map.of("groups", groups));
    }

    /** Contact details and social links come from Admin > Site settings; empty values are hidden. */
    @GetMapping("/contact")
    public ResponseEntity<String> contact(HttpServletRequest request) {
        Map<String, Object> contact = new java.util.LinkedHashMap<>();
        for (String k : java.util.List.of("phone", "email", "address")) {
            String v = site.get("contact", k, "");
            contact.put(k, v.isBlank() ? null : v);
        }
        Map<String, Object> social = new java.util.LinkedHashMap<>();
        for (String k : java.util.List.of("facebook", "instagram", "youtube", "linkedin")) {
            String v = site.get("social", k, "");
            social.put(k, v.isBlank() ? null : v);
        }
        String address = (String) contact.get("address");
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("contact", contact);
        m.put("social", social);
        m.put("hasConnect", contact.get("phone") != null || contact.get("email") != null);
        m.put("contactPhoneLink", contact.get("phone") == null ? null : String.valueOf(contact.get("phone")).replaceAll("[^0-9+]", ""));
        m.put("addressLines", address == null ? java.util.List.of() : java.util.Arrays.stream(address.split("\\r?\\n|,\\s*(?=\\S)")).map(String::trim).filter(x -> !x.isEmpty()).toList());
        m.put("mapUrl", address == null ? null : "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8));
        return views.page(request, "pages/contact", m);
    }

    @PostMapping("/contact")
    public ResponseEntity<String> contactStore(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required", "mobile_no", "numeric", "email", "required|email");
        db.insert("contacts", Row.of("name", in.get("name"), "mobile_no", in.get("mobile_no"), "email", in.get("email"),
                "message", in.get("message"), "status", 1));
        // the message is also kept in Admin > Contact messages
        boolean sent = mailer.send(adminEmail, "New contact enquiry", "emails/contact",
                Row.of("contactData", Row.of("name", in.get("name"), "email", in.get("email"),
                        "mobile_no", in.get("mobile_no"), "message", in.get("message"))));
        // the Blade page displays "success"/"error" flash keys
        Flash.put(request, sent ? "success" : "error", sent
                ? "Thanks for contacting us, our team will get back to you shortly."
                : "There is some issue please try again later.");
        return redirect.route(request, "contact");
    }

    @GetMapping("/privacy-policy")
    public ResponseEntity<String> privacyPolicy(HttpServletRequest request) {
        return views.page(request, "pages/privacy-policy", Map.of());
    }

    @GetMapping("/terms-conditions")
    public ResponseEntity<String> termsConditions(HttpServletRequest request) {
        return views.page(request, "pages/terms-conditions", Map.of());
    }

    @GetMapping("/artistikcity-vision")
    public ResponseEntity<String> artistikcityVision(HttpServletRequest request) {
        return views.page(request, "pages/artistikcity-vision", Map.of());
    }
}
