package com.devticket.event.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventErrorCode implements ErrorCode {

    EVENT_NOT_FOUND(404, "EVENT_001", "존재하지 않는 이벤트입니다."),
    INVALID_PRICE(400, "EVENT_003", "이벤트 가격은 0원 이상 9,999,999원 이하여야 합니다."),
    INVALID_QUANTITY(400, "EVENT_004", "이벤트 참여 인원은 5명 이상 9,999명 이하여야 합니다."),
    INVALID_EVENT_DATES(400, "EVENT_005", "판매 시작일, 종료일, 행사일의 순서가 올바르지 않습니다."),
    OUT_OF_STOCK(409, "EVENT_006", "티켓 잔여 수량이 부족합니다."),
    CANNOT_CHANGE_STATUS(400, "EVENT_007", "변경할 수 없는 이벤트 상태입니다."),
    UNAUTHORIZED_SELLER(403, "EVENT_008", "해당 이벤트에 대한 권한이 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}