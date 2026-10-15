package com.sltours.enquiries;
import com.sltours.enquiries.dto.*;
import com.sltours.enquiries.model.*;
import com.sltours.enquiries.repository.EnquiryRepository;
import com.sltours.enquiries.service.EnquiryService;
import com.sltours.shared.error.ConflictException;
import com.sltours.support.PostgresIntegrationTest;
import com.sltours.tripplanner.dto.CreateTripPlanningRequest;
import com.sltours.tripplanner.service.TripPlanningService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class EnquiryIntegrationTest extends PostgresIntegrationTest {
    @Autowired EnquiryService enquiries;
    @Autowired EnquiryRepository repository;
    @Autowired TripPlanningService planner;
    @Autowired JdbcTemplate jdbc;

    private CreateEnquiryRequest contact(UUID key) {
        return new CreateEnquiryRequest(key, "Thandi Nkosi", "Thandi@Example.com ", "+27 82 000 0000",
            "We would like a private trip to Cape Point.", EnquiryType.TOUR, "cape-peninsula-journey",
            LocalDate.now().plusDays(10), "V&A Waterfront", 4, null, null, null);
    }

    @Test void enquiryIsStoredBeforeTheReceiptAndRetriesDoNotDuplicate() {
        UUID key = UUID.randomUUID();
        EnquiryReceipt first = enquiries.submit(contact(key));
        EnquiryReceipt retry = enquiries.submit(contact(key));
        assertThat(first.duplicate()).isFalse();
        assertThat(retry.duplicate()).isTrue();
        assertThat(retry.reference()).isEqualTo(first.reference());
        Enquiry stored = repository.findByReference(first.reference()).orElseThrow();
        assertThat(stored.getCustomerEmail()).isEqualTo("thandi@example.com");
        assertThat(stored.getStatus()).isEqualTo(EnquiryStatus.NEW);
        assertThat(jdbc.queryForObject("select count(*) from enquiries where idempotency_key = ?", Long.class, key)).isEqualTo(1);
    }

    @Test void inboxSearchesFiltersUpdatesAndConvertsWithoutReenteringCustomerDetails() {
        EnquiryReceipt receipt = enquiries.submit(contact(UUID.randomUUID()));
        assertThat(enquiries.list("thandi", null, null, 0, 20).getContent()).extracting(EnquirySummary::reference).contains(receipt.reference());
        assertThat(enquiries.list(receipt.reference().toLowerCase(), EnquiryStatus.CLOSED, null, 0, 20).getContent()).isEmpty();
        assertThat(enquiries.list("100%_match", null, null, 0, 20).getContent()).isEmpty();

        EnquiryDetail withNote = enquiries.addNote(receipt.reference(), new AddEnquiryNoteRequest("Called the customer, waiting on dates."));
        assertThat(withNote.status()).isEqualTo(EnquiryStatus.IN_PROGRESS);
        assertThat(withNote.notes()).singleElement().satisfies(n -> assertThat(n.author()).isEqualTo("admin@test.local"));

        EnquiryDetail waiting = enquiries.updateStatus(receipt.reference(), new UpdateEnquiryRequest(EnquiryStatus.AWAITING_CUSTOMER, withNote.version()));
        assertThatThrownBy(() -> enquiries.updateStatus(receipt.reference(), new UpdateEnquiryRequest(EnquiryStatus.CLOSED, withNote.version())))
            .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> enquiries.updateStatus(receipt.reference(), new UpdateEnquiryRequest(EnquiryStatus.CONVERTED, waiting.version())))
            .isInstanceOf(IllegalArgumentException.class);

        ConversionResult converted = enquiries.convert(receipt.reference(), new ConvertEnquiryRequest(waiting.version(),
            "cape-peninsula-journey", LocalDate.now().plusDays(10), 6, null, null));
        var booking = jdbc.queryForMap("select customer_name, customer_email, pickup_location, account_id from bookings where reference = ?",
            converted.bookingReference());
        assertThat(booking.get("customer_name")).isEqualTo("Thandi Nkosi");
        assertThat(booking.get("customer_email")).isEqualTo("thandi@example.com");
        assertThat(booking.get("pickup_location")).isEqualTo("V&A Waterfront");
        assertThat(booking.get("account_id")).isNull();
        EnquiryDetail after = enquiries.get(receipt.reference());
        assertThat(after.status()).isEqualTo(EnquiryStatus.CONVERTED);
        assertThat(after.convertedBookingReference()).isEqualTo(converted.bookingReference());
        assertThatThrownBy(() -> enquiries.convert(receipt.reference(), new ConvertEnquiryRequest(after.version(),
            "cape-peninsula-journey", LocalDate.now().plusDays(10), 6, null, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void tripPlannerRequestsAppearInTheInboxAndExistingOnesWereBackfilled() {
        UUID key = UUID.randomUUID();
        var request = new CreateTripPlanningRequest("Planner Guest", "planner@example.com", null, LocalDate.now().plusDays(20),
            LocalDate.now().plusDays(25), 2, 1, "family", List.of("nature"), List.of("tours"), "Penguins please", key, null);
        var response = planner.createRequest(request);
        assertThat(planner.createRequest(request).reference()).isEqualTo(response.reference());
        EnquiryDetail detail = enquiries.get(response.reference());
        assertThat(detail.source()).isEqualTo(EnquirySource.TRIP_PLANNER);
        assertThat(detail.passengers()).isEqualTo(3);
        assertThat(detail.message()).contains("Penguins please");
        assertThat(jdbc.queryForObject("select count(*) from enquiries where reference = 'SLP-2026-K9M8N' and source = 'TRIP_PLANNER'", Long.class))
            .isEqualTo(1);
    }
}
