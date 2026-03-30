package com.devticket.payment.wallet.domain.exception;

import com.devticket.payment.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements ErrorCode {

    INSUFFICIENT_BALANCE(409, "WALLET_001", "예치금 잔액이 부족합니다."),
    INVALID_CHARGE_AMOUNT(400, "WALLET_002", "충전 금액은 1,000원 이상 50,000원 이하여야 합니다."),
    DAILY_CHARGE_LIMIT_EXCEEDED(400, "WALLET_003", "일일 충전 한도(1,000,000원)를 초과했습니다."),
    REFUNDABLE_BALANCE_NOT_FOUND(400, "WALLET_004", "환불 가능한 예치금이 없습니다."),
    WALLET_NOT_FOUND(404, "WALLET_005", "예치금 지갑을 찾을 수 없습니다."),
    CHARGE_AMOUNT_MISMATCH(400, "WALLET_006", "충전 금액이 일치하지 않습니다."),
    CHARGE_NOT_FOUND(404, "WALLET_007", "충전 요청을 찾을 수 없습니다."),
    CHARGE_NOT_PENDING(409, "WALLET_008", "대기 상태가 아닌 충전 건입니다.");

    private final int status;
    private final String code;
    private final String message;
}
