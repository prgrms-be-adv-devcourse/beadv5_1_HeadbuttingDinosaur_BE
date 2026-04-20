package com.devticket.settlement.common.exception.response;

import com.devticket.settlement.common.exception.ErrorCode;
import java.time.LocalDateTime;

public record ErrorResponse(
    int status,
    String code,
    String message,
    LocalDateTime timeStamp
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
