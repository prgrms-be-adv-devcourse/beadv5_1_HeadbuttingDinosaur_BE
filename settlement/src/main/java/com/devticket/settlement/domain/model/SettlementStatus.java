package com.devticket.settlement.domain.model;

public enum SettlementStatus {
    CONFIRMED,          // 정산서 생성 완료. 지급 대기 상태.
    PENDING_MIN_AMOUNT, // 지급 보류 (최소 정산금액 1만원 미달, 다음 달 이월)
    CANCELLED,          // 정산서 취소처리
    PAID,               // 지급완료
    PAID_FAILED         // 지급실패 (관리자 재시도 대상)
}
