package com.devticket.event.presentation.consumer;

import com.devticket.event.application.EventService;
import com.devticket.event.common.messaging.KafkaTopics;
import com.devticket.event.domain.exception.StockDeductionException;
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
 * order.created Consumer — 재고 차감 후 stock.deducted / stock.failed 발행
 *
 * <p>처리 순서 (kafka-idempotency-guide.md §4):
 * isDuplicate → 재고 차감 → markProcessed → ack
 *
 * <p>영구 실패(재고 부족): StockDeductionException 캐치 → saveStockFailed() 별도 트랜잭션
 * <p>dedup 레이스: DataIntegrityViolationException 캐치 → 스킵 + ack
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final EventService eventService;

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "event-order.created")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        try {
            eventService.processOrderCreated(messageId, record.topic(), record.value());
        } catch (StockDeductionException ex) {
            // 영구 실패 (재고 부족) → 새 트랜잭션에서 stock.failed Outbox 저장
            log.warn("재고 차감 실패 — orderId={}, eventId={}, reason={}",
                    ex.getOrderId(), ex.getEventId(), ex.getMessage());
            eventService.saveStockFailed(messageId, record.topic(), ex.getOrderId(), ex.getEventId(), ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            // processed_message UNIQUE 충돌: 다른 요청이 이미 처리 완료 → 스킵
            log.warn("order.created dedup 레이스 — messageId={}", messageId);
        }
        ack.acknowledge();
    }

    private UUID extractMessageId(Headers headers) {
        Header header = headers.lastHeader("X-Message-Id");
        if (header == null) {
            throw new IllegalStateException("Kafka 헤더에 X-Message-Id가 없습니다");
        }
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    }
}
