package com.devticket.event.common.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Commerce → Event: 주문 생성 이벤트 (order.created)
 * Event 서비스가 수신하여 재고 차감 처리
 */
public record OrderCreatedEvent(
        UUID orderId,
        UUID userId,
        List<OrderItem> orderItems,
        int totalAmount,
        Instant timestamp
) {
    public record OrderItem(
            UUID eventId,
            int quantity
    ) {}
}
