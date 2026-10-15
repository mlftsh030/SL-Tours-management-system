package com.sltours.fleet.controller;
import com.sltours.fleet.dto.FleetDtos.*;
import com.sltours.fleet.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Staff can view the fleet and report problems; changing vehicles or clearing restrictions is administrator-only. */
@RestController
@RequestMapping("/api/v1/admin/vehicles")
public class AdminFleetController {
    private final FleetService fleet;
    private final RestrictionService restrictions;
    public AdminFleetController(FleetService fleet, RestrictionService restrictions) { this.fleet = fleet; this.restrictions = restrictions; }
    @GetMapping List<VehicleSummary> list() { return fleet.list(); }
    @GetMapping("/{id}") VehicleDetail get(@PathVariable UUID id) { return fleet.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    VehicleDetail create(@Valid @RequestBody SaveVehicleRequest request) { return fleet.create(request); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    VehicleDetail update(@PathVariable UUID id, @Valid @RequestBody SaveVehicleRequest request) { return fleet.update(id, request); }
    @PostMapping("/{id}/genuine") @PreAuthorize("hasRole('ADMIN')")
    VehicleDetail markGenuine(@PathVariable UUID id) { return fleet.markGenuine(id); }
    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('ADMIN')")
    VehicleDetail addImage(@PathVariable UUID id, @RequestPart("file") MultipartFile file, @RequestParam String altText) {
        return fleet.addImage(id, file, altText);
    }
    @PatchMapping("/{id}/images/{imageId}") @PreAuthorize("hasRole('ADMIN')")
    VehicleDetail updateImage(@PathVariable UUID id, @PathVariable UUID imageId, @Valid @RequestBody ImageUpdateRequest request) {
        return fleet.updateImage(id, imageId, request);
    }
    @DeleteMapping("/{id}/images/{imageId}") @PreAuthorize("hasRole('ADMIN')")
    VehicleDetail removeImage(@PathVariable UUID id, @PathVariable UUID imageId) { return fleet.removeImage(id, imageId); }
    @PostMapping("/{id}/restrictions")
    VehicleDetail openRestriction(@PathVariable UUID id, @Valid @RequestBody OpenRestrictionRequest request) {
        restrictions.open(id, request.type(), request.reason(), null);
        return fleet.get(id);
    }
    @PostMapping("/{id}/restrictions/{restrictionId}/clear") @PreAuthorize("hasRole('ADMIN')")
    VehicleDetail clearRestriction(@PathVariable UUID id, @PathVariable UUID restrictionId, @Valid @RequestBody ClearRestrictionRequest request) {
        restrictions.clear(id, restrictionId, request.version(), request.note());
        return fleet.get(id);
    }
}
