package com.devticket.settlement.domain.model;

public enum SettlementStatus {
    COMPLETED,          // 정산 완료 (최소 정산금액 충족)
    PENDING_MIN_AMOUNT, // 정산 보류 (최소 정산금액 미달, 다음 달 이월)
    CANCELLED           // 정산 취소
}
