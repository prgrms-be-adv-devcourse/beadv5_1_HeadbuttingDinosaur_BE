package com.devticket.payment.common.outbox;

import com.devticket.payment.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "outbox", schema = "payment", indexes = {
    @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
})
public class Outbox extends BaseEntity {

    private static final int MAX_RETRY = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "partition_key", length = 36)
    private String partitionKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public static Outbox create(String aggregateId, String partitionKey,
                                String eventType, String topic, String payload) {
        Outbox outbox = new Outbox();
        outbox.aggregateId = aggregateId;
        outbox.partitionKey = partitionKey;
        outbox.eventType = eventType;
        outbox.topic = topic;
        outbox.payload = payload;
        outbox.status = OutboxStatus.PENDING;
        outbox.messageId = UUID.randomUUID().toString();
        outbox.retryCount = 0;
        return outbox;
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed() {
        this.retryCount++;
        if (this.retryCount >= MAX_RETRY) {
            this.status = OutboxStatus.FAILED;
        } else {
            long backoffSeconds = 1L << (this.retryCount - 1);
            this.nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
        }
    }

    public boolean isPending() {
        return this.status == OutboxStatus.PENDING;
    }
}
