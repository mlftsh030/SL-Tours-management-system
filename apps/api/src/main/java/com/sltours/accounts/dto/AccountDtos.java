package com.sltours.accounts.dto;
import com.sltours.enquiries.model.*;
import com.sltours.notifications.EmailSender.Delivery;
import jakarta.validation.constraints.*;
import java.time.*;

/** Requests and responses for customer self-service: verification, password reset, profile and own enquiries. */
public final class AccountDtos {
    private AccountDtos() {}
    /** International format with the country code, e.g. +27 82 555 1234. */
    public static final String PHONE_PATTERN = "^\\+[1-9][0-9 ()-]{6,22}[0-9]$";
    public static final String PHONE_MESSAGE = "Enter a WhatsApp number with the country code, for example +27 82 555 1234";

    public record RegisterResult(String email, Delivery verificationEmail) {}
    public record TokenRequest(@NotBlank @Size(max = 64) String token) {}
    public record LinkedRecords(int bookings, int enquiries, int documents) {
        public int total() { return bookings + enquiries + documents; }
    }
    public record VerifyResult(String email, LinkedRecords linked) {}
    public record DeliveryResult(Delivery delivery) {}
    public record ResetRequest(@NotBlank @Email @Size(max = 180) String email) {}
    public record ResetConfirm(@NotBlank @Size(max = 64) String token, @NotBlank @Size(min = 12, max = 64) String password,
                               @Size(max = 64) String passwordConfirmation) {
        @Override public String toString() { return "ResetConfirm[REDACTED]"; }
    }
    public record Profile(String name, String email, String phone, boolean emailVerified, OffsetDateTime memberSince) {}
    public record UpdateProfile(@NotBlank @Size(max = 160) String name,
                                @Size(max = 40) @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone) {}
    public record CustomerEnquiry(String reference, EnquiryType type, EnquirySource source, EnquiryStatus status, String tourTitle,
                                  LocalDate requestedDate, Integer passengers, String message, String bookingReference, OffsetDateTime createdAt) {}
}
