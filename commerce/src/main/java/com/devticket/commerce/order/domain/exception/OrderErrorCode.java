package com.devticket.commerce.order.domain.exception;

import com.devticket.commerce.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(400, "ORDER_001", "존재하지 않는 주문입니다."),
    ALREADY_PAID_ORDER(400, "ORDER_002", "이미 결제 완료된 주문입니다."),
    ALREADY_CANCELLED_ORDER(400, "ORDER_003", "이미 취소된 주문입니다."),
    ORDER_FORBIDDEN(400, "ORDER_004", "해당 주문의 접근 권한이 없습니다."),
    OUT_OF_STOCK(400, "ORDER_005", "주문 생성에 실패했습니다. (재고 부족)"),
    SUSPENDED_ACCOUNT(400, "ORDER_006", "정지된 계정으로 주문할 수 없습니다."),
    INVALID_ORDER_AMOUNT(400, "ORDER_007", "주문 금액은 0원 이상이어야 합니다."),
    CANNOT_COMPLETE_PAYMENT(400, "ORDER_008", "결제 완료 처리가 불가능한 주문 상태입니다."),
    CANNOT_CHANGE_TO_PENDING(400, "ORDER_009", "결제 대기 상태로 변경할 수 없는 주문입니다."),
    CANNOT_CHANGE_AMOUNT_AFTER_PAID(400, "ORDER_010", "결제가 완료된 주문은 수량을 변경할 수 없습니다."),
    INVALID_QUANTITY(400, "ORDER_011", "유효하지 않은 주문 수량입니다.");

    private final int status;
    private final String code;
    private final String message;

}