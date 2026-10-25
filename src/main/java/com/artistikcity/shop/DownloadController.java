package com.artistikcity.shop;

import com.artistikcity.marketplace.MarketplaceException;
import com.artistikcity.support.Storage;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Paid digital downloads: GET /shop/download/{token}. The token (48 hex characters, from the order e-mail and the order
 * page) is the key; it works for 30 days and 10 downloads, and only while the order is paid. The file is streamed from
 * private storage, never from /storage/**.
 */
@Controller
public class DownloadController {

    private final ShopOrderService orders;
    private final Storage storage;

    public DownloadController(ShopOrderService orders, Storage storage) {
        this.orders = orders;
        this.storage = storage;
    }

    @GetMapping("/shop/download/{token}")
    public void download(@PathVariable("token") String token, HttpServletResponse response) throws IOException {
        Map<String, Object> item;
        try {
            item = orders.download(token);
        } catch (MarketplaceException e) {
            response.setStatus(e.status());
            response.setContentType("text/html; charset=utf-8");
            response.getWriter().write("<!doctype html><meta charset=utf-8><title>Download</title><body style=\"font-family:sans-serif;padding:40px;max-width:560px\">"
                    + "<h1 style=\"font-size:22px\">Download unavailable</h1><p>" + escape(e.getMessage()) + "</p><p><a href=\"/marketplace\">Back to the marketplace</a></p>");
            return;
        }
        Optional<Storage.PrivateFile> file = storage.openPrivate(String.valueOf(item.get("hires_path")));
        if (file.isEmpty()) {
            response.sendError(404, "The file is missing. Contact us and we'll send it to you.");
            return;
        }
        orders.countDownload(((Number) item.get("id")).longValue());
        String path = String.valueOf(item.get("hires_path"));
        String ext = path.contains(".") ? path.substring(path.lastIndexOf('.')) : "";
        String name = (String.valueOf(item.get("slug")) + "-high-resolution" + ext).replaceAll("[^A-Za-z0-9._-]", "-");
        try (InputStream in = file.get().body()) {
            response.setStatus(200);
            response.setContentType(file.get().contentType());
            if (file.get().length() >= 0) response.setContentLengthLong(file.get().length());
            response.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            in.transferTo(response.getOutputStream());
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
