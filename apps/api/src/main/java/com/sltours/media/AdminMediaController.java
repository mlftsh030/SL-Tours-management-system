package com.sltours.media;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Staff access to private media. Driver verification documents are restricted to administrators. */
@RestController
@RequestMapping("/api/v1/admin/media")
public class AdminMediaController {
    private final MediaService media;
    public AdminMediaController(MediaService media) { this.media = media; }
    @GetMapping("/{id}")
    ResponseEntity<InputStreamResource> get(@PathVariable UUID id, Authentication authentication) {
        MediaFile file = media.find(id);
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (file.getPurpose() == MediaPurpose.DRIVER_DOCUMENT && !admin) throw new AccessDeniedException("Administrators only");
        return MediaResponses.serve(file, media.open(file));
    }
}
