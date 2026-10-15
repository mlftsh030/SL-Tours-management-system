package com.sltours.config;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import com.sltours.fleet.model.Vehicle;
import com.sltours.fleet.repository.VehicleRepository;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development-profile demo data: sign-in accounts for the seeded drivers and public listings for the seeded
 * vehicles. Never runs outside the dev profile, and never overwrites records an administrator has edited.
 */
@Component
@Profile("dev")
public class DevDataBootstrap implements ApplicationRunner {
    private static final Map<UUID, String> DRIVER_LOGINS = Map.of(
        UUID.fromString("60000000-0000-0000-0000-000000000001"), "sipho.driver@sltours.local",
        UUID.fromString("60000000-0000-0000-0000-000000000002"), "daniel.driver@sltours.local");
    /** Demo sign-ins created only by this class; disabled at startup outside dev/test by {@link DemoAccountGuard}. */
    public static final Set<String> DEMO_EMAILS = Set.copyOf(DRIVER_LOGINS.values());
    private static final UUID VERIFIED_DEMO_DRIVER = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Map<UUID, List<String>> AMENITIES = Map.of(
        UUID.fromString("70000000-0000-0000-0000-000000000001"), List.of("Air conditioning", "Leather seats", "Phone charging", "Bottled water"),
        UUID.fromString("70000000-0000-0000-0000-000000000002"), List.of("Air conditioning", "Large luggage space", "Group seating"),
        UUID.fromString("70000000-0000-0000-0000-000000000003"), List.of("Air conditioning", "Sliding doors"),
        UUID.fromString("70000000-0000-0000-0000-000000000004"), List.of("Air conditioning", "Phone charging"));
    private final DriverRepository drivers;
    private final AccountRepository accounts;
    private final VehicleRepository vehicles;
    private final PasswordEncoder passwords;
    private final String driverPassword;
    public DevDataBootstrap(DriverRepository drivers, AccountRepository accounts, VehicleRepository vehicles, PasswordEncoder passwords,
            @Value("${sltours.dev.driver-password}") String driverPassword) {
        this.drivers = drivers; this.accounts = accounts; this.vehicles = vehicles; this.passwords = passwords; this.driverPassword = driverPassword;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        // Re-enable demo sign-ins that a non-dev start disabled (see DemoAccountGuard).
        DEMO_EMAILS.forEach(email -> accounts.findByEmail(email).filter(a -> !a.isEnabled()).ifPresent(a -> a.setEnabled(true)));
        DRIVER_LOGINS.forEach((driverId, email) -> drivers.findById(driverId).filter(d -> d.getAccountId() == null).ifPresent(driver -> {
            Account account = accounts.findByEmail(email).orElseGet(() -> {
                Account created = new Account();
                created.setEmail(email); created.setName(driver.getName()); created.setRole(Account.Role.DRIVER);
                created.setPasswordHash(passwords.encode(driverPassword));
                return accounts.save(created);
            });
            driver.setAccountId(account.getId());
            if (driverId.equals(VERIFIED_DEMO_DRIVER) && driver.getVerificationStatus() == Driver.Verification.PENDING) {
                driver.setLicenceNumber("DEMO-0001");
                driver.setLicenceExpiresOn(LocalDate.now().plusYears(2));
                driver.setPrdpExpiresOn(LocalDate.now().plusYears(1));
                driver.setVerification(Driver.Verification.VERIFIED, "Development seed only, not a real verification", "dev-bootstrap");
            }
        }));
        AMENITIES.forEach((vehicleId, amenities) -> vehicles.findById(vehicleId)
            .filter(v -> v.getVersion() == 0 && v.getListingStatus() == Vehicle.ListingStatus.HIDDEN && v.getAmenities().isEmpty())
            .ifPresent(v -> { v.setAmenities(amenities); v.setListingStatus(Vehicle.ListingStatus.LISTED);
                v.setPublicDescription("Comfortable, air-conditioned " + v.getDisplayName() + " with a professional SL Tours driver."); }));
    }
}
