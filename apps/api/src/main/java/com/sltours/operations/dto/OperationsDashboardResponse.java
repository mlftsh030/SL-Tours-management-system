package com.sltours.operations.dto;

public record OperationsDashboardResponse(
        long bookingsToday,
        long awaitingPayment,
        long confirmedBookings,
        long enquiries,
        long revenueCents,
        long activeGuides,
        long activeVehicles,
        long newEnquiries,
        long privateTripRequests,
        long inspectionsAwaitingReview,
        long openVehicleRestrictions,
        long tripsNeedingAttention
) {
}
