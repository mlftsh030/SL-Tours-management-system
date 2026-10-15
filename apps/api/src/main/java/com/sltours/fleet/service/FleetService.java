package com.sltours.fleet.service;
import com.sltours.audit.AuditService;
import com.sltours.fleet.dto.FleetDtos.*;
import com.sltours.fleet.model.*;
import com.sltours.fleet.repository.*;
import com.sltours.media.*;
import com.sltours.shared.error.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FleetService {
    private final VehicleRepository vehicles;
    private final VehicleRestrictionRepository restrictions;
    private final VehicleImageRepository images;
    private final MediaService media;
    private final AuditService audit;
    private final com.sltours.assignments.service.AdminAssignmentService assignments;
    private final com.sltours.privatetrips.service.VehicleOptionService options;
    public FleetService(VehicleRepository vehicles, VehicleRestrictionRepository restrictions, VehicleImageRepository images,
            MediaService media, AuditService audit, com.sltours.assignments.service.AdminAssignmentService assignments,
            com.sltours.privatetrips.service.VehicleOptionService options) {
        this.vehicles = vehicles; this.restrictions = restrictions; this.images = images; this.media = media; this.audit = audit;
        this.assignments = assignments; this.options = options;
    }

    @Transactional(readOnly = true)
    public List<VehicleSummary> list() {
        List<Vehicle> all = vehicles.findAllByOrderByDisplayNameAsc();
        Map<UUID, String> firstImage = firstImageUrls(all.stream().map(Vehicle::getId).toList());
        return all.stream().map(v -> new VehicleSummary(v.getId(), v.getRegistration(), v.getDisplayName(), v.getCategory(), v.getCapacity(),
            v.getLuggageCapacity(), v.getOperationalStatus(), v.getListingStatus(),
            restrictions.findByVehicleIdAndClearedAtIsNull(v.getId()).stream().map(FleetService::restriction).toList(),
            firstImage.get(v.getId()), v.getHourlyRateCents(), v.isDemoData())).toList();
    }

    @Transactional(readOnly = true)
    public VehicleDetail get(UUID id) { return detail(find(id)); }

    @Transactional
    public VehicleDetail create(SaveVehicleRequest request) {
        String registration = normalizeRegistration(request.registration());
        if (vehicles.existsByRegistrationIgnoreCase(registration)) throw new ConflictException("A vehicle with this registration already exists");
        Vehicle vehicle = new Vehicle();
        apply(vehicle, request, registration);
        vehicles.saveAndFlush(vehicle);
        audit.record("VEHICLE_CREATED", vehicle.getId().toString(), registration + " " + vehicle.getCategory());
        return detail(vehicle);
    }

    @Transactional
    public VehicleDetail update(UUID id, SaveVehicleRequest request) {
        Vehicle vehicle = find(id);
        if (vehicle.getVersion() != request.version()) throw new ConflictException("This vehicle changed. Reload before saving.");
        String registration = normalizeRegistration(request.registration());
        if (vehicles.existsByRegistrationIgnoreCaseAndIdNot(registration, id)) throw new ConflictException("A vehicle with this registration already exists");
        String before = vehicle.getOperationalStatus() + "/" + vehicle.getListingStatus() + "/rate=" + vehicle.getHourlyRateCents();
        apply(vehicle, request, registration);
        vehicles.flush();
        audit.record("VEHICLE_UPDATED", id.toString(), before + " -> " + vehicle.getOperationalStatus() + "/" + vehicle.getListingStatus()
            + "/rate=" + vehicle.getHourlyRateCents());
        return detail(vehicle);
    }

    /** Confirms that a row flagged as V2 sample data is in fact a real vehicle. */
    @Transactional
    public VehicleDetail markGenuine(UUID id) {
        Vehicle vehicle = find(id);
        if (!vehicle.isDemoData()) throw new IllegalArgumentException("This vehicle is not flagged as sample data");
        vehicle.setDemoData(false);
        vehicles.flush();
        audit.record("SAMPLE_FLAG_REMOVED", id.toString(), "vehicle " + vehicle.getRegistration() + " confirmed as genuine");
        return detail(vehicle);
    }

    @Transactional
    public VehicleDetail addImage(UUID id, MultipartFile file, String altText) {
        Vehicle vehicle = find(id);
        if (!StringUtils.hasText(altText)) throw new IllegalArgumentException("Describe the photo for screen-reader users (alternative text)");
        MediaFile stored = media.store(file, MediaPurpose.VEHICLE_IMAGE, MediaFile.Visibility.PUBLIC, MediaService.IMAGES);
        int order = images.findByVehicleIdOrderBySortOrderAscCreatedAtAsc(id).size();
        images.save(new VehicleImage(id, stored.getId(), altText.trim(), order));
        audit.record("VEHICLE_IMAGE_ADDED", id.toString(), "media=" + stored.getId());
        return detail(vehicle);
    }

    @Transactional
    public VehicleDetail updateImage(UUID id, UUID imageId, ImageUpdateRequest request) {
        VehicleImage image = image(id, imageId);
        image.setAltText(request.altText().trim()); image.setSortOrder(request.sortOrder());
        images.flush();
        return detail(find(id));
    }

    @Transactional
    public VehicleDetail removeImage(UUID id, UUID imageId) {
        VehicleImage image = image(id, imageId);
        images.delete(image);
        media.delete(image.getMediaId(), "Vehicle photo removed");
        return detail(find(id));
    }

    private VehicleImage image(UUID vehicleId, UUID imageId) {
        return images.findById(imageId).filter(i -> i.getVehicleId().equals(vehicleId))
            .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
    }

    private void apply(Vehicle v, SaveVehicleRequest r, String registration) {
        if (r.minimumMinutes() != null && r.hourlyRateCents() == null)
            throw new IllegalArgumentException("Set a vehicle hourly rate before overriding its minimum duration");
        v.setRegistration(registration); v.setDisplayName(r.displayName().trim()); v.setCategory(r.category());
        v.setCapacity(r.capacity()); v.setLuggageCapacity(r.luggageCapacity());
        v.setOperationalStatus(r.operationalStatus()); v.setListingStatus(r.listingStatus());
        v.setPublicDescription(trim(r.publicDescription())); v.setOperatingNotes(trim(r.operatingNotes())); v.setNotes(trim(r.notes()));
        v.setHourlyRateCents(r.hourlyRateCents()); v.setMinimumMinutes(r.minimumMinutes());
        v.setAmenities(r.amenities().stream().map(String::trim).distinct().toList());
        String model = trim(r.vehicleOptionSlug());
        if (model != null) {
            var option = options.find(model).orElseThrow(() -> new IllegalArgumentException("Choose a published vehicle model"));
            if (option.category() != r.category())
                throw new IllegalArgumentException("The " + option.name() + " is listed as " + option.category().name().replace('_', ' ').toLowerCase(Locale.ROOT)
                    + "; set the same category or choose another model");
        }
        v.setVehicleOptionSlug(model);
    }

    public Map<UUID, String> firstImageUrls(Collection<UUID> vehicleIds) {
        Map<UUID, String> urls = new HashMap<>();
        if (vehicleIds.isEmpty()) return urls;
        for (VehicleImage image : images.findByVehicleIdInOrderBySortOrderAscCreatedAtAsc(vehicleIds))
            urls.putIfAbsent(image.getVehicleId(), publicUrl(image.getMediaId()));
        return urls;
    }
    public List<Image> imagesFor(UUID vehicleId) {
        return images.findByVehicleIdOrderBySortOrderAscCreatedAtAsc(vehicleId).stream()
            .map(i -> new Image(i.getId(), i.getMediaId(), publicUrl(i.getMediaId()), i.getAltText(), i.getSortOrder())).toList();
    }
    public static String publicUrl(UUID mediaId) { return "/api/v1/public/media/" + mediaId; }

    private VehicleDetail detail(Vehicle v) {
        return new VehicleDetail(v.getId(), v.getVersion(), v.getRegistration(), v.getDisplayName(), v.getCategory(), v.getCapacity(),
            v.getLuggageCapacity(), v.getOperationalStatus(), v.getListingStatus(), v.getPublicDescription(), v.getOperatingNotes(),
            v.getNotes(), v.getHourlyRateCents(), v.getMinimumMinutes(), List.copyOf(v.getAmenities()), imagesFor(v.getId()),
            restrictions.findByVehicleIdOrderByOpenedAtDesc(v.getId()).stream().map(FleetService::restriction).toList(), v.isDemoData(),
            v.getVehicleOptionSlug(),
            assignments.upcomingForVehicle(v.getId()));
    }
    public static Restriction restriction(VehicleRestriction r) {
        return new Restriction(r.getId(), r.getVersion(), r.getType(), r.getReason(), r.getSourceInspectionId(), r.getOpenedBy(),
            r.getOpenedAt(), r.getClearedBy(), r.getClearedAt(), r.getClearanceNote());
    }
    public Vehicle find(UUID id) { return vehicles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vehicle not found")); }
    private static String normalizeRegistration(String value) { return value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private static String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
