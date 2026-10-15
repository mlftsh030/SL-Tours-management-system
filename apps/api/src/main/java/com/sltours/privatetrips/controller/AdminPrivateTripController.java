package com.sltours.privatetrips.controller;
import com.sltours.fleet.model.VehicleCategory;
import com.sltours.privatetrips.dto.PrivateTripDtos.*;
import com.sltours.privatetrips.service.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPrivateTripController {
    private final PrivateTripAdminService trips;
    private final PricingAdminService pricing;
    public AdminPrivateTripController(PrivateTripAdminService trips, PricingAdminService pricing) { this.trips = trips; this.pricing = pricing; }
    @GetMapping("/private-trips/{reference}") AdminPrivateTrip get(@PathVariable String reference) { return trips.get(reference); }
    @PostMapping("/private-trips/{reference}/confirm") @PreAuthorize("hasRole('ADMIN')")
    AdminPrivateTrip confirm(@PathVariable String reference, @Valid @RequestBody ConfirmRequest request) { return trips.confirm(reference, request); }
    @PostMapping("/enquiries/{reference}/convert-private-trip") @PreAuthorize("hasRole('ADMIN')")
    AdminPrivateTrip convert(@PathVariable String reference, @Valid @RequestBody ConvertEnquiryToTripRequest request) {
        return trips.convertEnquiry(reference, request);
    }
    @GetMapping("/pricing") PricingView pricing() { return pricing.get(); }
    @PutMapping("/pricing/settings") @PreAuthorize("hasRole('ADMIN')")
    PricingView settings(@Valid @RequestBody SaveSettingsRequest request) { return pricing.saveSettings(request); }
    @PutMapping("/pricing/rates/{category}") @PreAuthorize("hasRole('ADMIN')")
    PricingView rate(@PathVariable VehicleCategory category, @Valid @RequestBody SaveRateRequest request) { return pricing.saveRate(category, request); }
    @PostMapping("/pricing/fees") @PreAuthorize("hasRole('ADMIN')")
    PricingView createFee(@Valid @RequestBody SaveFeeRequest request) { return pricing.saveFee(null, request); }
    @PutMapping("/pricing/fees/{id}") @PreAuthorize("hasRole('ADMIN')")
    PricingView updateFee(@PathVariable UUID id, @Valid @RequestBody SaveFeeRequest request) { return pricing.saveFee(id, request); }
}
