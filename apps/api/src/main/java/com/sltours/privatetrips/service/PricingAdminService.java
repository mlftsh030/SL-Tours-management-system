package com.sltours.privatetrips.service;
import com.sltours.audit.AuditService;
import com.sltours.fleet.model.*;
import com.sltours.fleet.repository.OperationsSettingsRepository;
import com.sltours.privatetrips.dto.PrivateTripDtos.*;
import com.sltours.privatetrips.model.*;
import com.sltours.privatetrips.repository.*;
import com.sltours.shared.error.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rates, fees and pricing rules. Every change is audited; prices already quoted to customers are never altered. */
@Service
public class PricingAdminService {
    private final OperationsSettingsRepository settings;
    private final PrivateTripRateRepository rates;
    private final PrivateTripFeeRepository fees;
    private final AuditService audit;
    private final boolean sampleRatesShown;
    public PricingAdminService(OperationsSettingsRepository settings, PrivateTripRateRepository rates, PrivateTripFeeRepository fees,
            AuditService audit, @Value("${sltours.pricing.allow-sample-rates:false}") boolean sampleRatesShown) {
        this.settings = settings; this.rates = rates; this.fees = fees; this.audit = audit; this.sampleRatesShown = sampleRatesShown;
    }

    @Transactional(readOnly = true)
    public PricingView get() {
        OperationsSettings s = settings.current();
        return new PricingView(new SettingsView(s.getVersion(), s.getTurnaroundMinutes(), s.getBillingIncrementMinutes(), s.getIncludedKmPerHour(),
                s.getExcessKmRateCents(), s.getOvertimeHourlyRateCents(), s.getUpdatedBy(), s.getUpdatedAt()),
            rates.findAll().stream().sorted(Comparator.comparing(PrivateTripRate::getCategory)).map(r -> new RateView(r.getCategory(), r.getVersion(),
                r.getHourlyRateCents(), r.getMinimumMinutes(), r.getRateStatus(), r.getUpdatedBy(), r.getUpdatedAt())).toList(),
            fees.findAllByOrderBySortOrderAscNameAsc().stream().map(f -> new FeeView(f.getId(), f.getVersion(), f.getName(), f.getType(),
                f.getAmountCents(), f.isActive(), f.getSortOrder())).toList(),
            sampleRatesShown);
    }

    @Transactional
    public PricingView saveSettings(SaveSettingsRequest request) {
        if (!List.of(15, 30, 60).contains(request.billingIncrementMinutes())) throw new IllegalArgumentException("Billing increment must be 15, 30 or 60 minutes");
        if ((request.includedKmPerHour() == null) != (request.excessKmRateCents() == null))
            throw new IllegalArgumentException("Set both the included distance and the excess-distance rate, or neither");
        OperationsSettings s = settings.current();
        if (s.getVersion() != request.version()) throw new ConflictException("Settings changed. Reload before saving.");
        String before = "turnaround=" + s.getTurnaroundMinutes() + "; increment=" + s.getBillingIncrementMinutes();
        s.setTurnaroundMinutes(request.turnaroundMinutes()); s.setBillingIncrementMinutes(request.billingIncrementMinutes());
        s.setIncludedKmPerHour(request.includedKmPerHour()); s.setExcessKmRateCents(request.excessKmRateCents());
        s.setOvertimeHourlyRateCents(request.overtimeHourlyRateCents()); s.touch(actor());
        settings.flush();
        audit.record("PRICING_SETTINGS_UPDATED", "operations_settings", before + " -> turnaround=" + s.getTurnaroundMinutes()
            + "; increment=" + s.getBillingIncrementMinutes());
        return get();
    }

    @Transactional
    public PricingView saveRate(VehicleCategory category, SaveRateRequest request) {
        PrivateTripRate rate = rates.findById(category).orElseThrow(() -> new ResourceNotFoundException("Rate not found"));
        if (rate.getVersion() != request.version()) throw new ConflictException("This rate changed. Reload before saving.");
        String before = rate.getRateStatus() + " " + rate.getHourlyRateCents();
        rate.update(request.hourlyRateCents(), request.minimumMinutes(), actor());
        rates.flush();
        audit.record("PRIVATE_TRIP_RATE_UPDATED", category.name(), before + " -> LIVE " + request.hourlyRateCents() + "/h, min " + request.minimumMinutes() + "m");
        return get();
    }

    @Transactional
    public PricingView saveFee(UUID id, SaveFeeRequest request) {
        PrivateTripFee fee;
        if (id == null) fee = new PrivateTripFee();
        else {
            fee = fees.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fee not found"));
            if (fee.getVersion() != request.version()) throw new ConflictException("This fee changed. Reload before saving.");
        }
        fee.update(request.name().trim(), request.type(), request.amountCents(), request.active(), request.sortOrder(), actor());
        fees.saveAndFlush(fee);
        audit.record(id == null ? "PRIVATE_TRIP_FEE_CREATED" : "PRIVATE_TRIP_FEE_UPDATED", fee.getId().toString(),
            fee.getName() + " " + fee.getType() + " " + fee.getAmountCents() + (fee.isActive() ? "" : " (inactive)"));
        return get();
    }

    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
