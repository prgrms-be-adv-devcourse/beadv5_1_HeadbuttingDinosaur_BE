package com.devticket.payment.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public boolean send(String topic, String key, OutboxEventMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            kafkaTemplate.send(topic, key, json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Outbox] Kafka 발행 실패 — topic={}, messageId={}, error={}",
                            topic, message.messageId(), ex.getMessage());
                    } else {
                        log.info("[Outbox] Kafka 발행 성공 — topic={}, messageId={}, offset={}",
                            topic, message.messageId(),
                            result.getRecordMetadata().offset());
                    }
                });

            return true;
        } catch (JsonProcessingException e) {
            log.error("[Outbox] 메시지 직렬화 실패 — topic={}, messageId={}",
                topic, message.messageId(), e);
            return false;
        }
    }
}
