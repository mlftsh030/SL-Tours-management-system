package com.sltours.support;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.assignments.model.Assignment;
import com.sltours.assignments.service.AssignmentService;
import com.sltours.bookings.model.*;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import com.sltours.fleet.model.*;
import com.sltours.fleet.repository.VehicleRepository;
import com.sltours.inspections.dto.InspectionDtos.InspectionForm;
import com.sltours.inspections.model.*;
import com.sltours.tours.repository.TourRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Builds isolated vehicles, drivers and bookings so integration tests never collide on shared resources. */
@TestComponent
public class Fixtures {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /** A real, decodable 200 × 150 photo; uploads are checked for complete pictures. */
    public static final byte[] JPEG = image("jpg", 200, 150);
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final AccountRepository accounts;
    private final BookingRepository bookings;
    private final TourRepository tours;
    private final AssignmentService assignments;
    private final PasswordEncoder passwords;
    public Fixtures(VehicleRepository vehicles, DriverRepository drivers, AccountRepository accounts, BookingRepository bookings,
            TourRepository tours, AssignmentService assignments, PasswordEncoder passwords) {
        this.vehicles = vehicles; this.drivers = drivers; this.accounts = accounts; this.bookings = bookings; this.tours = tours;
        this.assignments = assignments; this.passwords = passwords;
    }

    public static int next() { return SEQUENCE.incrementAndGet(); }

    @Transactional
    public Vehicle vehicle(VehicleCategory category, int capacity, Vehicle.ListingStatus listing) {
        Vehicle v = new Vehicle();
        v.setRegistration("TEST " + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        v.setDisplayName("Test " + category + " " + next()); v.setCategory(category); v.setCapacity(capacity);
        v.setListingStatus(listing); v.setAmenities(List.of("Air conditioning"));
        return vehicles.saveAndFlush(v);
    }

    @Transactional
    public Driver driver(boolean verified) {
        Account account = new Account();
        account.setEmail("driver" + next() + "-" + UUID.randomUUID().toString().substring(0, 6) + "@test.local");
        account.setName("Driver"); account.setRole(Account.Role.DRIVER); account.setPasswordHash(passwords.encode("driver-password-123"));
        accounts.saveAndFlush(account);
        Driver driver = new Driver();
        driver.setName("Driver " + next()); driver.setAccountId(account.getId());
        driver.setLicenceNumber("L-" + next()); driver.setLicenceExpiresOn(LocalDate.now().plusYears(3)); driver.setPrdpExpiresOn(LocalDate.now().plusYears(2));
        if (verified) driver.setVerification(Driver.Verification.VERIFIED, null, "test");
        return drivers.saveAndFlush(driver);
    }

    public String email(Driver driver) { return accounts.findById(driver.getAccountId()).orElseThrow().getEmail(); }

    @Transactional
    public Booking tourBooking(int guests, LocalDate date) {
        Booking booking = new Booking();
        booking.setReference("SLT-T" + next() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        booking.setTour(tours.findAll().get(0)); booking.setTravelDate(date); booking.setGuests(guests);
        booking.setCustomerName("Guest"); booking.setCustomerEmail("guest@example.com"); booking.setCustomerPhone("+27 82 000 0000");
        booking.setPickupLocation("Sea Point"); booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmountCents(100000); booking.setAmountPaidCents(0);
        return bookings.saveAndFlush(booking);
    }

    /** A confirmed tour booking assigned to the vehicle and driver for a window starting {@code daysAhead} days from now. */
    public Assignment assigned(Vehicle vehicle, Driver driver, int daysAhead) {
        OffsetDateTime start = LocalDate.now().plusDays(daysAhead).atTime(9, 0).atZone(AssignmentService.OPERATING_ZONE).toOffsetDateTime();
        Booking booking = tourBooking(2, start.toLocalDate());
        return assignments.assign(booking, vehicle.getId(), driver.getId(), start, start.plusHours(4));
    }

    public Assignment assignedAt(Vehicle vehicle, Driver driver, OffsetDateTime start, OffsetDateTime end) {
        Booking booking = tourBooking(2, start.atZoneSameInstant(AssignmentService.OPERATING_ZONE).toLocalDate());
        return assignments.assign(booking, vehicle.getId(), driver.getId(), start, end);
    }

    /** Encodes a small gradient picture in the given ImageIO format ("jpg" or "png"). */
    public static byte[] image(String format, int width, int height) {
        var picture = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) picture.setRGB(x, y, (x * 255 / width) << 16 | (y * 255 / height) << 8 | 90);
        var out = new java.io.ByteArrayOutputStream();
        try { javax.imageio.ImageIO.write(picture, format, out); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        return out.toByteArray();
    }

    public static MockMultipartFile photo(String name) { return new MockMultipartFile(name, name + ".jpg", "image/jpeg", JPEG); }

    public static List<MultipartFile> photos(int count) {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < count; i++) files.add(photo("photos"));
        return files;
    }

    /** A complete checklist; any listed items are reported as defects with the given severity. */
    public static InspectionForm form(int odometer, Map<ChecklistItem, ChecklistAnswer.Severity> defects) {
        List<ChecklistAnswer> answers = Arrays.stream(ChecklistItem.values()).map(item -> defects.containsKey(item)
            ? new ChecklistAnswer(item, ChecklistAnswer.Result.DEFECT, defects.get(item), "Problem with " + item.label)
            : new ChecklistAnswer(item, ChecklistAnswer.Result.OK, null, null)).toList();
        List<String> tags = new ArrayList<>(List.of("VEHICLE", "VEHICLE", "VEHICLE", "VEHICLE"));
        defects.keySet().forEach(item -> tags.add(item.name()));
        return new InspectionForm(odometer, FuelLevel.FULL, "Test inspection", answers, tags);
    }

    public static List<MultipartFile> photosFor(InspectionForm form) { return photos(form.photoTags().size()); }
}
