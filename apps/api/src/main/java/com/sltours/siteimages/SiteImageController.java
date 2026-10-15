package com.sltours.siteimages;
import com.sltours.siteimages.SiteImageService.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SiteImageController {
    private final SiteImageService images;
    public SiteImageController(SiteImageService images) { this.images = images; }
    @GetMapping("/api/v1/public/site-images") List<SiteImageView> published(@RequestParam SiteImage.Slot slot) { return images.published(slot); }
    @GetMapping("/api/v1/admin/site-images") @PreAuthorize("hasRole('ADMIN')") List<SiteImageView> all(@RequestParam SiteImage.Slot slot) { return images.all(slot); }
    @PostMapping(path = "/api/v1/admin/site-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    SiteImageView upload(@RequestParam SiteImage.Slot slot, @RequestParam String altText, @RequestPart("file") MultipartFile file) {
        return images.upload(slot, file, altText);
    }
    @PutMapping("/api/v1/admin/site-images/{id}") @PreAuthorize("hasRole('ADMIN')")
    SiteImageView update(@PathVariable UUID id, @Valid @RequestBody SaveSiteImageRequest request) { return images.update(id, request); }
    @DeleteMapping("/api/v1/admin/site-images/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')")
    void delete(@PathVariable UUID id) { images.delete(id); }
}
