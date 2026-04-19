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

/**
 * payment.completed Consumer — Order PAYMENT_PENDING → PAID 전이 + 티켓 발급 + 장바구니 삭제.
 *
 * <p>예외 정책 (at-least-once + DLT, 무한 재시도 아님):
 * <ul>
 *   <li>processed_message UNIQUE 충돌(constraint=uk_processed_message_message_id_topic) → 이미 처리
 *       완료 간주 → ACK + 스킵
 *   <li>그 외 DataIntegrityViolationException → rethrow → KafkaConsumerConfig의 ExponentialBackOff
 *       (2→4→8초, 3회) 재시도 → 소진 시 {topic}.DLT 이동
 *   <li>DLT 이동 후 수동 조치 (Admin API 또는 재처리 워커)
 * </ul>
 */
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
        // X-Message-Id 헤더 우선, 없으면 본문의 messageId 필드에서 추출
        UUID messageId = extractMessageId(record);
        String payload = extractPayload(record.value());

        try {
            orderUsecase.processPaymentCompleted(messageId, record.topic(), payload);
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            if (isProcessedMessageUniqueConflict(e)) {
                log.warn("[PaymentCompletedConsumer] processed_message UNIQUE 충돌 — 이미 처리 완료, 스킵. messageId={}",
                        messageId);
                ack.acknowledge();
                return;
            }
            throw e;
        }
    }

    private boolean isProcessedMessageUniqueConflict(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                return "uk_processed_message_message_id_topic".equals(constraintName);
            }
            cause = cause.getCause();
        }
        return false;
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
