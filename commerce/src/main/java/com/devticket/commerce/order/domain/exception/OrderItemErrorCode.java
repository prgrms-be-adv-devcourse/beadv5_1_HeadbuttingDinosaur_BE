package com.devticket.commerce.order.domain.exception;

import com.devticket.commerce.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderItemErrorCode implements ErrorCode {

    EXCEED_MAX_QUANTITY(400, "ORDER_ITEM_001", "주문 가능한 수량 범위를 벗어났습니다.");
    
    private final int status;
    private final String code;
    private final String message;

}