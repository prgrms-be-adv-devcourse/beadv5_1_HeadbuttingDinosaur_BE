package com.devticket.commerce.cart.domain.exception;

import com.devticket.commerce.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventErrorCode implements ErrorCode {

    EVENT_NOT_FOUND(404, "EVENT_001", "존재하지 않는 이벤트입니다.");

    private final int status;
    private final String code;
    private final String message;
}