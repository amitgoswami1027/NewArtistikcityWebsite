package com.artistikcity.web;

import com.artistikcity.support.Storage;
import com.artistikcity.support.s3.S3Client;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Serves /storage/** from S3 when STORAGE_DRIVER=s3. On AWS, CloudFront normally answers these paths straight from
 * the bucket; this covers requests that reach the app anyway (no CloudFront, local testing). Supports byte ranges so
 * videos can seek. With the local driver, Spring's resource handler serves the files instead (WebConfig).
 */
@Controller
@ConditionalOnProperty(name = "app.storage.driver", havingValue = "s3")
public class StorageController {

    private final Storage storage;

    public StorageController(Storage storage) {
        this.storage = storage;
    }

    @RequestMapping(value = "/storage/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void serve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length() + "/storage/".length());
        String rel;
        try {
            rel = java.net.URLDecoder.decode(path.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
            Optional<S3Client.S3Object> found = storage.open(rel, request.getHeader("Range"));
            if (found.isEmpty()) {
                response.sendError(404);
                return;
            }
            S3Client.S3Object o = found.get();
            try (InputStream in = o.body()) {
                String etag = o.etag();
                if (etag != null && etag.equals(request.getHeader("If-None-Match")) && o.status() == 200) {
                    response.setStatus(304);
                    response.setHeader("ETag", etag);
                    return;
                }
                response.setStatus(o.status());
                response.setContentType(o.contentType());
                response.setHeader("Accept-Ranges", "bytes");
                response.setHeader("X-Content-Type-Options", "nosniff");
                if (o.length() >= 0) {
                    response.setContentLengthLong(o.length());
                }
                if (etag != null) {
                    response.setHeader("ETag", etag);
                }
                if (o.lastModified() != null) {
                    response.setHeader("Last-Modified", o.lastModified());
                }
                if (o.contentRange() != null) {
                    response.setHeader("Content-Range", o.contentRange());
                }
                response.setHeader("Cache-Control", o.cacheControl() != null ? o.cacheControl() : "public, max-age=86400");
                if (!"HEAD".equals(request.getMethod()) && o.status() != 416) {
                    OutputStream out = response.getOutputStream();
                    in.transferTo(out);
                }
            }
        } catch (IllegalArgumentException e) {
            response.sendError(404);
        }
    }
}
