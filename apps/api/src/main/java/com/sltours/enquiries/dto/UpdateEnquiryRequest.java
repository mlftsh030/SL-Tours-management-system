package com.sltours.enquiries.dto;
import com.sltours.enquiries.model.EnquiryStatus;
import jakarta.validation.constraints.*;
public record UpdateEnquiryRequest(@NotNull EnquiryStatus status, @NotNull @Min(0) Long version) {}
