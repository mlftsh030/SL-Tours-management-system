package com.sltours.media;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.sltours.shared.error.ResourceNotFoundException;

/** Serves only files explicitly published (vehicle listing photos, website images). */
@RestController
@RequestMapping("/api/v1/public/media")
public class PublicMediaController {
    private final MediaService media;
    public PublicMediaController(MediaService media) { this.media = media; }
    @GetMapping("/{id}")
    ResponseEntity<InputStreamResource> get(@PathVariable UUID id) {
        MediaFile file = media.find(id);
        if (file.getVisibility() != MediaFile.Visibility.PUBLIC) throw new ResourceNotFoundException("File not found");
        return MediaResponses.serve(file, media.open(file));
    }
}
