package com.devticket.commerce.common.exception;

public interface ErrorCode {

    int getStatus();

    String getCode();

    String getMessage();
}
