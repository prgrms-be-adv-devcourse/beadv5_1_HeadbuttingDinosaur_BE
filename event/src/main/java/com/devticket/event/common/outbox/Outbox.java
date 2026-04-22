package com.devticket.event.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbox 패턴 — 비즈니스 트랜잭션과 Kafka 발행을 원자적으로 보장
 *
 * <p>생성 규칙:
 * <ul>
 *   <li>messageId는 생성 시 UUID.randomUUID()로 단 한 번 고정 — 재발행 시에도 변경하지 않음</li>
 *   <li>Outbox INSERT는 반드시 비즈니스 로직과 단일 @Transactional 경계 안에 위치</li>
 *   <li>OutboxScheduler가 발행 후 messageId를 Kafka 헤더 X-Message-Id에 포함</li>
 * </ul>
 */
@Entity
@Table(name = "outbox", schema = "event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox {

    /** 재시도 최대 횟수 — 지수 백오프 6회 (즉시/1/2/4/8/16초, 누적 31초) */
    private static final int MAX_RETRIES = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kafka 헤더 X-Message-Id로 전달 — Consumer dedup 키 */
    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String messageId;

    /** 비즈니스 키 UUID — 운영 추적용 (orderId, eventId 등) */
    @Column(nullable = false, length = 36)
    private String aggregateId;

    /** Kafka Partition Key — orderId 또는 eventId */
    @Column(nullable = false, length = 36)
    private String partitionKey;

    /** 이벤트 유형 식별자 (ORDER_CREATED, STOCK_DEDUCTED 등) */
    @Column(nullable = false, length = 128)
    private String eventType;

    /** Kafka 토픽명 */
    @Column(nullable = false, length = 128)
    private String topic;

    /** JSON 직렬화된 이벤트 페이로드 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer retryCount;

    /** 다음 재시도 시각 — null이면 즉시 처리 대상 */
    @Column
    private Instant nextRetryAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant sentAt;

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
        this.createdAt = Instant.now();
    }

    public static Outbox create(String aggregateId, String partitionKey,
                                String eventType, String topic, String payload) {
        return new Outbox(aggregateId, partitionKey, eventType, topic, payload);
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * 발행 실패 처리 — 지수 백오프 스케줄 계산
     * 시도 횟수: 6회 (즉시→1→2→4→8→16초), 초과 시 FAILED
     */
    public void markFailed() {
        this.retryCount++;
        if (this.retryCount >= MAX_RETRIES) {
            this.status = OutboxStatus.FAILED;
        } else {
            long delaySec = (long) Math.pow(2, this.retryCount - 1);
            this.nextRetryAt = Instant.now().plusSeconds(delaySec);
        }
    }
}
