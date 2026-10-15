package com.sltours.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sltours.bookings.dto.CreateBookingRequest;
import com.sltours.bookings.model.Booking;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.bookings.service.BookingMapper;
import com.sltours.bookings.service.BookingReferenceGenerator;
import com.sltours.bookings.service.BookingService;
import com.sltours.tours.model.PricingType;
import com.sltours.tours.model.Tour;
import com.sltours.tours.model.TourCategory;
import com.sltours.tours.model.TourStatus;
import com.sltours.tours.service.TourService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TourService tourService;

    @Mock
    private BookingReferenceGenerator referenceGenerator;

    @Mock
    private com.sltours.accounts.repository.AccountRepository accounts;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                tourService,
                referenceGenerator,
                new BookingMapper(), accounts, org.mockito.Mockito.mock(com.sltours.partners.service.ReferralService.class));
    }

    @Test
    void createsBookingWithPriceFromTourDatabaseRecord() {
        Tour tour = tour(PricingType.PER_PERSON, 210000, 1, 18);
        when(tourService.findActiveBySlug("cape-peninsula-journey")).thenReturn(tour);
        when(referenceGenerator.nextReference(bookingRepository)).thenReturn("SLT-2026-ABC123");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = bookingService.createBooking(new CreateBookingRequest(
                "cape-peninsula-journey",
                LocalDate.now().plusDays(3),
                4,
                "Sarah Demo",
                "SARAH@example.com",
                "+44 20 0000 0000",
                "United Kingdom",
                "Hotel lobby",
                "Family trip", null));

        assertThat(response.reference()).isEqualTo("SLT-2026-ABC123");
        assertThat(response.totalAmountCents()).isEqualTo(840000);
    }

    @Test
    void rejectsGuestCountsOutsideTourRules() {
        Tour tour = tour(PricingType.PER_PERSON, 210000, 2, 6);
        when(tourService.findActiveBySlug("cape-peninsula-journey")).thenReturn(tour);

        assertThatThrownBy(() -> bookingService.createBooking(new CreateBookingRequest(
                "cape-peninsula-journey",
                LocalDate.now().plusDays(3),
                1,
                "Sarah Demo",
                "sarah@example.com",
                null,
                null,
                null,
                null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 2 and 6");
    }

    @Test
    void authenticatedBookingsUseAccountIdentityRatherThanSubmittedEmail() {
        Tour tour = tour(PricingType.PER_PERSON, 210000, 1, 18);
        when(tourService.findActiveBySlug("cape-peninsula-journey")).thenReturn(tour);
        when(referenceGenerator.nextReference(bookingRepository)).thenReturn("SLT-ACCOUNT");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var account = new com.sltours.accounts.model.Account();
        account.setName("Real Customer"); account.setEmail("real@example.com");
        account.setRole(com.sltours.accounts.model.Account.Role.CUSTOMER);
        when(accounts.findByEmail("real@example.com")).thenReturn(java.util.Optional.of(account));
        bookingService.createBooking(new CreateBookingRequest("cape-peninsula-journey", LocalDate.now().plusDays(3), 2,
            "Impersonated Customer", "other@example.com", null, null, null, null, null), "real@example.com");
        var captor = org.mockito.ArgumentCaptor.forClass(Booking.class);
        org.mockito.Mockito.verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(account.getId());
        assertThat(captor.getValue().getCustomerEmail()).isEqualTo("real@example.com");
        assertThat(captor.getValue().getCustomerName()).isEqualTo("Real Customer");
    }

    @Test
    void guestBookingCannotClaimAccountThroughEmail() {
        Tour tour = tour(PricingType.PER_PERSON, 210000, 1, 18);
        when(tourService.findActiveBySlug("cape-peninsula-journey")).thenReturn(tour);
        when(referenceGenerator.nextReference(bookingRepository)).thenReturn("SLT-GUEST");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        bookingService.createBooking(new CreateBookingRequest("cape-peninsula-journey", LocalDate.now().plusDays(3), 2,
            "Someone", "real@example.com", null, null, null, null, null));
        var captor = org.mockito.ArgumentCaptor.forClass(Booking.class);
        org.mockito.Mockito.verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isNull();
        org.mockito.Mockito.verifyNoInteractions(accounts);
    }

    private Tour tour(PricingType pricingType, int priceCents, int minGuests, int maxGuests) {
        Tour tour = new Tour();
        tour.setSlug("cape-peninsula-journey");
        tour.setTitle("Cape Peninsula Journey");
        tour.setShortDescription("Coastal full-day tour");
        tour.setFullDescription("Full detail");
        tour.setDuration("Full day");
        tour.setBasePriceCents(priceCents);
        tour.setPricingType(pricingType);
        tour.setMinimumGuests(minGuests);
        tour.setMaximumGuests(maxGuests);
        tour.setPickupAvailable(true);
        tour.setFeatured(true);
        tour.setStatus(TourStatus.ACTIVE);
        tour.setCategory(TourCategory.PENINSULA);
        tour.setHeroImage("/assets/cape-point-tour.jpg");
        tour.setSeoTitle("Cape Peninsula");
        tour.setSeoDescription("Cape Peninsula tour");
        return tour;
    }
}
