package com.sltours.assignments.repository;
import com.sltours.assignments.model.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    /** Placeholder for "no assignment to ignore" in conflict queries. */
    UUID NONE = new UUID(0, 0);
    Optional<Assignment> findByIdAndDriverId(UUID id, UUID driverId);
    List<Assignment> findByDriverIdAndStatusNotAndBlockedUntilAfterOrderByStartsAtAsc(UUID driverId, AssignmentStatus excluded, OffsetDateTime after);
    Optional<Assignment> findFirstByBookingIdAndStatusNot(UUID bookingId, AssignmentStatus excluded);
    List<Assignment> findByBookingIdAndStatusIn(UUID bookingId, Collection<AssignmentStatus> statuses);
    List<Assignment> findByStatusNotAndBlockedUntilAfterOrderByStartsAtAsc(AssignmentStatus excluded, OffsetDateTime after);
    List<Assignment> findByStatusInOrderByStartsAtAsc(Collection<AssignmentStatus> statuses);
    List<Assignment> findByStatusInAndEndsAtAfterOrderByStartsAtAsc(Collection<AssignmentStatus> statuses, OffsetDateTime after);
    List<Assignment> findByVehicleIdAndStatusInAndEndsAtAfterOrderByStartsAtAsc(UUID vehicleId, Collection<AssignmentStatus> statuses, OffsetDateTime after);
    List<Assignment> findByVehicleIdAndStatusInAndIdNot(UUID vehicleId, Collection<AssignmentStatus> statuses, UUID id);
    List<Assignment> findByDriverIdAndStatusInAndEndsAtAfterOrderByStartsAtAsc(UUID driverId, Collection<AssignmentStatus> statuses, OffsetDateTime after);

    /** Live assignments for the vehicle whose blocked window overlaps [from, until). */
    @Query("""
        select a from Assignment a where a.vehicleId = :vehicleId and a.status <> com.sltours.assignments.model.AssignmentStatus.CANCELLED
        and a.startsAt < :until and a.blockedUntil > :from and a.id <> :ignore""")
    List<Assignment> vehicleConflicts(@Param("vehicleId") UUID vehicleId, @Param("from") OffsetDateTime from,
        @Param("until") OffsetDateTime until, @Param("ignore") UUID ignore);

    @Query("""
        select a from Assignment a where a.driverId = :driverId and a.status <> com.sltours.assignments.model.AssignmentStatus.CANCELLED
        and a.startsAt < :until and a.blockedUntil > :from and a.id <> :ignore""")
    List<Assignment> driverConflicts(@Param("driverId") UUID driverId, @Param("from") OffsetDateTime from,
        @Param("until") OffsetDateTime until, @Param("ignore") UUID ignore);

    /** Vehicles busy at any point in [from, until), used to filter availability in one query. */
    @Query("""
        select distinct a.vehicleId from Assignment a where a.vehicleId is not null
        and a.status <> com.sltours.assignments.model.AssignmentStatus.CANCELLED and a.startsAt < :until and a.blockedUntil > :from""")
    Set<UUID> busyVehicleIds(@Param("from") OffsetDateTime from, @Param("until") OffsetDateTime until);
}
