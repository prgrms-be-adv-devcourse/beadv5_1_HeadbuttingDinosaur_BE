package com.devticket.settlement.common.exception;

public interface ErrorCode {

    int getStatus();

    String getCode();

    String getMessage();
}
