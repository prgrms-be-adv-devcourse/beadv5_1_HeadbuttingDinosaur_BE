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
public class PaymentCompletedConsumer {

    private final OrderUsecase orderUsecase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "commerce-payment.completed"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            // X-Message-Id 헤더 우선, 없으면 본문의 messageId 필드에서 추출
            UUID messageId = extractMessageId(record);
            String payload = extractPayload(record.value());

            try {
                orderUsecase.processPaymentCompleted(messageId, record.topic(), payload);
            } catch (DataIntegrityViolationException e) {
                log.warn("[PaymentCompletedConsumer] UNIQUE 충돌 — 이미 처리된 메시지. messageId={}", messageId);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[PaymentCompletedConsumer] 메시지 처리 실패 — topic={}, error={}",
                    record.topic(), e.getMessage(), e);
            throw e;
        }
    }

    private UUID extractMessageId(ConsumerRecord<String, String> record) {
        // 1. Kafka 헤더에서 추출 시도
        Header header = record.headers().lastHeader("X-Message-Id");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                log.warn("[PaymentCompletedConsumer] 헤더 UUID 파싱 실패, 본문 fallback. header={}",
                        new String(header.value(), StandardCharsets.UTF_8));
            }
        }
        // 2. 본문 wrapper의 messageId 필드에서 추출 (Payment Outbox 구조 대응)
        try {
            JsonNode root = objectMapper.readTree(record.value());
            JsonNode messageIdNode = root.get("messageId");
            if (messageIdNode != null && !messageIdNode.isNull()) {
                return UUID.fromString(messageIdNode.asText());
            }
        } catch (Exception e) {
            log.warn("[PaymentCompletedConsumer] 본문에서 messageId 추출 실패", e);
        }
        throw new IllegalArgumentException("messageId를 추출할 수 없습니다. topic=" + record.topic());
    }

    private String extractPayload(String value) {
        // Outbox wrapper 구조인 경우 payload 필드 추출, 아니면 원본 그대로 반환
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
