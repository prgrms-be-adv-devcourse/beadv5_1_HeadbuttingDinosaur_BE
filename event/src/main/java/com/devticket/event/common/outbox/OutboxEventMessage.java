package com.devticket.event.common.outbox;

/**
 * OutboxScheduler → OutboxEventProducer 전달용 Value Object
 */
public record OutboxEventMessage(
        Long outboxId,
        String messageId,
        String topic,
        String partitionKey,
        String payload
) {
    public static OutboxEventMessage from(Outbox outbox) {
        return new OutboxEventMessage(
                outbox.getId(),
                outbox.getMessageId(),
                outbox.getTopic(),
                outbox.getPartitionKey(),
                outbox.getPayload()
        );
    }
}
