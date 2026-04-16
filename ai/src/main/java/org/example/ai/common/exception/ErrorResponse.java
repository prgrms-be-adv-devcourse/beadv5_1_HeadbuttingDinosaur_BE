package org.example.ai.common.exception;

import java.util.List;

public class ErrorResponse {
    private int status;
    private String code;
    private String message;
    private String timestamp;
    private List<FieldError> errors;  // 유효성 검증 실패 시에만 포함

    public static class FieldError {
        private String field;
        private String value;
        private String reason;
    }
}
