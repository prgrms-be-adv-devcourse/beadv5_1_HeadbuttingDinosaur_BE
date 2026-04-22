package com.devticket.payment.common.outbox;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class OutboxEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka-producer.send-timeout-ms:2000}")
    private long sendTimeoutMs = 2000L;

    public OutboxEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Kafka 메시지를 동기적으로 발행한다.
     * 발행 실패 시 OutboxPublishException을 던진다.
     */
    public void publish(OutboxEventMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            ProducerRecord<String, String> record =
                new ProducerRecord<>(message.topic(), message.partitionKey(), json);
            record.headers().add("X-Message-Id",
                message.messageId().getBytes(StandardCharsets.UTF_8));

            var result = kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);

            log.info("[Outbox] Kafka 발행 성공 — topic={}, messageId={}, offset={}",
                message.topic(), message.messageId(),
                result.getRecordMetadata().offset());

        } catch (JsonProcessingException e) {
            throw new OutboxPublishException("메시지 직렬화 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Kafka 발행 중 인터럽트 발생", e);
        } catch (TimeoutException | ExecutionException | KafkaException e) {
            throw new OutboxPublishException("Kafka 발행 실패", e);
        }
    }
}
