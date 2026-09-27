package com.artistikcity.marketplace;

import com.artistikcity.support.Storage;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Stores listing photos under uploads/marketplace/, after checking type, size and resolution. */
@Component
public class MarketplaceImages {

    public static final long MAX_BYTES = 20L * 1024 * 1024;
    public static final int MIN_LONG_EDGE = 800;

    private final Storage storage;

    public MarketplaceImages(Storage storage) {
        this.storage = storage;
    }

    /** @return {url, width, height} or throws with a message for the uploader */
    public Map<String, Object> store(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new MarketplaceException(422, "NO_FILE", "Choose a photo to upload.");
        if (bytes.length > MAX_BYTES) throw new MarketplaceException(422, "TOO_LARGE", "That photo is larger than 20 MB.");
        String ext = sniff(bytes);
        if (ext == null) throw new MarketplaceException(422, "TYPE", "Upload a JPG or PNG photo.");
        int w = 0, h = 0;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
                w = img.getWidth();
                h = img.getHeight();
            }
        } catch (IOException ignored) {
            // unreadable metadata: keep the file, size check skipped
        }
        if (w > 0 && Math.max(w, h) < MIN_LONG_EDGE) {
            throw new MarketplaceException(422, "LOW_RES", "This photo is " + w + "×" + h + " px. Collectors zoom in on brushwork, so use at least "
                    + MIN_LONG_EDGE + " px on the longest side (2000 px or more is ideal).");
        }
        String name = UUID.randomUUID() + "." + ext;
        storage.put("uploads/marketplace/" + name, bytes);
        return Map.of("url", "/storage/uploads/marketplace/" + name, "width", w, "height", h);
    }

    static String sniff(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        return null;
    }
}
