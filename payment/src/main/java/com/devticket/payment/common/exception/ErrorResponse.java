package com.devticket.payment.common.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
    int status,
    String code,
    String message,
    LocalDateTime timestamp
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
            errorCode.getStatus(),
            errorCode.getCode(),
            errorCode.getMessage(),
            LocalDateTime.now()
        );
    }
}
