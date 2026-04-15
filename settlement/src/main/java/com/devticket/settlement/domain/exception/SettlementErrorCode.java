package com.devticket.settlement.domain.exception;

import com.devticket.settlement.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {
    SETTLEMENT_NOT_FOUND(404, "SETTLEMENT_001", "정산 내역을 찾을 수 없습니다."),
    SETTLEMENT_BAD_REQUEST(400, "SETTLEMENT_002", "정산 대상 이벤트가 없습니다."),
    SETTLEMENT_MIN_AMMOUNT(400, "SETTLEMENT_003", "최소 정산 금액(10,000원) 미달입니다."),
    FEE_POLICY_NOT_FOUND(404, "SETTLEMENT_004", "수수료 정책이 등록되지 않았습니다.");


    private final int status;
    private final String code;
    private final String message;
}
