package com.devticket.settlement.common.exception;

import com.devticket.settlement.common.exception.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
            .status(e.getErrorCode().getStatus())
            .body(ErrorResponse.from(errorCode));
    }

    // 유효성 검증 예외
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.from(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    // 알 수 없는 오류
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[GlobalExceptionHandler] Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity
            .internalServerError()
            .body(ErrorResponse.from(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }
}
