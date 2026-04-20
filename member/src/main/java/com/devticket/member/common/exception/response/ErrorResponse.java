package com.devticket.member.common.exception.response;

import com.devticket.member.common.exception.ErrorCode;
import com.devticket.member.common.exception.FieldError;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.validation.BindingResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String code,
    String message,
    LocalDateTime timestamp,
    List<FieldError> errors
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
            errorCode.getStatus(),
            errorCode.getCode(),
            errorCode.getMessage(),
            LocalDateTime.now(),
            null
        );
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(500, code, message, LocalDateTime.now(), null);
    }

    public static ErrorResponse ofValidation(String code, BindingResult bindingResult) {
        List<FieldError> fieldErrors = bindingResult.getFieldErrors().stream()
            .map(error -> new FieldError(
                error.getField(),
                error.getRejectedValue() == null ? "" : error.getRejectedValue().toString(),
                error.getDefaultMessage()
            ))
            .toList();

        return new ErrorResponse(400, code, "입력값이 올바르지 않습니다.", LocalDateTime.now(), fieldErrors);
    }
}
