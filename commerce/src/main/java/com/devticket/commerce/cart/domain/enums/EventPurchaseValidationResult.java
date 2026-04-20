package com.devticket.commerce.cart.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventPurchaseValidationResult {
    AVAILABLE("구매 가능"),
    SALE_ENDED("판매 기한 종료"),
    SOLD_OUT("전체 매진"),
    EVENT_CANCELLED("이벤트 취소"),
    MAX_PER_USER_EXCEEDED("1인당 최대 구매 수량 초과"),
    INSUFFICIENT_STOCK("재고 부족");

    private final String message;
}
