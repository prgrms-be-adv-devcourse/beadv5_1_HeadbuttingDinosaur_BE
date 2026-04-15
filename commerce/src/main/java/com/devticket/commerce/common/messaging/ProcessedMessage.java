package com.devticket.commerce.common.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "processed_message", schema = "commerce")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Outbox message_id (Kafka 헤더 X-Message-Id에서 추출)
    // UNIQUE 제약 — 레이스 컨디션 최종 방어선
    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    // 운영 디버깅용
    @Column(nullable = false, length = 128)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Builder
    private ProcessedMessage(String messageId, String topic, Instant processedAt) {
        this.messageId = messageId;
        this.topic = topic;
        this.processedAt = processedAt;
    }
}
