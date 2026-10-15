package com.sltours.tours.service;
import com.sltours.audit.AuditService;
import com.sltours.shared.error.*;
import com.sltours.tours.dto.*;
import com.sltours.tours.model.Tour;
import com.sltours.tours.repository.TourRepository;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminTourService {
    private final TourRepository tours;
    private final TourMapper mapper;
    private final AuditService audit;
    public AdminTourService(TourRepository tours, TourMapper mapper, AuditService audit) {
        this.tours = tours; this.mapper = mapper; this.audit = audit;
    }
    @Transactional(readOnly = true)
    public Page<AdminTourResponse> list(int page, int size) {
        return tours.findAll(PageRequest.of(page, size, Sort.by("displayOrder", "title", "id"))).map(this::response);
    }
    @Transactional
    public AdminTourResponse create(SaveTourRequest request) {
        validate(request);
        if (tours.existsBySlug(request.slug())) throw new ConflictException("A tour with this URL already exists");
        Tour tour = new Tour(); apply(tour, request); tours.saveAndFlush(tour);
        audit.record("TOUR_CREATED", tour.getId().toString(), "priceCents=" + tour.getBasePriceCents() + "; status=" + tour.getStatus());
        return response(tour);
    }
    @Transactional
    public AdminTourResponse update(UUID id, SaveTourRequest request) {
        validate(request);
        Tour tour = tours.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tour not found"));
        if (tour.getVersion() != request.version().longValue()) throw new ConflictException("This tour changed. Reload it before saving.");
        if (tours.existsBySlugAndIdNot(request.slug(), id)) throw new ConflictException("A tour with this URL already exists");
        int previous = tour.getBasePriceCents(); var previousStatus = tour.getStatus();
        apply(tour, request); tours.flush();
        audit.record("TOUR_UPDATED", id.toString(), "priceCents=" + previous + "->" + tour.getBasePriceCents()
            + "; status=" + previousStatus + "->" + tour.getStatus());
        return response(tour);
    }
    private void validate(SaveTourRequest request) {
        if (request.maximumGuests() < request.minimumGuests()) throw new IllegalArgumentException("Maximum guests must be at least minimum guests");
        validateImage(request.heroImage());
        if (request.bannerImage() != null && !request.bannerImage().isBlank()) validateImage(request.bannerImage());
    }
    private void validateImage(String path) {
        if (path == null || !path.matches("/assets/[a-zA-Z0-9_./-]+\\.(?:png|jpg|jpeg|webp|avif)") || path.contains(".."))
            throw new IllegalArgumentException("Images must use an existing /assets/ image path (PNG, JPEG, WebP or AVIF)");
    }
    private void apply(Tour tour, SaveTourRequest r) {
        tour.setSlug(r.slug()); tour.setTitle(r.title().trim()); tour.setShortDescription(r.shortDescription().trim());
        tour.setFullDescription(r.fullDescription().trim()); tour.setDuration(r.duration().trim());
        tour.setBasePriceCents(r.basePriceCents()); tour.setPricingType(r.pricingType());
        tour.setMinimumGuests(r.minimumGuests()); tour.setMaximumGuests(r.maximumGuests());
        tour.setPickupAvailable(r.pickupAvailable()); tour.setFeatured(r.featured()); tour.setStatus(r.status());
        tour.setCategory(r.category()); tour.setHeroImage(r.heroImage()); tour.setBannerImage(r.bannerImage());
        tour.setSeoTitle(r.seoTitle().trim()); tour.setSeoDescription(r.seoDescription().trim());
        tour.setCardNote(r.cardNote()); tour.setDisplayOrder(r.displayOrder());
        tour.setLocations(r.locations()); tour.setTags(r.tags()); tour.setHighlights(r.highlights());
        tour.setIncluded(r.included()); tour.setExcluded(r.excluded()); tour.setItinerary(r.itinerary()); tour.setFaqs(r.faqs());
        TourExtras x = r.extras();
        if (x != null) {
            if (x.heroImageAlt() == null || x.heroImageAlt().isBlank()) throw new IllegalArgumentException("Describe the tour image (alternative text)");
            tour.setPickupSummary(blankToNull(x.pickupSummary())); tour.setGroupSizeLabel(blankToNull(x.groupSizeLabel()));
            tour.setAvailabilityLabel(blankToNull(x.availabilityLabel())); tour.setTourTypeLabel(blankToNull(x.tourTypeLabel()));
            tour.setHeroImageAlt(x.heroImageAlt().trim()); tour.setItineraryNote(blankToNull(x.itineraryNote()));
            tour.setPracticalInfo(new java.util.ArrayList<>(x.practicalInfo())); tour.setReviewNotes(blankToNull(x.reviewNotes()));
            tour.setSourceUrl(blankToNull(x.sourceUrl())); tour.setEnquiryForm(x.enquiryForm());
        }
    }
    private AdminTourResponse response(Tour tour) {
        return new AdminTourResponse(tour.getId(), tour.getVersion(), tour.getStatus(), tour.getDisplayOrder(), tour.getCardNote(), mapper.toDetail(tour), mapper.toExtras(tour));
    }
    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
