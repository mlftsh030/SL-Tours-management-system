package com.sltours.inspections.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.assignments.model.*;
import com.sltours.assignments.repository.AssignmentRepository;
import com.sltours.assignments.service.AssignmentService;
import com.sltours.audit.AuditService;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import com.sltours.drivers.service.DriverEligibility;
import com.sltours.fleet.model.*;
import com.sltours.fleet.repository.VehicleRepository;
import com.sltours.fleet.service.RestrictionService;
import com.sltours.inspections.dto.InspectionDtos.*;
import com.sltours.inspections.model.*;
import com.sltours.inspections.repository.*;
import com.sltours.media.*;
import com.sltours.shared.error.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Collection and return inspections. Rules enforced here:
 * <ul>
 *   <li>drivers act only on their own assignments and must be eligible on the trip date;</li>
 *   <li>a restricted or out-of-service vehicle cannot be collected;</li>
 *   <li>a vehicle still out on another trip (collected and not yet returned) cannot be collected or released again;</li>
 *   <li>collection opens a set time before the trip starts, so the fleet status matches where vehicles really are;</li>
 *   <li>safety defects open a SAFETY restriction and are never released, whatever the approval policy;</li>
 *   <li>a return needs a live collection and an odometer reading at or above it;</li>
 *   <li>approving an inspection never clears a restriction.</li>
 * </ul>
 */
@Service
public class InspectionService {
    private static final String VEHICLE_TAG = "VEHICLE";
    /** A vehicle is out once a collection is submitted, until its return is recorded. */
    private static final Set<AssignmentStatus> VEHICLE_OUT = EnumSet.of(AssignmentStatus.AWAITING_RELEASE, AssignmentStatus.RELEASED);
    private static final DateTimeFormatter OPENS_AT = DateTimeFormatter.ofPattern("HH:mm 'on' EEE d MMM yyyy", Locale.ENGLISH);
    private final InspectionRepository inspections;
    private final InspectionPhotoRepository photos;
    private final AssignmentRepository assignments;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final AccountRepository accounts;
    private final BookingRepository bookings;
    private final DriverEligibility eligibility;
    private final RestrictionService restrictions;
    private final MediaService media;
    private final AuditService audit;
    private final ObjectMapper json;
    private final boolean approvalRequired;
    private final int minimumVehiclePhotos;
    private final Duration collectionOpensBefore;

    public InspectionService(InspectionRepository inspections, InspectionPhotoRepository photos, AssignmentRepository assignments,
            VehicleRepository vehicles, DriverRepository drivers, AccountRepository accounts, BookingRepository bookings,
            DriverEligibility eligibility, RestrictionService restrictions, MediaService media, AuditService audit, ObjectMapper json,
            @Value("${sltours.inspections.collection-approval-required:true}") boolean approvalRequired,
            @Value("${sltours.inspections.min-vehicle-photos:4}") int minimumVehiclePhotos,
            @Value("${sltours.inspections.collection-opens-before:12h}") Duration collectionOpensBefore) {
        this.inspections = inspections; this.photos = photos; this.assignments = assignments; this.vehicles = vehicles;
        this.drivers = drivers; this.accounts = accounts; this.bookings = bookings; this.eligibility = eligibility;
        this.restrictions = restrictions; this.media = media; this.audit = audit; this.json = json;
        this.approvalRequired = approvalRequired; this.minimumVehiclePhotos = minimumVehiclePhotos;
        this.collectionOpensBefore = collectionOpensBefore;
    }

    public boolean isApprovalRequired() { return approvalRequired; }
    public int minimumVehiclePhotos() { return minimumVehiclePhotos; }

    /** Null when collection is open; otherwise when it opens, for the driver. */
    public String collectionNotYetOpen(Assignment assignment) {
        OffsetDateTime opens = assignment.getStartsAt().minus(collectionOpensBefore);
        if (!OffsetDateTime.now().isBefore(opens)) return null;
        return "Collection opens at " + opens.atZoneSameInstant(AssignmentService.OPERATING_ZONE).format(OPENS_AT) + ", "
            + collectionOpensBefore.toHours() + " hours before the trip.";
    }

    /** The booking reference of another trip that still has this assignment's vehicle out, if any. */
    public Optional<String> vehicleStillOutOn(Assignment assignment, Set<AssignmentStatus> outStatuses) {
        if (assignment.getVehicleId() == null) return Optional.empty();
        return assignments.findByVehicleIdAndStatusInAndIdNot(assignment.getVehicleId(), outStatuses, assignment.getId()).stream()
            .min(Comparator.comparing(Assignment::getStartsAt))
            .map(other -> bookings.findById(other.getBookingId()).map(b -> b.getReference()).orElse("another trip"));
    }

    /** Driver-facing reason the vehicle cannot be collected because it has not come back from another trip. */
    public Optional<String> vehicleNotReturned(Assignment assignment) {
        return vehicleStillOutOn(assignment, VEHICLE_OUT)
            .map(reference -> "This vehicle has not been returned from trip " + reference + " yet. Contact the office before collecting it.");
    }
    public static List<ChecklistDefinition> checklist() {
        return Arrays.stream(ChecklistItem.values()).map(i -> new ChecklistDefinition(i, i.label, i.safetyCritical)).toList();
    }

    @Transactional
    public SubmissionResult submitCollection(Driver driver, UUID assignmentId, InspectionForm form, List<MultipartFile> files, MultipartFile selfie) {
        Assignment assignment = ownAssignment(driver, assignmentId);
        if (assignment.getStatus() != AssignmentStatus.SCHEDULED)
            throw new IllegalArgumentException(assignment.getStatus() == AssignmentStatus.CANCELLED
                ? "This assignment was cancelled" : "A collection inspection has already been recorded for this assignment");
        if (inspections.findLive(assignmentId, Inspection.Type.COLLECTION).isPresent())
            throw new ConflictException("A collection inspection is already waiting for review");
        eligibility.require(driver, accountEnabled(driver), assignment.getStartsAt().atZoneSameInstant(AssignmentService.OPERATING_ZONE).toLocalDate());
        Vehicle vehicle = vehicle(assignment);
        if (vehicle.getOperationalStatus() != Vehicle.OperationalStatus.ACTIVE)
            throw new IllegalArgumentException("This vehicle is out of service. Do not take it; contact the office.");
        if (restrictions.isRestricted(vehicle.getId()))
            throw new IllegalArgumentException("This vehicle has an open safety or maintenance restriction. Do not take it; contact the office.");
        String notYet = collectionNotYetOpen(assignment);
        if (notYet != null) throw new IllegalArgumentException(notYet);
        vehicleNotReturned(assignment).ifPresent(reason -> { throw new ConflictException(reason); });
        if (selfie == null || selfie.isEmpty()) throw new IllegalArgumentException("Take a fresh collection selfie before collecting the vehicle");
        validate(form, files);

        MediaFile selfieFile = media.store(selfie, MediaPurpose.INSPECTION_SELFIE, MediaFile.Visibility.PRIVATE, MediaService.IMAGES);
        Inspection inspection = record(driver, assignment, Inspection.Type.COLLECTION, form, files, selfieFile.getId());
        String outcome;
        if (inspection.isHasSafetyDefect()) {
            restrictions.open(vehicle.getId(), VehicleRestriction.Type.SAFETY, "Safety defect reported at collection: " + defectSummary(form, true), inspection.getId());
            outcome = "Safety defect recorded. Do not take this vehicle. The office has been alerted and will arrange a replacement.";
        } else if (approvalRequired) {
            assignment.setStatus(AssignmentStatus.AWAITING_RELEASE);
            outcome = "Collection inspection submitted. Wait for the office to approve the release before driving.";
        } else {
            assignment.release("policy: approval not required");
            outcome = "Collection inspection recorded. The vehicle is released to you.";
        }
        assignments.flush();
        audit.record("INSPECTION_SUBMITTED", inspection.getId().toString(), "COLLECTION assignment=" + assignmentId
            + "; defect=" + inspection.isHasDefect() + "; safety=" + inspection.isHasSafetyDefect());
        return new SubmissionResult(inspection.getId(), Inspection.Type.COLLECTION, assignment.getStatus(), outcome);
    }

    @Transactional
    public SubmissionResult submitReturn(Driver driver, UUID assignmentId, InspectionForm form, List<MultipartFile> files) {
        Assignment assignment = ownAssignment(driver, assignmentId);
        Inspection collection = inspections.findLive(assignmentId, Inspection.Type.COLLECTION)
            .orElseThrow(() -> new IllegalArgumentException("No collection inspection is recorded for this assignment. A return needs a collection first."));
        if (assignment.getStatus() != AssignmentStatus.RELEASED)
            throw new IllegalArgumentException(assignment.getStatus() == AssignmentStatus.RETURNED || assignment.getStatus() == AssignmentStatus.CLOSED
                ? "A return inspection has already been recorded for this assignment"
                : "This vehicle has not been released to you yet, so it cannot be returned");
        if (inspections.findLive(assignmentId, Inspection.Type.RETURN).isPresent())
            throw new ConflictException("A return inspection has already been recorded for this assignment");
        if (form.odometerKm() < collection.getOdometerKm())
            throw new IllegalArgumentException("Return odometer (" + form.odometerKm() + " km) cannot be lower than at collection ("
                + collection.getOdometerKm() + " km)");
        validate(form, files);

        Inspection inspection = record(driver, assignment, Inspection.Type.RETURN, form, files, null);
        assignment.setStatus(AssignmentStatus.RETURNED);
        // Newly reported damage keeps the vehicle out of service until an administrator clears it.
        if (inspection.isHasSafetyDefect())
            restrictions.open(assignment.getVehicleId(), VehicleRestriction.Type.SAFETY, "Safety defect reported at return: " + defectSummary(form, true), inspection.getId());
        else if (inspection.isHasDefect())
            restrictions.open(assignment.getVehicleId(), VehicleRestriction.Type.MAINTENANCE, "Defect reported at return: " + defectSummary(form, false), inspection.getId());
        assignments.flush();
        audit.record("INSPECTION_SUBMITTED", inspection.getId().toString(), "RETURN assignment=" + assignmentId
            + "; distanceKm=" + (form.odometerKm() - collection.getOdometerKm()) + "; defect=" + inspection.isHasDefect());
        return new SubmissionResult(inspection.getId(), Inspection.Type.RETURN, assignment.getStatus(),
            inspection.isHasDefect() ? "Return recorded. The reported defects have been sent to the office." : "Return recorded. Thank you.");
    }

    @Transactional
    public InspectionView approve(UUID inspectionId, ReviewRequest request) {
        Inspection inspection = pending(inspectionId, request.version());
        Assignment assignment = assignments.findById(inspection.getAssignmentId()).orElseThrow();
        if (inspection.getType() == Inspection.Type.COLLECTION) {
            if (inspection.isHasSafetyDefect())
                throw new IllegalArgumentException("A collection with a safety defect cannot be approved. Reject it, resolve the defect and ask the driver to re-inspect, or assign another vehicle.");
            if (restrictions.isRestricted(inspection.getVehicleId()))
                throw new IllegalArgumentException("The vehicle has an open restriction. Approval does not clear restrictions; clear it through the vehicle's maintenance action first.");
            Vehicle vehicle = vehicle(assignment);
            if (vehicle.getOperationalStatus() != Vehicle.OperationalStatus.ACTIVE) throw new IllegalArgumentException("The vehicle is not in operational service");
            vehicleStillOutOn(assignment, EnumSet.of(AssignmentStatus.RELEASED)).ifPresent(reference -> {
                throw new ConflictException("This vehicle is still released on trip " + reference
                    + ". Record and approve that return first, or reject this collection and assign another vehicle.");
            });
            Driver driver = drivers.findById(inspection.getDriverId()).orElseThrow();
            eligibility.require(driver, accountEnabled(driver), assignment.getStartsAt().atZoneSameInstant(AssignmentService.OPERATING_ZONE).toLocalDate());
            if (assignment.getStatus() == AssignmentStatus.AWAITING_RELEASE) assignment.release(actor());
            else if (assignment.getStatus() != AssignmentStatus.RELEASED) throw new IllegalArgumentException("This assignment is " + assignment.getStatus());
        } else {
            if (assignment.getStatus() != AssignmentStatus.RETURNED) throw new IllegalArgumentException("This assignment is " + assignment.getStatus());
            assignment.setStatus(AssignmentStatus.CLOSED);
        }
        inspection.review(Inspection.ReviewStatus.APPROVED, trim(request.notes()), actor());
        assignments.flush();
        audit.record("INSPECTION_APPROVED", inspectionId.toString(), inspection.getType() + " assignment=" + assignment.getId() + " -> " + assignment.getStatus());
        return view(inspection);
    }

    @Transactional
    public InspectionView reject(UUID inspectionId, ReviewRequest request) {
        Inspection inspection = pending(inspectionId, request.version());
        if (!StringUtils.hasText(request.notes())) throw new IllegalArgumentException("Explain why the inspection is rejected");
        Assignment assignment = assignments.findById(inspection.getAssignmentId()).orElseThrow();
        if (inspection.getType() == Inspection.Type.COLLECTION) {
            if (assignment.getStatus() == AssignmentStatus.RELEASED)
                throw new IllegalArgumentException("The vehicle was already released under the current approval policy. Open a restriction instead.");
            if (assignment.getStatus() == AssignmentStatus.AWAITING_RELEASE) assignment.setStatus(AssignmentStatus.SCHEDULED);
        } else if (assignment.getStatus() == AssignmentStatus.RETURNED) {
            assignment.setStatus(AssignmentStatus.RELEASED);
        }
        inspection.review(Inspection.ReviewStatus.REJECTED, request.notes().trim(), actor());
        assignments.flush();
        audit.record("INSPECTION_REJECTED", inspectionId.toString(), inspection.getType() + ": " + request.notes().trim());
        return view(inspection);
    }

    @Transactional(readOnly = true)
    public Comparison compare(UUID assignmentId) {
        Assignment assignment = assignments.findById(assignmentId).orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        Optional<Inspection> collection = inspections.findLive(assignmentId, Inspection.Type.COLLECTION);
        Optional<Inspection> returned = inspections.findLive(assignmentId, Inspection.Type.RETURN);
        List<ChecklistLine> newDefects = new ArrayList<>(), persisting = new ArrayList<>();
        Integer distance = null, fuelChange = null;
        if (collection.isPresent() && returned.isPresent()) {
            Map<ChecklistItem, ChecklistLine> before = lines(collection.get()).stream().collect(Collectors.toMap(ChecklistLine::item, l -> l));
            for (ChecklistLine line : lines(returned.get())) {
                if (line.result() != ChecklistAnswer.Result.DEFECT) continue;
                ChecklistLine previous = before.get(line.item());
                (previous != null && previous.result() == ChecklistAnswer.Result.DEFECT ? persisting : newDefects).add(line);
            }
            distance = returned.get().getOdometerKm() - collection.get().getOdometerKm();
            fuelChange = returned.get().getFuelLevel().percent - collection.get().getFuelLevel().percent;
        }
        return new Comparison(assignmentId, assignment.getStatus(), collection.map(this::view).orElse(null),
            returned.map(this::view).orElse(null), distance, fuelChange, newDefects, persisting);
    }

    @Transactional(readOnly = true)
    public List<PendingReview> pendingReviews() {
        return inspections.findByReviewStatusOrderBySubmittedAtAsc(Inspection.ReviewStatus.PENDING_REVIEW).stream().map(i -> {
            Assignment a = assignments.findById(i.getAssignmentId()).orElseThrow();
            return new PendingReview(i.getId(), i.getType(), a.getId(), bookings.findById(a.getBookingId()).map(b -> b.getReference()).orElse("—"),
                drivers.findById(i.getDriverId()).map(Driver::getName).orElse("—"), vehicles.findById(i.getVehicleId()).map(Vehicle::getDisplayName).orElse("—"),
                i.getSubmittedAt(), i.isHasDefect(), i.isHasSafetyDefect());
        }).toList();
    }

    @Transactional(readOnly = true)
    public InspectionView get(UUID inspectionId) {
        return view(inspections.findById(inspectionId).orElseThrow(() -> new ResourceNotFoundException("Inspection not found")));
    }

    /** True when the media belongs to an inspection the driver made on an assignment still assigned to them. */
    @Transactional(readOnly = true)
    public boolean driverMayView(Driver driver, UUID mediaId) {
        Optional<Inspection> inspection = inspections.findBySelfieMediaId(mediaId)
            .or(() -> photos.findByMediaId(mediaId).flatMap(p -> inspections.findById(p.getInspectionId())));
        return inspection.filter(i -> i.getDriverId().equals(driver.getId()))
            .flatMap(i -> assignments.findByIdAndDriverId(i.getAssignmentId(), driver.getId())).isPresent();
    }

    /** Driver-facing summary: submission state only, never the administrator's review notes. */
    @Transactional(readOnly = true)
    public Optional<Inspection> live(UUID assignmentId, Inspection.Type type) { return inspections.findLive(assignmentId, type); }

    private Inspection record(Driver driver, Assignment assignment, Inspection.Type type, InspectionForm form, List<MultipartFile> files, UUID selfieId) {
        boolean defect = form.checklist().stream().anyMatch(ChecklistAnswer::isDefect);
        boolean safety = form.checklist().stream().anyMatch(ChecklistAnswer::isSafetyDefect);
        Inspection inspection = new Inspection(assignment.getId(), type, driver.getId(), assignment.getVehicleId(), actor());
        inspection.record(form.odometerKm(), form.fuelLevel(), trim(form.notes()), checklistJson(form.checklist()), defect, safety, selfieId);
        inspections.saveAndFlush(inspection);
        for (int i = 0; i < files.size(); i++) {
            String tag = form.photoTags().get(i);
            MediaFile stored = media.store(files.get(i), MediaPurpose.INSPECTION_PHOTO, MediaFile.Visibility.PRIVATE, MediaService.IMAGES);
            boolean vehiclePhoto = VEHICLE_TAG.equals(tag);
            photos.save(new InspectionPhoto(inspection.getId(), stored.getId(), vehiclePhoto ? InspectionPhoto.Kind.VEHICLE : InspectionPhoto.Kind.DEFECT,
                vehiclePhoto ? null : ChecklistItem.valueOf(tag), i));
        }
        return inspection;
    }

    private void validate(InspectionForm form, List<MultipartFile> files) {
        Set<ChecklistItem> answered = EnumSet.noneOf(ChecklistItem.class);
        for (ChecklistAnswer answer : form.checklist()) {
            if (!answered.add(answer.item())) throw new IllegalArgumentException("Checklist item answered twice: " + answer.item().label);
            if (answer.isDefect() && !StringUtils.hasText(answer.note())) throw new IllegalArgumentException("Describe the defect for: " + answer.item().label);
            if (answer.isDefect() && answer.severity() == null && !answer.item().safetyCritical)
                throw new IllegalArgumentException("Choose minor or safety for: " + answer.item().label);
        }
        EnumSet<ChecklistItem> missing = EnumSet.allOf(ChecklistItem.class); missing.removeAll(answered);
        if (!missing.isEmpty()) throw new IllegalArgumentException("Complete every checklist item. Missing: "
            + missing.stream().map(i -> i.label).collect(Collectors.joining(", ")));
        if (files.size() != form.photoTags().size()) throw new IllegalArgumentException("Each photo needs exactly one tag");
        for (String tag : form.photoTags()) {
            if (!VEHICLE_TAG.equals(tag) && Arrays.stream(ChecklistItem.values()).noneMatch(i -> i.name().equals(tag)))
                throw new IllegalArgumentException("Unknown photo tag: " + tag);
        }
        long overview = form.photoTags().stream().filter(VEHICLE_TAG::equals).count();
        if (overview < minimumVehiclePhotos)
            throw new IllegalArgumentException("Add at least " + minimumVehiclePhotos + " vehicle photos (front, rear, left and right)");
        for (ChecklistAnswer answer : form.checklist())
            if (answer.isDefect() && !form.photoTags().contains(answer.item().name()))
                throw new IllegalArgumentException("Add a photo of the defect: " + answer.item().label);
    }

    private Assignment ownAssignment(Driver driver, UUID assignmentId) {
        // Another driver's assignment is reported as not found so its existence is not revealed.
        return assignments.findByIdAndDriverId(assignmentId, driver.getId()).orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
    }
    private Inspection pending(UUID id, long version) {
        Inspection inspection = inspections.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inspection not found"));
        if (inspection.getVersion() != version) throw new ConflictException("This inspection changed. Reload before reviewing.");
        if (inspection.getReviewStatus() != Inspection.ReviewStatus.PENDING_REVIEW) throw new IllegalArgumentException("This inspection was already reviewed");
        return inspection;
    }
    private Vehicle vehicle(Assignment assignment) {
        if (assignment.getVehicleId() == null) throw new IllegalArgumentException("No vehicle is assigned yet");
        return vehicles.findById(assignment.getVehicleId()).orElseThrow();
    }
    private boolean accountEnabled(Driver driver) {
        return driver.getAccountId() != null && accounts.findById(driver.getAccountId()).map(Account::isEnabled).orElse(false);
    }
    private static String defectSummary(InspectionForm form, boolean safetyOnly) {
        return form.checklist().stream().filter(a -> safetyOnly ? a.isSafetyDefect() : a.isDefect())
            .map(a -> a.item().label + " (" + a.note().trim() + ")").collect(Collectors.joining("; "));
    }
    private String checklistJson(List<ChecklistAnswer> answers) {
        List<ChecklistLine> lines = answers.stream().sorted(Comparator.comparing(ChecklistAnswer::item)).map(a -> new ChecklistLine(a.item(), a.item().label,
            a.result(), a.isDefect() ? (a.isSafetyDefect() ? ChecklistAnswer.Severity.SAFETY : ChecklistAnswer.Severity.MINOR) : null,
            a.isDefect() ? a.note().trim() : trim(a.note()))).toList();
        try { return json.writeValueAsString(lines); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private List<ChecklistLine> lines(Inspection inspection) {
        try { return json.readValue(inspection.getChecklist(), new TypeReference<List<ChecklistLine>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    public InspectionView view(Inspection i) {
        Vehicle vehicle = vehicles.findById(i.getVehicleId()).orElseThrow();
        var photoList = photos.findByInspectionIdOrderBySortOrderAsc(i.getId()).stream()
            .map(p -> new Photo(p.getMediaId(), "/api/v1/admin/media/" + p.getMediaId(), p.getKind(), p.getChecklistItem())).toList();
        return new InspectionView(i.getId(), i.getVersion(), i.getType(), i.getAssignmentId(), i.getDriverId(),
            drivers.findById(i.getDriverId()).map(Driver::getName).orElse("—"), vehicle.getId(), vehicle.getDisplayName(), vehicle.getRegistration(),
            i.getSubmittedBy(), i.getSubmittedAt(), i.getOdometerKm(), i.getFuelLevel(), i.getNotes(), lines(i), i.isHasDefect(), i.isHasSafetyDefect(),
            i.getSelfieMediaId() == null ? null : "/api/v1/admin/media/" + i.getSelfieMediaId(), photoList, i.getReviewStatus(),
            i.getReviewedBy(), i.getReviewedAt(), i.getReviewNotes());
    }
    private static String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
