package com.devticket.payment.common.outbox;

import java.time.Instant;

public record OutboxEventMessage(
    String messageId,
    String eventType,
    String topic,
    String partitionKey,
    String payload,
    Instant timestamp
) {

    public static OutboxEventMessage from(Outbox outbox) {
        String key = outbox.getPartitionKey() != null
            ? outbox.getPartitionKey()
            : outbox.getAggregateId();
        return new OutboxEventMessage(
            outbox.getMessageId(),
            outbox.getEventType(),
            outbox.getTopic(),
            key,
            outbox.getPayload(),
            Instant.now()
        );
    }
}
