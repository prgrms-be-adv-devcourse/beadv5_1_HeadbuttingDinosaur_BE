package com.devticket.event.domain.exception;

public interface ErrorCode {
    int getStatus();
    String getCode();
    String getMessage();
}