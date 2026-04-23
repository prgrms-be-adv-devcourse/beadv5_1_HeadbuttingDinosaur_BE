package com.devticket.event.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event → Payment: 판매자 자발적 판매 중지 이벤트 (event.sale-stopped)
 * 어드민 강제 취소(event.force-cancelled) 와는 환불 정책이 달라 토픽을 분리한다.
 */
public record EventSaleStoppedEvent(
        UUID eventId,
        UUID sellerId,
        Instant occurredAt
) {}
