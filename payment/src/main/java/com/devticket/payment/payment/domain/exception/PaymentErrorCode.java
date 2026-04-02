package com.devticket.payment.payment.domain.exception;

import com.devticket.payment.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    INVALID_PAYMENT_REQUEST(400, "PAYMENT_001", "유효하지 않은 결제 요청입니다."),
    ALREADY_PROCESSED_PAYMENT(409, "PAYMENT_002", "이미 처리된 결제입니다."),
    PG_CONFIRM_FAILED(502, "PAYMENT_003", "PG사 결제 승인에 실패했습니다."),
    PG_TIMEOUT(502, "PAYMENT_004", "PG사 응답 지연으로 결제에 실패했습니다."),
    PG_REFUND_FAILED(502, "PAYMENT_005", "PG사 환불 처리에 실패했습니다."),
    ORDER_COMPLETE_FAILED(502, "PAYMENT_006", "주문 완료 처리(Commerce 연동)에 실패했습니다."),
    PG_CANCEL_FAILED(502,       "PAYMENT_007", "PG 결제 취소에 실패했습니다."),;

    private final int status;
    private final String code;
    private final String message;
}
