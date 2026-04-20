package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.UUID;

// Event 서비스 → Commerce: Admin 강제 취소 / Seller 취소 fan-out 트리거
public record EventForceCancelledEvent(
        UUID eventId,
        String reason,
        Instant timestamp
) {}
