package com.devticket.event.common.exception;

import java.io.IOException;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("MaxUploadSizeExceededException: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("EVENT_023", "이미지 크기는 최대 5MB입니다.", 400));
    }

    /**
     * 클라이언트가 응답 수신 전 연결을 끊은 경우.
     * 서버 결함이 아니고 응답 본문도 송신할 수 없으므로 WARN으로만 로깅.
     */
    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    public void handleClientDisconnect(Exception e) {
        log.warn("ClientDisconnected: {} - {}", e.getClass().getSimpleName(), e.getMessage());
    }

    /**
     * 응답 직렬화 단계 예외. cause 체인에 Broken pipe가 있으면 클라이언트 단절로 간주해 WARN,
     * 그 외(직렬화 실패 등)는 ERROR로 분기.
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotWritable(HttpMessageNotWritableException e) {
        if (isClientDisconnect(e)) {
            log.warn("ClientDisconnected (HttpMessageNotWritable): {}", e.getMessage());
            return null;
        }
        log.error("HttpMessageNotWritableException: ", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("COMMON_006", "서버 내부 오류가 발생했습니다.", 500));
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

    private boolean isClientDisconnect(Throwable t) {
        while (t != null) {
            if (t instanceof ClientAbortException) {
                return true;
            }
            if (t instanceof IOException) {
                String msg = t.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase(Locale.ROOT);
                    if (lower.contains("broken pipe") || lower.contains("connection reset")) {
                        return true;
                    }
                }
            }
            t = t.getCause();
        }
        return false;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        String message = "필수 헤더가 누락되었습니다: " + e.getHeaderName();

        ErrorResponse response = ErrorResponse.of("COMMON_001", message, HttpStatus.BAD_REQUEST.value());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 필수 요청 파라미터 누락 처리
     * 예: GET /internal/events/{id}/validate-purchase 에서 requestedQuantity 파라미터 누락 시
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        log.warn("MissingServletRequestParameterException: parameterName={}", e.getParameterName());
        String message = "필수 요청 파라미터가 누락되었습니다: " + e.getParameterName();
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("COMMON_001", message, HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * 요청 파라미터 타입 변환 실패 처리
     * 예: GET /internal/events/by-seller/{sellerId}?status=on_sale 에서 잘못된 enum값 입력 시
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException: parameterName={}, requiredType={}, value={}",
                e.getName(), e.getRequiredType().getSimpleName(), e.getValue());
        String message = String.format("요청 파라미터 타입이 올바르지 않습니다: %s (필수: %s)",
                e.getValue(), e.getRequiredType().getSimpleName());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("COMMON_001", message, HttpStatus.BAD_REQUEST.value()));
    }
}