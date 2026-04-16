package com.devticket.payment.wallet.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PaymentFailedEvent(
    UUID orderId,
    UUID userId,
    List<OrderItem> orderItems,   // 재고 복구 대상 목록
    String reason,
    Instant timestamp
) {
    public record OrderItem(
        UUID eventId,
        int quantity
    ) {}
}
