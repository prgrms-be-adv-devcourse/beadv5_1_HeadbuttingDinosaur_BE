package org.example.ai.common.exception;

public interface ErrorCode {
    int getStatus();
    String getCode();
    String getMessage();
}
