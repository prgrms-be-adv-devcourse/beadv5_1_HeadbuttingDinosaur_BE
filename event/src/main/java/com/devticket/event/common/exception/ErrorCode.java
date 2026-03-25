package com.devticket.event.common.exception;

public interface ErrorCode {
    int getStatus();
    String getCode();
    String getMessage();
}