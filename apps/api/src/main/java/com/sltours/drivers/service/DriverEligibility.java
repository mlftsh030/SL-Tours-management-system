package com.sltours.drivers.service;
import com.sltours.drivers.model.Driver;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Backend rule for whether a driver may be assigned to, and collect a vehicle for, a trip on a given date.
 * South African passenger transport requires a valid driving licence and Professional Driving Permit (PrDP).
 */
@Component
public class DriverEligibility {
    public List<String> problems(Driver driver, boolean accountEnabled, LocalDate tripDate) {
        List<String> problems = new ArrayList<>();
        if (driver.getStatus() != Driver.Status.ACTIVE) problems.add("Driver is inactive");
        if (driver.getAccountId() == null) problems.add("Driver has no sign-in account");
        else if (!accountEnabled) problems.add("Driver account is disabled");
        if (driver.getVerificationStatus() != Driver.Verification.VERIFIED) problems.add("Driver verification is not complete");
        if (driver.getLicenceExpiresOn() == null || driver.getLicenceExpiresOn().isBefore(tripDate))
            problems.add("Driving licence is missing or expires before " + tripDate);
        if (driver.getPrdpExpiresOn() == null || driver.getPrdpExpiresOn().isBefore(tripDate))
            problems.add("PrDP is missing or expires before " + tripDate);
        return problems;
    }
    public void require(Driver driver, boolean accountEnabled, LocalDate tripDate) {
        List<String> problems = problems(driver, accountEnabled, tripDate);
        if (!problems.isEmpty()) throw new IllegalArgumentException("Driver is not eligible: " + String.join("; ", problems));
    }
}
