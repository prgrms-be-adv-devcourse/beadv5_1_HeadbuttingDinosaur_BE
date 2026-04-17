package com.devticket.commerce.order.presentation.consumer;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.order.application.usecase.OrderUsecase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedConsumer {

    private final OrderUsecase orderUsecase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "commerce-payment.failed"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            UUID messageId = extractMessageId(record);
            String payload = extractPayload(record.value());

            try {
                orderUsecase.processPaymentFailed(messageId, record.topic(), payload);
            } catch (DataIntegrityViolationException e) {
                log.warn("[PaymentFailedConsumer] UNIQUE 충돌 — 이미 처리된 메시지. messageId={}", messageId);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[PaymentFailedConsumer] 메시지 처리 실패 — topic={}, error={}",
                    record.topic(), e.getMessage(), e);
            throw e;
        }
    }

    private UUID extractMessageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("X-Message-Id");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                log.warn("[PaymentFailedConsumer] 헤더 UUID 파싱 실패, 본문 fallback. header={}",
                        new String(header.value(), StandardCharsets.UTF_8));
            }
        }
        try {
            JsonNode root = objectMapper.readTree(record.value());
            JsonNode messageIdNode = root.get("messageId");
            if (messageIdNode != null && !messageIdNode.isNull()) {
                return UUID.fromString(messageIdNode.asText());
            }
        } catch (Exception e) {
            log.warn("[PaymentFailedConsumer] 본문에서 messageId 추출 실패", e);
        }
        throw new IllegalArgumentException("messageId를 추출할 수 없습니다. topic=" + record.topic());
    }

    private String extractPayload(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode payloadNode = root.get("payload");
            if (payloadNode != null && !payloadNode.isNull()) {
                return payloadNode.asText();
            }
        } catch (Exception ignored) {
        }
        return value;
    }
}
