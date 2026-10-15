package com.sltours.media;
import com.sltours.audit.AuditService;
import com.sltours.shared.error.ResourceNotFoundException;
import java.io.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates and stores uploads. File types are detected from content (magic bytes), never trusted from the
 * client, and pictures must be complete and readable ({@link ImageCheck}). Files written during a transaction
 * that rolls back are removed again.
 */
@Service
public class MediaService {
    public enum Kind {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp"), PDF("application/pdf", "pdf");
        public final String contentType; public final String extension;
        Kind(String contentType, String extension) { this.contentType = contentType; this.extension = extension; }
    }
    public static final Set<Kind> IMAGES = EnumSet.of(Kind.JPEG, Kind.PNG, Kind.WEBP);
    public static final Set<Kind> DOCUMENTS = EnumSet.of(Kind.JPEG, Kind.PNG, Kind.WEBP, Kind.PDF);
    public static final Set<Kind> PDF_ONLY = EnumSet.of(Kind.PDF);

    private final MediaStorage storage;
    private final MediaRepository media;
    private final AuditService audit;
    private final long maxImageBytes;
    private final long maxDocumentBytes;
    public MediaService(MediaStorage storage, MediaRepository media, AuditService audit,
            @Value("${sltours.storage.max-image-bytes:10485760}") long maxImageBytes,
            @Value("${sltours.storage.max-document-bytes:15728640}") long maxDocumentBytes) {
        this.storage = storage; this.media = media; this.audit = audit;
        this.maxImageBytes = maxImageBytes; this.maxDocumentBytes = maxDocumentBytes;
    }

    @Transactional
    public MediaFile store(MultipartFile file, MediaPurpose purpose, MediaFile.Visibility visibility, Set<Kind> allowed) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("A file is required");
        long limit = allowed.contains(Kind.PDF) ? maxDocumentBytes : maxImageBytes;
        if (file.getSize() > limit) throw new IllegalArgumentException("Files must be " + (limit / 1_048_576) + " MB or smaller");
        try {
            byte[] bytes = file.getBytes();
            Kind kind = detect(bytes).filter(allowed::contains).orElseThrow(() -> new IllegalArgumentException(
                allowed.contains(Kind.PDF) ? "Upload a JPEG, PNG, WebP or PDF file" : "Upload a JPEG, PNG or WebP photo"));
            if (IMAGES.contains(kind)) ImageCheck.verify(kind, bytes);
            YearMonth month = YearMonth.now(ZoneOffset.UTC);
            String key = "%s/%d/%02d/%s.%s".formatted(purpose.name().toLowerCase(Locale.ROOT), month.getYear(), month.getMonthValue(),
                UUID.randomUUID(), kind.extension);
            storage.write(key, new ByteArrayInputStream(bytes));
            removeOnRollback(key);
            return media.saveAndFlush(new MediaFile(key, kind.contentType, bytes.length, sha256(bytes), safeName(file.getOriginalFilename()),
                purpose, visibility, actor()));
        } catch (IOException e) {
            throw new UncheckedIOException("The file could not be stored. Try again.", e);
        }
    }

    /** Stores a file the server generated itself (for example an issued invoice PDF), checked like an upload. */
    @Transactional
    public MediaFile storeGenerated(byte[] bytes, String filename, MediaPurpose purpose, Set<Kind> allowed) {
        Kind kind = detect(bytes).filter(allowed::contains).orElseThrow(() -> new IllegalStateException("Generated file has an unexpected type"));
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        String key = "%s/%d/%02d/%s.%s".formatted(purpose.name().toLowerCase(Locale.ROOT), month.getYear(), month.getMonthValue(),
            UUID.randomUUID(), kind.extension);
        try { storage.write(key, new ByteArrayInputStream(bytes)); }
        catch (IOException e) { throw new UncheckedIOException("The file could not be stored. Try again.", e); }
        removeOnRollback(key);
        return media.saveAndFlush(new MediaFile(key, kind.contentType, bytes.length, sha256(bytes), safeName(filename), purpose,
            MediaFile.Visibility.PRIVATE, actor()));
    }

    /** The stored bytes of a file. */
    public byte[] readAll(MediaFile file) {
        try (InputStream in = storage.read(file.getStorageKey())) { return in.readAllBytes(); }
        catch (IOException e) { throw new ResourceNotFoundException("File not found"); }
    }

    @Transactional(readOnly = true)
    public MediaFile find(UUID id) {
        return media.findById(id).filter(m -> !m.isDeleted()).orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    public InputStream open(MediaFile file) {
        try { return storage.read(file.getStorageKey()); }
        catch (IOException e) { throw new ResourceNotFoundException("File not found"); }
    }

    /** Removes the stored bytes and keeps the metadata row as a deletion record. */
    @Transactional
    public void delete(UUID id, String reason) {
        MediaFile file = find(id);
        file.markDeleted(actor());
        String key = file.getStorageKey();
        audit.record("MEDIA_DELETED", id.toString(), file.getPurpose() + ": " + reason);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { try { storage.delete(key); } catch (IOException ignored) { /* Retained file is reported by audits. */ } }
        });
    }

    static Optional<Kind> detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Optional.of(Kind.JPEG);
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A
            && b[6] == 0x1A && b[7] == 0x0A) return Optional.of(Kind.PNG);
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B'
            && b[11] == 'P') return Optional.of(Kind.WEBP);
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return Optional.of(Kind.PDF);
        return Optional.empty();
    }

    private void removeOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) try { storage.delete(key); } catch (IOException ignored) { /* Best effort cleanup. */ }
            }
        });
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String safeName(String name) {
        if (name == null) return null;
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.isBlank() ? null : base.substring(0, Math.min(base.length(), 200));
    }
    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
