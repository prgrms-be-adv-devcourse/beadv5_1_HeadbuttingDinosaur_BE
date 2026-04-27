package com.devticket.payment.common.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "processed_message")
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public static ProcessedMessage of(String messageId, String topic) {
        ProcessedMessage pm = new ProcessedMessage();
        pm.messageId = messageId;
        pm.topic = topic;
        pm.processedAt = LocalDateTime.now();
        return pm;
    }
}
