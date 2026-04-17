package com.devticket.commerce.order.presentation.consumer;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.order.application.service.OrderService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventConsumer {

    private final OrderService orderService;

    /**
     * stock.deducted 수신: Order CREATED → PAYMENT_PENDING 전이
     * groupId: commerce-stock.deducted
     */
    @KafkaListener(
        topics = KafkaTopics.STOCK_DEDUCTED,
        groupId = "commerce-stock.deducted"
    )
    public void consumeStockDeducted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        try {
            orderService.processStockDeducted(messageId, record.topic(), record.value());
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            if (isProcessedMessageUniqueConflict(e)) {
                log.warn("[stock.deducted] messageId={} processed_message UNIQUE 충돌 — 이미 처리 완료, 스킵", messageId);
                ack.acknowledge();
                return;
            }
            throw e;
        }
    }

    /**
     * stock.failed 수신: Order CREATED → FAILED 전이 (재고 부족 보상)
     * groupId: commerce-stock.failed
     */
    @KafkaListener(
        topics = KafkaTopics.STOCK_FAILED,
        groupId = "commerce-stock.failed"
    )
    public void consumeStockFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        try {
            orderService.processStockFailed(messageId, record.topic(), record.value());
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            if (isProcessedMessageUniqueConflict(e)) {
                log.warn("[stock.failed] messageId={} processed_message UNIQUE 충돌 — 이미 처리 완료, 스킵", messageId);
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
