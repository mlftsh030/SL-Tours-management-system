package com.sltours.bookings.service;

import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.service.AccountService;
import java.util.Locale;
import com.sltours.bookings.dto.BookingResponse;
import com.sltours.bookings.dto.CreateBookingRequest;
import com.sltours.bookings.model.Booking;
import com.sltours.bookings.model.BookingStatus;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.tours.model.PricingType;
import com.sltours.tours.model.Tour;
import com.sltours.tours.service.TourService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final TourService tourService;
    private final BookingReferenceGenerator referenceGenerator;
    private final BookingMapper bookingMapper;
    private final AccountRepository accounts;
    private final com.sltours.partners.service.ReferralService referrals;

    public BookingService(
            BookingRepository bookingRepository,
            TourService tourService,
            BookingReferenceGenerator referenceGenerator,
            BookingMapper bookingMapper,
            AccountRepository accounts,
            com.sltours.partners.service.ReferralService referrals
    ) {
        this.referrals = referrals;
        this.bookingRepository = bookingRepository;
        this.tourService = tourService;
        this.referenceGenerator = referenceGenerator;
        this.bookingMapper = bookingMapper;
        this.accounts = accounts;
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        return createBooking(request, null);
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, String authenticatedEmail) {
        Tour tour = tourService.findActiveBySlug(request.tourSlug());
        validateGuestCount(tour, request.guests());

        Booking booking = new Booking();
        booking.setReference(referenceGenerator.nextReference(bookingRepository));
        booking.setTour(tour);
        booking.setTravelDate(request.travelDate());
        booking.setGuests(request.guests());
        booking.setCustomerName(request.customerName().trim());
        booking.setCustomerEmail(request.customerEmail().trim().toLowerCase(Locale.ROOT));
        if (authenticatedEmail != null) {
            accounts.findByEmail(authenticatedEmail).filter(Account::isEnabled)
                .filter(a -> a.getRole() == Account.Role.CUSTOMER).ifPresent(account -> {
                    booking.setAccountId(account.getId());
                    booking.setCustomerEmail(account.getEmail());
                    booking.setCustomerName(account.getName());
                });
        }
        booking.setCustomerPhone(request.customerPhone());
        booking.setCustomerCountry(request.customerCountry());
        booking.setPickupLocation(request.pickupLocation());
        booking.setNotes(request.notes());
        booking.setStatus(BookingStatus.ENQUIRY);
        booking.setTotalAmountCents(calculateTotal(tour, request.guests()));
        booking.setAmountPaidCents(0);

        Booking saved = bookingRepository.save(booking);
        if (request.referral() != null) {
            bookingRepository.flush();
            referrals.attachToBooking(saved.getId(), request.referral());
        }
        return bookingMapper.toResponse(saved, "Thanks. SL Tours will review your request and confirm availability.");
    }

    /** Staff-created tour booking from an enquiry. The booking is not linked to any account by email. */
    @Transactional
    public Booking createFromEnquiry(Tour tour, java.time.LocalDate travelDate, int guests, String name, String email,
            String phone, String pickupLocation, String notes) {
        validateGuestCount(tour, guests);
        Booking booking = new Booking();
        booking.setReference(referenceGenerator.nextReference(bookingRepository));
        booking.setTour(tour);
        booking.setTravelDate(travelDate);
        booking.setGuests(guests);
        booking.setCustomerName(name);
        booking.setCustomerEmail(email);
        booking.setCustomerPhone(phone);
        booking.setPickupLocation(pickupLocation);
        booking.setNotes(notes);
        booking.setStatus(BookingStatus.ENQUIRY);
        booking.setTotalAmountCents(calculateTotal(tour, guests));
        booking.setAmountPaidCents(0);
        return bookingRepository.saveAndFlush(booking);
    }

    private void validateGuestCount(Tour tour, int guests) {
        if (guests < tour.getMinimumGuests() || guests > tour.getMaximumGuests()) {
            throw new IllegalArgumentException("Guest count must be between "
                    + tour.getMinimumGuests() + " and " + tour.getMaximumGuests());
        }
    }

    private int calculateTotal(Tour tour, int guests) {
        if (tour.getPricingType() == PricingType.CUSTOM) {
            return 0;
        }
        if (tour.getPricingType() == PricingType.PER_TRIP) {
            return tour.getBasePriceCents();
        }
        return Math.multiplyExact(tour.getBasePriceCents(), guests);
    }
}
