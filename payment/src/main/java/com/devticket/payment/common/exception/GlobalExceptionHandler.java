package com.devticket.payment.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity
            .status(e.getErrorCode().getStatus())
            .body(ErrorResponse.from(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.from(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity
            .internalServerError()
            .body(ErrorResponse.from(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }
}
