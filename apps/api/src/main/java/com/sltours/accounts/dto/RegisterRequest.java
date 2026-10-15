package com.sltours.accounts.dto;
import jakarta.validation.constraints.*;

/** Public sign-up. There is deliberately no role field: every self-registered account is a CUSTOMER. */
public record RegisterRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Email @Size(max = 180) String email,
    @Size(max = 40) @Pattern(regexp = AccountDtos.PHONE_PATTERN, message = AccountDtos.PHONE_MESSAGE) String phone,
    @NotBlank @Size(min = 12, max = 64) String password,
    @Size(max = 64) String passwordConfirmation
) {
    public RegisterRequest(String name, String email, String password) { this(name, email, null, password, null); }
    @Override public String toString() { return "RegisterRequest[password=REDACTED]"; }
}
