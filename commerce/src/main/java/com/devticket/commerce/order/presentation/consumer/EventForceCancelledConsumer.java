package com.devticket.commerce.order.presentation.consumer;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.PayloadExtractor;
import com.devticket.commerce.order.application.service.RefundFanoutService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Admin 이벤트 강제 취소 / Seller 이벤트 취소 fan-out 진입점.
 * <p>
 * event.force-cancelled 수신 → 해당 eventId 의 PAID Order 들에 대해 refund.requested fan-out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventForceCancelledConsumer {

    private final RefundFanoutService refundFanoutService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.EVENT_FORCE_CANCELLED,
        groupId = "commerce-event.force-cancelled"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        String payload = PayloadExtractor.extract(objectMapper, record.value());
        try {
            refundFanoutService.processEventForceCancelled(messageId, record.topic(), payload);
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            if (isProcessedMessageUniqueConflict(e)) {
                log.warn("[event.force-cancelled] messageId={} processed_message UNIQUE 충돌 — 스킵", messageId);
                ack.acknowledge();
                return;
            }
            throw e;
        }
    }

    private UUID extractMessageId(Headers headers) {
        Header header = headers.lastHeader("X-Message-Id");
        if (header == null) {
            throw new IllegalArgumentException(
                "X-Message-Id 헤더 누락 — Outbox Producer 설정 확인 필요");
        }
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
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
}
