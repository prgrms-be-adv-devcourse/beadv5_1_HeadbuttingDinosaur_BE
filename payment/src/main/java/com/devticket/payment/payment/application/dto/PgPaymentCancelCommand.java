package com.devticket.payment.payment.application.dto;

public record PgPaymentCancelCommand(
    String paymentKey,
    int cancelAmount,
    String cancelReason,
    String idempotencyKey
) {
    // 편의 생성자 — 기존 호출부 호환 (idempotencyKey 없이)
    public PgPaymentCancelCommand(String paymentKey, int cancelAmount, String cancelReason) {
        this(paymentKey, cancelAmount, cancelReason, null);
    }
}
