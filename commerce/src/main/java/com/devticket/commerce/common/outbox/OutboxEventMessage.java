package com.devticket.commerce.common.outbox;

// 스케줄러 → OutboxEventProducer 전달용 VO
public record OutboxEventMessage(
        Long outboxId,
        String messageId,    // Kafka 헤더 X-Message-Id로 전달
        String topic,
        String partitionKey, // Kafka 파티션 키
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
