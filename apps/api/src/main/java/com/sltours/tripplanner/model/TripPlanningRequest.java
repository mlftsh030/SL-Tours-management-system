package com.sltours.tripplanner.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trip_planning_requests")
public class TripPlanningRequest {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Column(nullable = false, length = 160)
    private String customerName;

    @Column(nullable = false, length = 180)
    private String customerEmail;

    @Column(length = 80)
    private String customerPhone;

    @Column(nullable = false)
    private LocalDate arrivalDate;

    @Column(nullable = false)
    private LocalDate departureDate;

    @Column(nullable = false)
    private int adults;

    @Column(nullable = false)
    private int children;

    @Column(nullable = false, length = 80)
    private String groupType;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "trip_planning_interests", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false)
    private List<String> interests = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "trip_planning_services", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false)
    private List<String> services = new ArrayList<>();

    @Column(length = 1600)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TripPlanningStatus status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public LocalDate getArrivalDate() { return arrivalDate; }
    public LocalDate getDepartureDate() { return departureDate; }
    public int getAdults() { return adults; }
    public int getChildren() { return children; }
    public String getGroupType() { return groupType; }
    public List<String> getInterests() { return interests; }
    public List<String> getServices() { return services; }
    public String getNotes() { return notes; }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getReference() {
        return reference;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public void setArrivalDate(LocalDate arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public void setDepartureDate(LocalDate departureDate) {
        this.departureDate = departureDate;
    }

    public void setAdults(int adults) {
        this.adults = adults;
    }

    public void setChildren(int children) {
        this.children = children;
    }

    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public void setServices(List<String> services) {
        this.services = services;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setStatus(TripPlanningStatus status) {
        this.status = status;
    }
}
