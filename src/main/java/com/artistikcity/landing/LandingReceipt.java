package com.artistikcity.landing;

import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The receipt of a landing page purchase: the same details as the thank-you e-mail ({@link LandingHooks#thankYouModel}),
 * shown on the congratulations screen (state result.receipt) and downloadable as a PDF at
 * {@code GET /landing/receipt/{orderId}.pdf} - only by the signed-in buyer of that order (otherwise 404), only for a paid
 * or refunded order (a refunded one is stamped). Rendered in memory with OpenHTMLtoPDF from templates/landing/receipt-pdf
 * with the bundled Poppins font (it has the ₹ sign; SIL Open Font License, fonts/poppins/OFL.txt); nothing is stored.
 */
@RestController
public class LandingReceipt {

    private static final Logger log = LoggerFactory.getLogger(LandingReceipt.class);

    /** The receipt fields the congratulations screen shows. */
    static final List<String> FIELDS = List.of("orderNumber", "paidOn", "billedTo", "billedEmail", "billedAddress", "course", "kind", "duration", "starts", "artist",
            "listPrice", "offer", "discount", "coupon", "tax", "taxLabel", "taxIncluded", "amount", "paidWith", "paymentRef", "test");

    private final Db db;
    private final Auth auth;
    private final Views views;
    private final LandingHooks hooks;
    private final Environment env;
    private volatile String logo;

    public LandingReceipt(Db db, Auth auth, Views views, LandingHooks hooks, Environment env) {
        this.db = db;
        this.auth = auth;
        this.views = views;
        this.hooks = hooks;
        this.env = env;
    }

    public static String pdfUrl(long orderId) {
        return "/landing/receipt/" + orderId + ".pdf";
    }

    /** The receipt for the congratulations screen, or null. */
    public Map<String, Object> view(HttpServletRequest request, long orderId, String email) {
        Map<String, Object> model = hooks.thankYouModel(orderId, email, base(request), false);
        if (model == null) return null;
        Map<String, Object> r = new LinkedHashMap<>();
        for (String k : FIELDS) r.put(k, model.get(k));
        r.put("pdf", pdfUrl(orderId));
        return r;
    }

    @GetMapping("/landing/receipt/{orderId}.pdf")
    public ResponseEntity<byte[]> pdf(HttpServletRequest request, @PathVariable("orderId") long orderId) {
        Row user = auth.user(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/login?return_to=" + pdfUrl(orderId)).build();
        }
        Row o = db.first("select id, payment_status from orders where id = ? and user_id = ? and landing_page_id is not null and payment_status in (1, 3)",
                orderId, user.get("id"));
        if (o == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).header("Content-Type", "text/plain;charset=UTF-8").body("Receipt not found.".getBytes());
        }
        Map<String, Object> model = hooks.thankYouModel(orderId, user.str("email", ""), base(request), false);
        model.put("logo", logo());
        model.put("refunded", o.lng("payment_status", 0) == 3);
        model.put("site", base(request).replaceAll("^https?://", ""));
        model.put("generatedOn", LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)) + " IST");
        byte[] pdf;
        try {
            pdf = render(views.render("landing/receipt-pdf", model));
        } catch (RuntimeException e) {
            log.error("Receipt PDF for order {} failed", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).header("Content-Type", "text/plain;charset=UTF-8")
                    .body("The receipt could not be created right now. Please try again.".getBytes());
        }
        String file = "ArtistikCity-receipt-" + String.valueOf(model.get("orderNumber")).replaceAll("[^A-Za-z0-9-]", "") + ".pdf";
        return ResponseEntity.ok().header("Content-Type", "application/pdf").header("Content-Disposition", "attachment; filename=\"" + file + "\"")
                .header("Cache-Control", "private, no-store").header("X-Robots-Tag", "noindex").body(pdf);
    }

    static byte[] render(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.useFont(() -> font("Poppins-Regular.ttf"), "Poppins", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            b.useFont(() -> font("Poppins-Bold.ttf"), "Poppins", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            b.withHtmlContent(html, null);
            b.toStream(out);
            b.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render the receipt", e);
        }
    }

    private static InputStream font(String name) {
        try {
            return new ClassPathResource("fonts/poppins/" + name).getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("Font missing: " + name, e);
        }
    }

    private String logo() {
        if (logo == null) {
            try (InputStream in = new ClassPathResource("static/assets/images/logo.png").getInputStream()) {
                logo = "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
            } catch (IOException e) {
                logo = "";
            }
        }
        return logo;
    }

    private String base(HttpServletRequest request) {
        String b = env.getProperty("app.url", "").trim();
        return (b.isEmpty() ? Inertia.baseUrl(request) : b).replaceAll("/+$", "");
    }
}
