package com.artistikcity.support;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Public file storage (Laravel's "public" disk). Files are written below {@code app.storage-dir}
 * (default ./storage) and served at /storage/** - so the React pages keep using the same URLs,
 * e.g. /storage/uploads/courses/{id}/{photo}.
 */
@Component
public class Storage {

    private final Path root;

    public Storage(Environment env) {
        this.root = Path.of(env.getProperty("app.storage-dir", "storage")).toAbsolutePath().normalize();
    }

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
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void copy(String fromRelative, String toRelative) {
        Path from = resolve(fromRelative);
        Path to = resolve(toRelative);
        if (!Files.exists(from)) {
            return;
        }
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    public byte[] read(String relativePath) {
        try {
            return Files.readAllBytes(resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path resolve(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes storage root: " + relativePath);
        }
        return p;
    }
}
