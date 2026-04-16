package com.devticket.payment.common.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventMessage(
    UUID messageId,
    String eventType,
    String payload,
    Instant timestamp
) {

    public static OutboxEventMessage from(Outbox outbox) {
        return new OutboxEventMessage(
            outbox.getMessageId(),
            outbox.getEventType(),
            outbox.getPayload(),
            Instant.now()
        );
    }
}
