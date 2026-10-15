package com.sltours.media;
import java.io.*;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Private filesystem storage, mounted as a Docker volume in deployments. Keys are generated server-side. */
@Component
public class LocalMediaStorage implements MediaStorage {
    private final Path root;
    public LocalMediaStorage(@Value("${sltours.storage.local-root}") String root) throws IOException {
        this.root = Files.createDirectories(Path.of(root)).toAbsolutePath().normalize();
    }
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return path;
    }
    @Override public void write(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
    @Override public InputStream read(String key) throws IOException { return Files.newInputStream(resolve(key)); }
    @Override public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key)); }
}
