package com.devticket.payment.refund.domain.exception;

import com.devticket.payment.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RefundErrorCode implements ErrorCode {

    REFUND_NOT_AVAILABLE(400, "REFUND_001", "환불 가능 기간이 아닙니다. (행사 3일 이내)"),
    ALREADY_REFUNDED(409, "REFUND_002", "이미 환불된 주문입니다."),
    REFUND_ALREADY_IN_PROGRESS(409, "REFUND_003", "이미 환불 요청이 진행 중입니다."),
    REFUND_NOT_FOUND(404, "REFUND_004", "환불 내역을 찾을 수 없습니다."),
    REFUND_INVALID_REQUEST(400, "REFUND_005", "잘못된 환불 요청입니다."),
    PAYMENT_NOT_FOUND(404, "REFUND_006", "결제 정보를 찾을 수 없습니다."),
    TICKET_NOT_FOUND(404, "REFUND_007", "존재하지 않는 티켓입니다."),
    EVENT_NOT_FOUND(404, "REFUND_008", "존재하지 않는 이벤트입니다.");

    private final int status;
    private final String code;
    private final String message;
}
