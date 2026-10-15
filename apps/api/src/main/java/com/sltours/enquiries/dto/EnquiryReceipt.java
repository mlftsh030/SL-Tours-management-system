package com.sltours.enquiries.dto;
import com.sltours.enquiries.model.EnquiryStatus;
/** Returned only after the enquiry is stored. {@code duplicate} is true when a retry matched an earlier submission. */
public record EnquiryReceipt(String reference, EnquiryStatus status, String message, boolean duplicate) {}
