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
    /** app.uploads.strip-metadata: public photos lose EXIF / XMP / IPTC (GPS!) before they are stored. */
    private final boolean stripMetadata;
    private final float jpegQuality;

    /**
     * Absolute address of the public files on S3 ({@code S3_PUBLIC_BASE_URL}, e.g. https://cdn.example.com or
     * https://my-bucket.s3.ap-south-1.amazonaws.com), followed by the key prefix. Empty: files are linked as
     * /storage/... on this site (CloudFront or the app serves them). Static so value objects can recognise our URLs.
     */
    private static volatile String publicBase = "";

    @Autowired
    public Storage(Environment env) {
        this.root = Path.of(env.getProperty("app.storage-dir", "storage")).toAbsolutePath().normalize();
        this.privateRoot = Path.of(env.getProperty("app.private-storage-dir", "storage-private")).toAbsolutePath().normalize();
        this.stripMetadata = !"false".equalsIgnoreCase(env.getProperty("app.uploads.strip-metadata", "true").trim());
        float q;
        try {
            q = Float.parseFloat(env.getProperty("app.uploads.jpeg-quality", "0.92").trim());
        } catch (NumberFormatException e) {
            q = 0.92f;
        }
        this.jpegQuality = Math.max(0.5f, Math.min(1f, q));
        String driver = env.getProperty("app.storage.driver", "local").trim().toLowerCase(Locale.ROOT);
        if ("s3".equals(driver)) {
            this.s3 = new S3Client(env.getProperty("app.storage.s3.bucket", ""), env.getProperty("app.storage.s3.region", ""),
                    env.getProperty("app.storage.s3.endpoint", ""), new AwsCredentialsProvider());
            String p = env.getProperty("app.storage.s3.prefix", "storage/");
            this.prefix = p.isEmpty() || p.endsWith("/") ? p : p + "/";
            String base = env.getProperty("app.storage.s3.public-base-url", "").trim();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            if (!base.isEmpty() && !base.matches("https?://[^\\s/]+(/[^\\s]*)?")) {
                throw new IllegalStateException("S3_PUBLIC_BASE_URL must be an http(s) address, not " + base);
            }
            publicBase = base.isEmpty() ? "" : base + "/" + this.prefix;
        } else if ("local".equals(driver)) {
            this.s3 = null;
            this.prefix = "";
            publicBase = "";
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
        this.stripMetadata = true;
        this.jpegQuality = 0.92f;
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

    /** Relative paths of every public file under a folder (e.g. "uploads"), on disk or in S3. Maintenance jobs only. */
    public java.util.List<String> listPublic(String folder) {
        String dir = clean(folder);
        if (s3 != null) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String k : s3.list(prefix + dir + "/")) out.add(k.substring(prefix.length()));
            return out;
        }
        Path base = local(dir);
        if (!Files.isDirectory(base)) return java.util.List.of();
        try (var walk = Files.walk(base)) {
            return walk.filter(Files::isRegularFile).map(p -> root.relativize(p).toString().replace('\\', '/')).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void put(String relativePath, byte[] bytes) {
        String rel = clean(relativePath);
        if (stripMetadata && ImageMetadata.applies(rel)) {
            bytes = ImageMetadata.strip(bytes, rel, jpegQuality);
        }
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

    /** Stores generated bytes (e.g. a PDF rendered in memory) as a private file. */
    public void putPrivate(String relativePath, byte[] bytes) {
        putPrivate(relativePath, new java.io.ByteArrayInputStream(bytes), bytes.length);
    }

    /**
     * A short-lived link the browser can open a private file with: on S3 a presigned URL straight to the bucket
     * (the app never proxies the bytes), empty with the local driver, where the caller streams the file itself.
     */
    public Optional<String> privateLink(String relativePath, java.time.Duration ttl, String downloadName, boolean inline) {
        if (s3 == null) {
            return Optional.empty();
        }
        String rel = clean(relativePath);
        return Optional.of(s3.presignedGet(PRIVATE_PREFIX + rel, ttl, contentType(rel), downloadName, inline));
    }

    /** The S3 object key of a private file (what the database records), e.g. {@code private/documents/x.pdf}. */
    public static String privateKey(String relativePath) {
        return PRIVATE_PREFIX + clean(relativePath);
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

    /**
     * Address to save in the database and put in pages for a public file: the absolute S3 / CDN URL when
     * S3_PUBLIC_BASE_URL is set (e.g. https://cdn.example.com/storage/uploads/marketplace/2026/11/x.jpg), otherwise
     * {@link #url(String)}.
     */
    public static String publicUrl(String relativePath) {
        String base = publicBase;
        return base.isEmpty() ? url(relativePath) : base + clean(relativePath);
    }

    /**
     * The storage path behind one of our file URLs: /storage/uploads/x.jpg or {S3_PUBLIC_BASE_URL}/storage/uploads/x.jpg
     * both give uploads/x.jpg. Empty for anything else (other sites, /assets/..., unsafe paths).
     */
    public static Optional<String> pathOf(String url) {
        if (url == null) return Optional.empty();
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        String rel = null;
        String base = publicBase;
        if (u.startsWith("/storage/")) rel = u.substring("/storage/".length());
        else if (!base.isEmpty() && u.startsWith(base)) rel = u.substring(base.length());
        if (rel == null || rel.isEmpty()) return Optional.empty();
        try {
            return Optional.of(clean(java.net.URLDecoder.decode(rel, java.nio.charset.StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** For tests. */
    static void publicBaseForTests(String base) {
        publicBase = base == null ? "" : base;
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
