package com.sltours.enquiries.dto;
import jakarta.validation.constraints.*;
public record AddEnquiryNoteRequest(@NotBlank @Size(max = 2000) String body) {}
