package com.devticket.event.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event → Payment/Commerce: 어드민 강제 취소 이벤트 (event.force-cancelled)
 * Commerce 가 수신해 해당 이벤트의 PAID 주문에 대해 환불 fan-out 을 수행한다.
 */
public record EventForceCancelledEvent(
        UUID eventId,
        UUID sellerId,
        String reason,
        Instant occurredAt
) {}
