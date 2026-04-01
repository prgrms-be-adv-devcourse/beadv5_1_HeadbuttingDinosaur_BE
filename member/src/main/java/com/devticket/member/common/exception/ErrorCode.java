package com.devticket.member.common.exception;

public interface ErrorCode {

    int getStatus();

    String getCode();

    String getMessage();
}
