package com.sltours.media;
import java.io.InputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;

/** Response headers for serving stored files: no sniffing, no shared caching of private files. */
public final class MediaResponses {
    private MediaResponses() {}
    public static ResponseEntity<InputStreamResource> serve(MediaFile file, InputStream content) {
        boolean pdf = file.getContentType().equals("application/pdf");
        String name = file.getOriginalFilename() == null ? file.getId() + "" : file.getOriginalFilename();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .contentLength(file.getSizeBytes())
            .header(HttpHeaders.CACHE_CONTROL, file.getVisibility() == MediaFile.Visibility.PUBLIC ? "public, max-age=86400" : "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(pdf ? "attachment" : "inline").filename(name).build().toString())
            .body(new InputStreamResource(content));
    }
}
