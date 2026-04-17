package com.devticket.commerce.order.infrastructure.kafka;

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
        } catch (DataIntegrityViolationException e) {
            // processed_message UNIQUE 충돌: 다른 요청이 이미 처리 완료 → 스킵
            log.warn("[stock.deducted] messageId={} UNIQUE 충돌 — 이미 처리 완료, 스킵", messageId);
        }
        ack.acknowledge();
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
        } catch (DataIntegrityViolationException e) {
            // processed_message UNIQUE 충돌: 다른 요청이 이미 처리 완료 → 스킵
            log.warn("[stock.failed] messageId={} UNIQUE 충돌 — 이미 처리 완료, 스킵", messageId);
        }
        ack.acknowledge();
    }

    private UUID extractMessageId(Headers headers) {
        Header header = headers.lastHeader("X-Message-Id");
        if (header == null) {
            throw new IllegalArgumentException(
                "X-Message-Id 헤더 누락 — Outbox Producer 설정 확인 필요 (kafka-idempotency-guide.md §3-5)");
        }
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    }
}
