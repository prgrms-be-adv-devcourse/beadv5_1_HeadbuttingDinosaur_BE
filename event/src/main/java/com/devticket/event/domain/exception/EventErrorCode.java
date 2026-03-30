package com.devticket.event.domain.exception;

import com.devticket.event.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventErrorCode implements ErrorCode {

    EVENT_NOT_FOUND(404, "EVENT_001", "존재하지 않는 이벤트입니다."),
    INVALID_REQUEST(400, "EVENT_002", "요청 값이 올바르지 않습니다."),
    INVALID_PRICE(400, "EVENT_003", "이벤트 가격은 0원 이상 9,999,999원 이하여야 합니다."),
    INVALID_QUANTITY(400, "EVENT_004", "이벤트 참여 인원은 5명 이상 9,999명 이하여야 합니다."),
    INVALID_EVENT_DATES(400, "EVENT_005", "판매 시작일, 종료일, 행사일의 순서가 올바르지 않습니다."),
    OUT_OF_STOCK(409, "EVENT_006", "티켓 잔여 수량이 부족합니다."),
    CANNOT_CHANGE_STATUS(400, "EVENT_007", "변경할 수 없는 이벤트 상태입니다."),
    UNAUTHORIZED_SELLER(403, "EVENT_008", "해당 이벤트에 대한 권한이 없습니다."),
    REGISTRATION_TIME_EXCEEDED(400, "EVENT_005", "이벤트는 판매 시작일 기준 3일 전까지 등록 가능합니다."),
    INVALID_SALE_PERIOD(400, "EVENT_014", "판매 시작 시각은 판매 종료 시각 이전이어야 합니다."),
    INVALID_EVENT_DATE(400, "EVENT_015", "판매 종료 시각은 행사 일시 이전이어야 합니다."),
    MAX_QUANTITY_EXCEEDED(400, "EVENT_016", "인당 최대 구매 수량은 총 수량을 초과할 수 없습니다."),
    TOTAL_QUANTITY_BELOW_SOLD(400, "EVENT_017", "총 수량은 이미 판매된 수량 이하로 줄일 수 없습니다."),
    INVALID_STOCK_QUANTITY(400, "EVENT_018", "재고 변경 수량은 1 이상이어야 합니다."),
    PURCHASE_NOT_ALLOWED(409, "EVENT_019", "현재 구매 불가능한 이벤트입니다.");

    private final int status;
    private final String code;
    private final String message;
}