package com.artistikcity.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * One-off clean-up of photos uploaded before metadata stripping existed: start the app once with
 * {@code --app.uploads.strip-existing=true} (or env {@code UPLOADS_STRIP_EXISTING=true}). In the background it reads
 * every JPEG / PNG / WebP under {@code uploads/} (local disk or S3), removes EXIF / XMP / IPTC with
 * {@link ImageMetadata} and writes the file back under the same name only when something was removed. Safe to run
 * again: clean files are left untouched. The site keeps serving meanwhile; the summary is logged.
 */
@Component
public class ImageMetadataJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImageMetadataJob.class);

    private final Storage storage;
    private final Environment env;

    public ImageMetadataJob(Storage storage, Environment env) {
        this.storage = storage;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!Boolean.parseBoolean(env.getProperty("app.uploads.strip-existing", "false").trim())) return;
        Thread t = new Thread(this::cleanAll, "strip-existing-metadata");
        t.setDaemon(true);
        t.start();
    }

    /** @return {checked, cleaned, failed} */
    public int[] cleanAll() {
        float q;
        try {
            q = Float.parseFloat(env.getProperty("app.uploads.jpeg-quality", "0.92"));
        } catch (NumberFormatException e) {
            q = 0.92f;
        }
        int checked = 0, cleaned = 0, failed = 0;
        for (String rel : storage.listPublic("uploads")) {
            if (!ImageMetadata.applies(rel)) continue;
            checked++;
            try {
                byte[] before = storage.read(rel);
                byte[] after = ImageMetadata.strip(before, rel, q);
                if (!Arrays.equals(before, after)) {
                    storage.put(rel, after);
                    cleaned++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not clean {}: {}", rel, e.toString());
            }
        }
        log.info("Photo metadata clean-up finished: {} photos checked, {} cleaned, {} failed", checked, cleaned, failed);
        return new int[]{checked, cleaned, failed};
    }
}
