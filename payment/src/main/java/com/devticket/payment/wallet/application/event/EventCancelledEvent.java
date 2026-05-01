package com.devticket.payment.wallet.application.event;

import com.devticket.payment.wallet.domain.enums.CancelledBy;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record EventCancelledEvent(
    UUID eventId,
    UUID sellerId,
    CancelledBy cancelledBy,
    UUID adminId,           // cancelledBy=ADMIN 시에만 존재 (nullable)
    Instant timestamp
) {

}
