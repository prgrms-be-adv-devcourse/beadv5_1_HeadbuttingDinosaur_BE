package com.devticket.payment.wallet.application.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(
    UUID orderId,
    UUID userId,
    List<OrderItem> orderItems,   // 재고 복구 대상 목록
    String reason,
    Instant timestamp
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItem(
        UUID eventId,
        int quantity
    ) {}
}
