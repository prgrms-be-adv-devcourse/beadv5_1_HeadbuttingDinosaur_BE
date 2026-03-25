package com.devticket.event.domain.exception;

import com.devticket.event.presentation.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ErrorResponse.of(errorCode));
    }

    /**
     * @Valid, @Validated 유효성 검증 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("ValidationException: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.ofValidation("COMMON_001", e.getBindingResult()));
    }

    /**
     * 처리되지 않은 모든 서버 내부 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("UnhandledException: ", e); // 500 에러는 스택 트레이스 로깅
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("COMMON_006", "서버 내부 오류가 발생했습니다.", 500));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        String message = "필수 헤더가 누락되었습니다: " + e.getHeaderName();

        ErrorResponse response = ErrorResponse.of("COMMON_001", message, HttpStatus.BAD_REQUEST.value());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}