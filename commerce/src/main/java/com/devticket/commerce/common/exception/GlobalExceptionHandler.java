package com.devticket.commerce.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    //비즈니스 규칙 위반 예외 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity
            .status(e.getErrorCode().getStatus())
            .body(ErrorResponse.from(e.getErrorCode()));
    }

    //DTO 유효성 검증(@Valid) 실패 시 처리
    //클라이언트가 보낸 입력값이 제약 조건(길이, 필수값 등)을 어겼을 때 동작
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.from(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    //예측하지 못한 시스템 오류 발생시
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity
            .internalServerError()
            .body(ErrorResponse.from(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }
}