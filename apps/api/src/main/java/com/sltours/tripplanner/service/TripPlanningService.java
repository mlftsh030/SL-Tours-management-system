package com.sltours.tripplanner.service;

import com.sltours.tripplanner.dto.CreateTripPlanningRequest;
import com.sltours.tripplanner.dto.TripPlanningResponse;
import com.sltours.tripplanner.model.TripPlanningRequest;
import com.sltours.tripplanner.model.TripPlanningStatus;
import com.sltours.tripplanner.repository.TripPlanningRequestRepository;
import java.security.SecureRandom;
import java.time.Year;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripPlanningService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String RECEIVED = "Thanks. SL Tours will shape this into a tailored Cape Town plan.";
    private final TripPlanningRequestRepository repository;
    private final com.sltours.enquiries.service.EnquiryService enquiries;

    public TripPlanningService(TripPlanningRequestRepository repository, com.sltours.enquiries.service.EnquiryService enquiries) {
        this.repository = repository;
        this.enquiries = enquiries;
    }

    @Transactional
    public TripPlanningResponse createRequest(CreateTripPlanningRequest request) {
        if (request.idempotencyKey() != null) {
            var previous = enquiries.existing(request.idempotencyKey());
            if (previous.isPresent()) return new TripPlanningResponse(previous.get().reference(), TripPlanningStatus.NEW, RECEIVED);
        }
        if (request.departureDate().isBefore(request.arrivalDate())) {
            throw new IllegalArgumentException("Departure date must be on or after arrival date");
        }

        TripPlanningRequest entity = new TripPlanningRequest();
        entity.setReference(nextReference());
        entity.setCustomerName(request.customerName().trim());
        entity.setCustomerEmail(request.customerEmail().trim().toLowerCase());
        entity.setCustomerPhone(request.customerPhone());
        entity.setArrivalDate(request.arrivalDate());
        entity.setDepartureDate(request.departureDate());
        entity.setAdults(request.adults());
        entity.setChildren(request.children());
        entity.setGroupType(request.groupType());
        entity.setInterests(request.interests());
        entity.setServices(request.services());
        entity.setNotes(request.notes());
        entity.setStatus(TripPlanningStatus.NEW);

        TripPlanningRequest saved = repository.saveAndFlush(entity);
        enquiries.recordTripPlanner(saved, summary(saved), request.idempotencyKey(), request.referral());
        return new TripPlanningResponse(saved.getReference(), TripPlanningStatus.NEW, RECEIVED);
    }

    static String summary(TripPlanningRequest r) {
        var lines = new java.util.ArrayList<String>();
        lines.add("Trip planner request: " + r.getArrivalDate() + " to " + r.getDepartureDate());
        lines.add("Travellers: " + r.getAdults() + " adult(s), " + r.getChildren() + " child(ren); group: " + r.getGroupType());
        lines.add("Interests: " + String.join(", ", r.getInterests()));
        lines.add("Services: " + String.join(", ", r.getServices()));
        if (r.getNotes() != null && !r.getNotes().isBlank()) lines.add(r.getNotes().trim());
        return String.join("\n", lines);
    }

    private String nextReference() {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                suffix.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
            }
            String reference = "SLP-" + Year.now() + "-" + suffix;
            if (!repository.existsByReference(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Could not allocate a trip planning reference");
    }
}
