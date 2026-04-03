package com.devticket.apigateway.infrastructure.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
    int status,
    String code,
    String message,
    LocalDateTime timestamp
) {
    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(status, code, message, LocalDateTime.now());
    }
}
