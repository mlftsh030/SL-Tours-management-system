package com.sltours.bookings.repository;

import com.sltours.bookings.model.Booking;
import com.sltours.bookings.model.BookingStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @EntityGraph(attributePaths = "tour")
    org.springframework.data.domain.Page<Booking> findByStatus(BookingStatus status, org.springframework.data.domain.Pageable pageable);

    @Override @EntityGraph(attributePaths = "tour")
    org.springframework.data.domain.Page<Booking> findAll(org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = "tour")
    org.springframework.data.domain.Page<Booking> findByAccountId(UUID accountId, org.springframework.data.domain.Pageable pageable);

    boolean existsByReference(String reference);

    Optional<Booking> findByIdempotencyKey(UUID idempotencyKey);

    Optional<Booking> findByReference(String reference);

    @EntityGraph(attributePaths = "tour")
    List<Booking> findTop50ByOrderByTravelDateAscCreatedAtDesc();

    @EntityGraph(attributePaths = "tour")
    List<Booking> findTop50ByStatusOrderByTravelDateAscCreatedAtDesc(BookingStatus status);

    long countByTravelDate(LocalDate travelDate);

    long countByStatus(BookingStatus status);

    @Query("select coalesce(sum(b.totalAmountCents), 0) from Booking b where b.status not in (com.sltours.bookings.model.BookingStatus.CANCELLED, com.sltours.bookings.model.BookingStatus.REFUNDED)")
    long sumNonCancelledRevenueCents();
    java.util.Optional<Booking> findBySourceQuotationId(UUID quotationId);
}
