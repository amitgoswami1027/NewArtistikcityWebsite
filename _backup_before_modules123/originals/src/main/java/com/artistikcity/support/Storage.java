package com.artistikcity.support;

import com.artistikcity.support.s3.AwsCredentialsProvider;
import com.artistikcity.support.s3.S3Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Public file storage (Laravel's "public" disk). Every file has a relative path such as
 * {@code uploads/courses/12/cover.jpg} and is served at {@code /storage/<path>}, so pages and database values never
 * change when the storage changes.
 *
 * <ul>
 *   <li>{@code STORAGE_DRIVER=local} (default): files live below {@code app.storage-dir} (default ./storage) and are
 *       served by Spring's resource handler (see WebConfig).</li>
 *   <li>{@code STORAGE_DRIVER=s3}: files live in the S3 bucket {@code S3_BUCKET} under the key {@code S3_PREFIX + path}
 *       (prefix default {@code storage/}, so the key equals the URL path without the leading slash). On AWS, CloudFront
 *       serves {@code /storage/*} straight from the bucket; {@code StorageController} streams them when a request does
 *       reach the app.</li>
 * </ul>
 */
@Component
public class Storage {

    /** Uploaded files may be replaced under the same name (e.g. a cover photo), so caches keep them for a day. */
    static final String CACHE_CONTROL = "public, max-age=86400";

    private final Path root;
    private final Path privateRoot;
    private final S3Client s3;
    private final String prefix;

    @Autowired
    public Storage(Environment env) {
        this.root = Path.of(env.getProperty("app.storage-dir", "storage")).toAbsolutePath().normalize();
        this.privateRoot = Path.of(env.getProperty("app.private-storage-dir", "storage-private")).toAbsolutePath().normalize();
        String driver = env.getProperty("app.storage.driver", "local").trim().toLowerCase(Locale.ROOT);
        if ("s3".equals(driver)) {
            this.s3 = new S3Client(env.getProperty("app.storage.s3.bucket", ""), env.getProperty("app.storage.s3.region", ""),
                    env.getProperty("app.storage.s3.endpoint", ""), new AwsCredentialsProvider());
            String p = env.getProperty("app.storage.s3.prefix", "storage/");
            this.prefix = p.isEmpty() || p.endsWith("/") ? p : p + "/";
        } else if ("local".equals(driver)) {
            this.s3 = null;
            this.prefix = "";
        } else {
            throw new IllegalStateException("STORAGE_DRIVER must be local or s3, not " + driver);
        }
    }

    /** For tests. */
    Storage(Path root, S3Client s3, String prefix) {
        this.root = root;
        this.privateRoot = root.resolveSibling(root.getFileName() + "-private");
        this.s3 = s3;
        this.prefix = prefix;
    }

    public boolean isS3() {
        return s3 != null;
    }

    /** Root folder of the local driver (also used for temporary files with S3). */
    public Path root() {
        return root;
    }

    /**
     * Laravel's {@code $file->storeAs($dir, time().'_'.$original, 'public')}.
     *
     * @return the stored file name (without directory)
     */
    public String storeUpload(UploadedFile file, String dir) {
        String name = (System.currentTimeMillis() / 1000) + "_" + file.getClientOriginalName();
        put(dir + "/" + name, file.getContent());
        return name;
    }

    public void put(String relativePath, byte[] bytes) {
        String rel = clean(relativePath);
        if (s3 != null) {
            s3.put(prefix + rel, bytes, contentType(rel), CACHE_CONTROL);
            return;
        }
        Path target = local(rel);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Stores a large file without holding it in memory (video uploads). {@code length} must be exact. */
    public void put(String relativePath, InputStream in, long length) {
        String rel = clean(relativePath);
        if (s3 != null) {
            s3.put(prefix + rel, in, length, contentType(rel), CACHE_CONTROL);
            return;
        }
        Path target = local(rel);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void copy(String fromRelative, String toRelative) {
        String from = clean(fromRelative);
        String to = clean(toRelative);
        if (s3 != null) {
            s3.copy(prefix + from, prefix + to);
            return;
        }
        Path a = local(from);
        Path b = local(to);
        if (!Files.exists(a)) {
            return;
        }
        try {
            Files.createDirectories(b.getParent());
            Files.copy(a, b, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean exists(String relativePath) {
        String rel = clean(relativePath);
        return s3 != null ? s3.exists(prefix + rel) : Files.exists(local(rel));
    }

    public byte[] read(String relativePath) {
        String rel = clean(relativePath);
        if (s3 != null) {
            return s3.read(prefix + rel);
        }
        try {
            return Files.readAllBytes(local(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A stored file as a stream (S3 driver; the local driver is served by Spring's resource handler). */
    public Optional<S3Client.S3Object> open(String relativePath, String range) {
        if (s3 == null) {
            throw new IllegalStateException("open() is for the S3 driver");
        }
        return s3.get(prefix + clean(relativePath), range);
    }

    // ------------------------------------------------------------------ private files

    /**
     * Private files (paid downloads such as high-resolution artwork) are never served at /storage/**: locally they live
     * in {@code app.private-storage-dir} (default ./storage-private, a sibling of the public folder), on S3 under the key
     * prefix {@code private/}, which CloudFront has no route to. The app streams them after checking the buyer.
     */
    public void putPrivate(String relativePath, InputStream in, long length) {
        String rel = clean(relativePath);
        if (s3 != null) {
            s3.put(PRIVATE_PREFIX + rel, in, length, contentType(rel), "private, no-store");
            return;
        }
        Path target = privateLocal(rel);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A private file to stream to the buyer, or empty when it is missing. The caller closes the stream. */
    public Optional<PrivateFile> openPrivate(String relativePath) {
        String rel = clean(relativePath);
        if (s3 != null) {
            return s3.get(PRIVATE_PREFIX + rel).map(o -> new PrivateFile(o.body(), o.length(), contentType(rel)));
        }
        Path p = privateLocal(rel);
        if (!Files.isRegularFile(p)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PrivateFile(Files.newInputStream(p), Files.size(p), contentType(rel)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean existsPrivate(String relativePath) {
        String rel = clean(relativePath);
        return s3 != null ? s3.exists(PRIVATE_PREFIX + rel) : Files.isRegularFile(privateLocal(rel));
    }

    public void deletePrivate(String relativePath) {
        String rel = clean(relativePath);
        if (s3 != null) {
            s3.delete(PRIVATE_PREFIX + rel);
            return;
        }
        try {
            Files.deleteIfExists(privateLocal(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record PrivateFile(InputStream body, long length, String contentType) {
    }

    static final String PRIVATE_PREFIX = "private/";

    private Path privateLocal(String rel) {
        Path p = privateRoot.resolve(rel).normalize();
        if (!p.startsWith(privateRoot)) {
            throw new IllegalArgumentException("Path escapes private storage: " + rel);
        }
        return p;
    }

    /** Public URL path of a stored file, e.g. {@code /storage/uploads/x.jpg}. */
    public static String url(String relativePath) {
        return "/storage/" + clean(relativePath);
    }

    /** The local file for a path (local driver only). */
    public Path resolve(String relativePath) {
        return local(clean(relativePath));
    }

    private Path local(String rel) {
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes storage root: " + rel);
        }
        return p;
    }

    /** Normalises a relative path and rejects anything that could leave the storage area. */
    static String clean(String relativePath) {
        if (relativePath == null) {
            throw new IllegalArgumentException("No storage path");
        }
        String p = relativePath.replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        p = p.replaceAll("/{2,}", "/");
        if (p.isEmpty() || p.endsWith("/")) {
            throw new IllegalArgumentException("Bad storage path: " + relativePath);
        }
        for (String seg : p.split("/")) {
            if (seg.equals("..") || seg.equals(".") || seg.isEmpty()) {
                throw new IllegalArgumentException("Path escapes storage root: " + relativePath);
            }
        }
        return p;
    }

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("avif", "image/avif"), Map.entry("pdf", "application/pdf"),
            Map.entry("mp4", "video/mp4"), Map.entry("m4v", "video/x-m4v"), Map.entry("mov", "video/quicktime"), Map.entry("webm", "video/webm"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("txt", "text/plain; charset=utf-8"), Map.entry("csv", "text/csv; charset=utf-8"),
            Map.entry("json", "application/json"), Map.entry("zip", "application/zip"));

    /** Content type by extension; unknown types download instead of rendering (no HTML from user uploads). */
    public static String contentType(String path) {
        int dot = path.lastIndexOf('.');
        String ext = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }
}
