package com.artistikcity.landing;

import com.artistikcity.support.Storage;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Resized copies of public uploads for responsive images: {@code /img/w640/uploads/courses/1/course.jpg}. Only fixed widths
 * and only files under uploads/ (no way to resize arbitrary things or to read private files). The first request makes the
 * copy (JPEG, quality 0.8, metadata dropped) and stores it under cache/img/; later ones are served from there with a
 * one-year cache header, so a CDN keeps them too. A source smaller than the width is re-encoded, never enlarged.
 */
@RestController
public class LandingImages {

    private static final Logger log = LoggerFactory.getLogger(LandingImages.class);
    public static final List<Integer> WIDTHS = List.of(320, 480, 640, 960, 1280, 1600);
    private static final Set<String> TYPES = Set.of("jpg", "jpeg", "png");

    private final Storage storage;

    public LandingImages(Storage storage) {
        this.storage = storage;
    }

    /** srcset for a /storage/uploads/... address; null for other addresses (they are used as they are). */
    public static String srcset(String url, int maxWidth) {
        String path = sourcePath(url);
        if (path == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int w : WIDTHS) {
            if (w > maxWidth) break;
            if (sb.length() > 0) sb.append(", ");
            sb.append("/img/w").append(w).append('/').append(path).append(' ').append(w).append('w');
        }
        return sb.toString();
    }

    /** A single resized address (e.g. the hero's src or a thumbnail). */
    public static String sized(String url, int width) {
        String path = sourcePath(url);
        int w = WIDTHS.stream().filter(x -> x >= width).findFirst().orElse(WIDTHS.get(WIDTHS.size() - 1));
        return path == null ? url : "/img/w" + w + "/" + path;
    }

    static String sourcePath(String url) {
        if (url == null || url.contains("..") || url.contains("?")) {
            return null;
        }
        // "/storage/uploads/..." or the S3 / CDN address of the same file (Storage.publicUrl)
        String p = Storage.pathOf(url).orElse(null);
        if (p == null || !p.startsWith("uploads/")) {
            return null;
        }
        String ext = p.substring(p.lastIndexOf('.') + 1).toLowerCase();
        return TYPES.contains(ext) ? p : null;
    }

    @GetMapping("/img/w{width}/uploads/**")
    public ResponseEntity<byte[]> image(HttpServletRequest request, @PathVariable("width") int width) {
        String uri = request.getRequestURI();
        String path = uri.substring(uri.indexOf("/uploads/") + 1);
        String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        if (!WIDTHS.contains(width) || path.contains("..") || path.contains("//") || !TYPES.contains(ext)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String cached = "cache/img/w" + width + "/" + path.replaceAll("\\.(png|jpeg|jpg)$", "") + ".jpg";
        try {
            byte[] out = storage.exists(cached) ? storage.read(cached) : null;
            if (out == null) {
                if (!storage.exists(path)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                }
                out = resize(storage.read(path), width);
                if (out == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                }
                storage.put(cached, out);
            }
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()).body(out);
        } catch (Exception e) {
            log.warn("Could not resize {} to {}: {}", path, width, e.toString());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    static byte[] resize(byte[] source, int width) throws Exception {
        BufferedImage in = ImageIO.read(new ByteArrayInputStream(source));
        if (in == null || in.getWidth() > 12000 || in.getHeight() > 12000) {
            return null;
        }
        int w = Math.min(width, in.getWidth());
        int h = Math.max(1, Math.round(in.getHeight() * (w / (float) in.getWidth())));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // two-step downscale keeps large photos sharp
        BufferedImage src = in;
        while (src.getWidth() / 2 > w) {
            BufferedImage half = new BufferedImage(src.getWidth() / 2, src.getHeight() / 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D hg = half.createGraphics();
            hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            hg.drawImage(src, 0, 0, half.getWidth(), half.getHeight(), null);
            hg.dispose();
            src = half;
        }
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.8f);
            p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new IIOImage(out, null, null), p);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }
}
