package com.devticket.commerce.cart.domain.exception;

import com.devticket.commerce.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventErrorCode implements ErrorCode {

    EVENT_NOT_FOUND(404, "EVENT_001", "존재하지 않는 이벤트입니다."),
    // 이벤트 상태 관련 (409 Conflict)
    EVENT_ALREADY_CANCELLED(409, "EVENT_011", "이미 취소된 이벤트입니다."),
    EVENT_ALREADY_ENDED(409, "EVENT_012", "이미 종료된 이벤트입니다."),
    // 구매 검증 실패 (400 Bad Request)
    INVALID_PURCHASE_REQUEST(400, "EVENT_014", "구매 가능한 상태가 아닙니다."),
    // 권한 관련 (403 Forbidden)
    EVENT_FORBIDDEN(403, "EVENT_002", "이벤트 관리 권한이 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}