package com.devticket.event.presentation.dto.internal;

public enum PurchaseUnavailableReason {
    SALE_ENDED,             // 판매 종료
    SOLD_OUT,               // 매진
    EVENT_CANCELLED,        // 이벤트 취소됨
    MAX_PER_USER_EXCEEDED,  // 인당 최대 구매 수 초과
    INSUFFICIENT_STOCK      // 요청 수량 > 잔여 수량
}
