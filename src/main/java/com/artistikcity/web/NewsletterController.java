package com.artistikcity.web;

import com.artistikcity.admin.NewsletterService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.util.Map;

/** Signed unsubscribe link from newsletter e-mails: GET shows a confirmation (link scanners don't unsubscribe anyone), POST unsubscribes. */
@Controller
public class NewsletterController {

    private final Db db;
    private final Json json;
    private final Views views;
    private final NewsletterService newsletter;

    public NewsletterController(Db db, Json json, Views views, NewsletterService newsletter) {
        this.db = db;
        this.json = json;
        this.views = views;
        this.newsletter = newsletter;
    }

    @GetMapping("/newsletter/unsubscribe")
    public ResponseEntity<String> confirm(HttpServletRequest request) {
        String email = request.getParameter("e");
        String token = request.getParameter("t");
        if (!newsletter.validToken(email, token)) {
            return views.page(request, "pages/unsubscribe", Map.of("invalid", true));
        }
        return views.page(request, "pages/unsubscribe", Map.of("confirm", true, "email", email.trim().toLowerCase(), "token", token));
    }

    @PostMapping("/newsletter/unsubscribe")
    public ResponseEntity<String> unsubscribe(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String email = in.get("e");
        if (!newsletter.validToken(email, in.get("t"))) {
            return views.page(request, "pages/unsubscribe", Map.of("invalid", true));
        }
        String e = email.trim().toLowerCase();
        db.update("update newsletters set unsubscribed_at = ?, updated_at = ? where lower(email) = ? and unsubscribed_at is null",
                LocalDateTime.now(), LocalDateTime.now(), e);
        return views.page(request, "pages/unsubscribe", Map.of("done", true, "email", e));
    }
}
