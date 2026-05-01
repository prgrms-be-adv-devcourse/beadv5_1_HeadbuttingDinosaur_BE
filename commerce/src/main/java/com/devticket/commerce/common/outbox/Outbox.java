package com.devticket.commerce.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Table(name = "outbox", schema = "commerce")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UUID.randomUUID()로 생성 — Kafka 헤더 X-Message-Id로 전달, Consumer dedup 키
    // 재발행 시에도 변경하지 않음 — 같은 row를 읽으므로 동일 ID 유지
    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    // 비즈니스 키 UUID (orderId 등) — 운영 추적용
    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    // Kafka 파티션 키 — orderId 기준 Saga 순서 보장 (aggregate_id와 다를 수 있음)
    @Column(name = "partition_key", nullable = false, length = 36)
    private String partitionKey;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    // 지수 백오프 기반 다음 재시도 시각 — null이면 즉시 처리 대상
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Builder
    private Outbox(String aggregateId, String partitionKey, String eventType,
            String topic, String payload) {
        this.messageId = UUID.randomUUID().toString();
        this.aggregateId = aggregateId;
        this.partitionKey = partitionKey;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = null;
    }

    public static Outbox create(String aggregateId, String partitionKey,
            String eventType, String topic, String payload) {
        return Outbox.builder()
                .aggregateId(aggregateId)
                .partitionKey(partitionKey)
                .eventType(eventType)
                .topic(topic)
                .payload(payload)
                .build();
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    // 지수 백오프: 즉시→1→2→4→8→16초, 6회 초과 시 FAILED
    public void markFailed() {
        this.retryCount++;
        if (this.retryCount >= 6) {
            this.status = OutboxStatus.FAILED;
            return;
        }
        long delaySeconds = (long) Math.pow(2, this.retryCount - 1); // 1→2→4→8→16
        this.nextRetryAt = Instant.now().plusSeconds(delaySeconds);
    }
}
