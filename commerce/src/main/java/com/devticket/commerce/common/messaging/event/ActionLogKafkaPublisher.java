package com.devticket.commerce.common.messaging.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * action.log 토픽 전용 Kafka Publisher (뼈대 — 본체 구현은 커밋 3).
 * 커밋 3에서 {@code @TransactionalEventListener(AFTER_COMMIT) + @Async} 적용,
 * {@code JsonProcessingException} 포함 전 예외 로깅 후 스킵 (at-most-once).
 */
@Component
public class ActionLogKafkaPublisher {

    private final KafkaTemplate<String, String> actionLogKafkaTemplate;
    private final ObjectMapper objectMapper;

    public ActionLogKafkaPublisher(
            @Qualifier("actionLogKafkaTemplate") KafkaTemplate<String, String> actionLogKafkaTemplate,
            ObjectMapper objectMapper) {
        this.actionLogKafkaTemplate = actionLogKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(ActionLogDomainEvent domain) {
        // 본체 구현: 커밋 3
    }
}
