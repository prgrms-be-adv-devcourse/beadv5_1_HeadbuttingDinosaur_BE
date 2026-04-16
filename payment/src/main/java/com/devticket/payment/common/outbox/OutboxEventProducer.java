package com.devticket.payment.common.outbox;

import java.util.concurrent.ExecutionException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Kafka 메시지를 동기적으로 발행한다.
     * 발행 실패 시 OutboxPublishException을 던진다.
     */
    public void send(String topic, String key, OutboxEventMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            var result = kafkaTemplate.send(topic, key, json).get();

            log.info("[Outbox] Kafka 발행 성공 — topic={}, messageId={}, offset={}",
                topic, message.messageId(),
                result.getRecordMetadata().offset());

        } catch (JsonProcessingException e) {
            throw new OutboxPublishException("메시지 직렬화 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Kafka 발행 중 인터럽트 발생", e);
        } catch (ExecutionException e) {
            throw new OutboxPublishException("Kafka 발행 실패", e);
        }
    }
}
