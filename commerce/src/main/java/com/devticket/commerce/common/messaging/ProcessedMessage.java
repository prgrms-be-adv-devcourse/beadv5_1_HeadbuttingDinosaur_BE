package com.devticket.commerce.common.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "processed_message",
    schema = "commerce",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_processed_message_message_id_topic",
        columnNames = "message_id"
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Outbox message_id (Kafka 헤더 X-Message-Id에서 추출)
    // UNIQUE 제약명 고정 — Consumer의 isProcessedMessageUniqueConflict()가 제약명으로 판별
    @Column(name = "message_id", nullable = false, length = 36)
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
