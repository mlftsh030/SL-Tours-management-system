package com.sltours.fleet.service;
import com.sltours.audit.AuditService;
import com.sltours.fleet.model.VehicleRestriction;
import com.sltours.fleet.repository.*;
import com.sltours.shared.error.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opening is routine; clearing is a deliberate administrator action with a recorded justification. */
@Service
public class RestrictionService {
    private final VehicleRestrictionRepository restrictions;
    private final VehicleRepository vehicles;
    private final AuditService audit;
    public RestrictionService(VehicleRestrictionRepository restrictions, VehicleRepository vehicles, AuditService audit) {
        this.restrictions = restrictions; this.vehicles = vehicles; this.audit = audit;
    }
    @Transactional
    public VehicleRestriction open(UUID vehicleId, VehicleRestriction.Type type, String reason, UUID sourceInspectionId) {
        if (!vehicles.existsById(vehicleId)) throw new ResourceNotFoundException("Vehicle not found");
        VehicleRestriction restriction = restrictions.save(new VehicleRestriction(vehicleId, type, reason.trim(), sourceInspectionId, actor()));
        audit.record("VEHICLE_RESTRICTION_OPENED", vehicleId.toString(), type + ": " + abbreviate(reason));
        return restriction;
    }
    @Transactional
    public VehicleRestriction clear(UUID vehicleId, UUID restrictionId, long version, String note) {
        VehicleRestriction restriction = restrictions.findById(restrictionId).filter(r -> r.getVehicleId().equals(vehicleId))
            .orElseThrow(() -> new ResourceNotFoundException("Restriction not found"));
        if (restriction.getVersion() != version) throw new ConflictException("This restriction changed. Reload before saving.");
        if (!restriction.isOpen()) throw new IllegalArgumentException("This restriction is already cleared");
        if (note == null || note.trim().length() < 10)
            throw new IllegalArgumentException("Describe the repair or check that makes the vehicle safe (at least 10 characters)");
        restriction.clear(actor(), note.trim());
        restrictions.flush();
        audit.record("VEHICLE_RESTRICTION_CLEARED", vehicleId.toString(), restriction.getType() + " " + restrictionId + ": " + abbreviate(note));
        return restriction;
    }
    public boolean isRestricted(UUID vehicleId) { return restrictions.existsByVehicleIdAndClearedAtIsNull(vehicleId); }
    private static String abbreviate(String text) { String t = text.trim(); return t.length() > 400 ? t.substring(0, 400) + "…" : t; }
    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
