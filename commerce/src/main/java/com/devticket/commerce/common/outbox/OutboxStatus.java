package com.devticket.commerce.common.outbox;

public enum OutboxStatus {
    PENDING,  // 발행 대기
    SENT,     // 발행 완료
    FAILED    // 최대 재시도 횟수 소진 — Admin 수동 재발행 필요
}
