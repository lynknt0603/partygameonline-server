package com.partygameonline.common.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String errorCode,
        String message,
        Instant timestamp,
        String path,
        String requestId,
        List<FieldErrorDetail> fieldErrors
) {

    public ErrorResponse(
            String errorCode,
            String message,
            String path,
            String requestId,
            List<FieldErrorDetail> fieldErrors
    ) {
        this(errorCode, message, Instant.now(), path, requestId, fieldErrors == null ? List.of() : List.copyOf(fieldErrors));
    }

    public ErrorResponse(String errorCode, String message, String path, String requestId) {
        this(errorCode, message, path, requestId, List.of());
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
