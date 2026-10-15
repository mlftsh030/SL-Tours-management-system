package com.sltours.shared.error;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        Map<String, String> errors
) {
    public static ApiError of(int status, String code, String message) {
        return new ApiError(OffsetDateTime.now(), status, code, message, Map.of());
    }

    public static ApiError withErrors(int status, String code, String message, Map<String, String> errors) {
        return new ApiError(OffsetDateTime.now(), status, code, message, errors);
    }
}
