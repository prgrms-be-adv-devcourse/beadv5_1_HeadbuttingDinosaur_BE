package com.devticket.commerce.order.presentation.consumer;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.order.application.service.RefundOrderService;
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
 * Refund Saga — Order 관련 Kafka 수신 진입점.
 *
 *  - refund.order.cancel      : PAID → REFUND_PENDING
 *  - refund.order.compensate  : REFUND_PENDING → PAID (보상 롤백)
 *  - refund.completed         : REFUND_PENDING → REFUNDED (Saga 최종 확정)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundOrderConsumer {

    private final RefundOrderService refundOrderService;

    @KafkaListener(
        topics = KafkaTopics.REFUND_ORDER_CANCEL,
        groupId = "commerce-refund.order.cancel"
    )
    public void consumeOrderCancel(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        try {
            refundOrderService.processOrderRefundCancel(messageId, record.topic(), record.value());
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            if (isProcessedMessageUniqueConflict(e)) {
                log.warn("[refund.order.cancel] messageId={} processed_message UNIQUE 충돌 — 스킵", messageId);
                ack.acknowledge();
                return;
            }
            throw e;
        }
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_ORDER_COMPENSATE,
        groupId = "commerce-refund.order.compensate"
    )
    public void consumeOrderCompensate(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        try {
            refundOrderService.processOrderCompensate(messageId, record.topic(), record.value());
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            if (isProcessedMessageUniqueConflict(e)) {
                log.warn("[refund.order.compensate] messageId={} processed_message UNIQUE 충돌 — 스킵", messageId);
                ack.acknowledge();
                return;
            }
            throw e;
        }
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_COMPLETED,
        groupId = "commerce-refund.completed"
    )
    public void consumeRefundCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        try {
            refundOrderService.processRefundCompleted(messageId, record.topic(), record.value());
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            if (isProcessedMessageUniqueConflict(e)) {
                log.warn("[refund.completed] messageId={} processed_message UNIQUE 충돌 — 스킵", messageId);
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
                "X-Message-Id 헤더 누락 — Outbox Producer 설정 확인 필요 (kafka-idempotency-guide.md §3-5)");
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
