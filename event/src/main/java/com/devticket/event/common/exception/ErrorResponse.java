package com.devticket.event.common.exception;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder(access = AccessLevel.PRIVATE)
public class ErrorResponse {

    private final int status;
    private final String code;
    private final String message;
    private final LocalDateTime timestamp;
    private final List<CustomFieldError> errors;

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
            .status(errorCode.getStatus())
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .timestamp(LocalDateTime.now())
            .errors(Collections.emptyList())
            .build();
    }

    public static ErrorResponse of(String code, String message, int status) {
        return ErrorResponse.builder()
            .status(status)
            .code(code)
            .message(message)
            .timestamp(LocalDateTime.now())
            .errors(Collections.emptyList())
            .build();
    }

    public static ErrorResponse ofValidation(String code, BindingResult bindingResult) {
        return ErrorResponse.builder()
            .status(400)
            .code(code)
            .message("입력값이 올바르지 않습니다.")
            .timestamp(LocalDateTime.now())
            .errors(CustomFieldError.of(bindingResult))
            .build();
    }

    @Getter
    public static class CustomFieldError {
        private final String field;
        private final String value;
        private final String reason;

        private CustomFieldError(String field, String value, String reason) {
            this.field = field;
            this.value = value;
            this.reason = reason;
        }

        public static List<CustomFieldError> of(BindingResult bindingResult) {
            List<FieldError> fieldErrors = bindingResult.getFieldErrors();
            return fieldErrors.stream()
                .map(error -> new CustomFieldError(
                    error.getField(),
                    error.getRejectedValue() == null ? "" : error.getRejectedValue().toString(),
                    error.getDefaultMessage()))
                .collect(Collectors.toList());
        }
    }
}
