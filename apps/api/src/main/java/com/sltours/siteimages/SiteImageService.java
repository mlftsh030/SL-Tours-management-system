package com.sltours.siteimages;
import com.sltours.audit.AuditService;
import com.sltours.media.*;
import com.sltours.shared.error.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * New uploads start inactive, so the live website keeps its current images until an administrator explicitly
 * activates a replacement. Hero images replace the built-in slider only while at least one is active; gallery
 * images are shown in addition to the existing gallery.
 */
@Service
public class SiteImageService {
    public record SiteImageView(UUID id, long version, SiteImage.Slot slot, String url, String altText, String title, String subtitle,
        String body, int sortOrder, boolean active) {}
    public record SaveSiteImageRequest(@NotNull @Min(0) Long version, @NotBlank @Size(max = 300) String altText, @Size(max = 160) String title,
        @Size(max = 160) String subtitle, @Size(max = 300) String body, @Min(0) @Max(1000) int sortOrder, boolean active) {}
    private final SiteImageRepository images;
    private final MediaService media;
    private final AuditService audit;
    public SiteImageService(SiteImageRepository images, MediaService media, AuditService audit) { this.images = images; this.media = media; this.audit = audit; }

    @Transactional(readOnly = true)
    public List<SiteImageView> published(SiteImage.Slot slot) { return images.findBySlotAndActiveTrueOrderBySortOrderAscCreatedAtAsc(slot).stream().map(this::view).toList(); }
    @Transactional(readOnly = true)
    public List<SiteImageView> all(SiteImage.Slot slot) { return images.findBySlotOrderBySortOrderAscCreatedAtAsc(slot).stream().map(this::view).toList(); }

    @Transactional
    public SiteImageView upload(SiteImage.Slot slot, MultipartFile file, String altText) {
        if (!StringUtils.hasText(altText)) throw new IllegalArgumentException("Describe the image for screen-reader users (alternative text)");
        MediaFile stored = media.store(file, MediaPurpose.SITE_IMAGE, MediaFile.Visibility.PUBLIC, MediaService.IMAGES);
        SiteImage image = new SiteImage(slot, stored.getId(), actor());
        image.update(altText.trim(), null, null, null, images.findBySlotOrderBySortOrderAscCreatedAtAsc(slot).size(), false);
        images.saveAndFlush(image);
        audit.record("SITE_IMAGE_UPLOADED", image.getId().toString(), slot + " (inactive until activated)");
        return view(image);
    }

    @Transactional
    public SiteImageView update(UUID id, SaveSiteImageRequest r) {
        SiteImage image = images.findById(id).orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        if (image.getVersion() != r.version()) throw new ConflictException("This image changed. Reload before saving.");
        boolean wasActive = image.isActive();
        image.update(r.altText().trim(), trim(r.title()), trim(r.subtitle()), trim(r.body()), r.sortOrder(), r.active());
        images.flush();
        if (wasActive != r.active()) audit.record(r.active() ? "SITE_IMAGE_ACTIVATED" : "SITE_IMAGE_DEACTIVATED", id.toString(), image.getSlot().name());
        return view(image);
    }

    @Transactional
    public void delete(UUID id) {
        SiteImage image = images.findById(id).orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        images.delete(image);
        media.delete(image.getMediaId(), "Website image removed");
    }

    private SiteImageView view(SiteImage i) {
        return new SiteImageView(i.getId(), i.getVersion(), i.getSlot(), "/api/v1/public/media/" + i.getMediaId(), i.getAltText(), i.getTitle(),
            i.getSubtitle(), i.getBody(), i.getSortOrder(), i.isActive());
    }
    private static String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
