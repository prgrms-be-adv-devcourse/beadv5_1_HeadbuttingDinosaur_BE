package com.devticket.event.application;

import com.devticket.event.common.messaging.KafkaTopics;
import com.devticket.event.common.messaging.event.RefundStockDoneEvent;
import com.devticket.event.common.messaging.event.RefundStockFailedEvent;
import com.devticket.event.common.messaging.event.RefundStockRestoreEvent;
import com.devticket.event.common.outbox.OutboxService;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 Saga 의 재고 복구 단계 처리 서비스.
 * Payment 가 발행한 refund.stock.restore 를 수신해 재고를 복구하고,
 * 결과로 refund.stock.done 또는 refund.stock.failed 를 발행한다.
 *
 * <p>payment.failed 경로의 {@link StockRestoreService} 와 책임이 달라 분리:
 *  - payment.failed: 재고 복구만, Outbox 발행 없음
 *  - refund.stock.restore: 재고 복구 + Saga 응답 토픽 발행 필수
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundStockRestoreService {

    private final EventRepository eventRepository;
    private final OutboxService outboxService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    /**
     * refund.stock.restore 를 처리한다.
     *
     * <p>처리 순서: dedup → 재고 복구(정렬된 비관적 락) → refund.stock.done 발행 → markProcessed.
     * 비즈니스 실패(Event 없음, 도메인 위반) 는 본 트랜잭션을 롤백하고
     * 별도 트랜잭션({@link #publishFailedAndMarkProcessed})으로 refund.stock.failed 를 발행한다.
     */
    @Transactional
    public void handleRefundStockRestore(UUID messageId, String topic, String payload) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }

        RefundStockRestoreEvent event = deserialize(payload);

        // eventId 정렬 → 비관적 락 조회 (데드락 방지)
        List<UUID> sortedEventIds = event.items().stream()
            .map(RefundStockRestoreEvent.Item::eventId)
            .distinct()
            .sorted()
            .toList();

        Map<UUID, Event> eventMap = eventRepository.findAllByEventIdInWithLock(sortedEventIds)
            .stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));

        List<RefundStockRestoreEvent.Item> sortedItems = event.items().stream()
            .sorted(Comparator.comparing(RefundStockRestoreEvent.Item::eventId))
            .toList();

        for (RefundStockRestoreEvent.Item item : sortedItems) {
            Event target = eventMap.get(item.eventId());
            if (target == null) {
                throw new EventNotFoundForRefundException(
                    "Event not found for refund stock restore: eventId=" + item.eventId()
                        + ", refundId=" + event.refundId());
            }

            // 정책적 스킵: 이미 종료된 이벤트는 재고 복구 의미 없음 → Saga 진행만 보장
            if (target.getStatus() == EventStatus.CANCELLED
                || target.getStatus() == EventStatus.FORCE_CANCELLED) {
                log.warn("[정책적 스킵] refund 재고 복구 — eventId={}, status={}, refundId={}",
                    item.eventId(), target.getStatus(), event.refundId());
                continue;
            }

            target.restoreStock(item.quantity());
            log.info("[refund 재고 복구] eventId={}, quantity={}, refundId={}, remaining={}",
                item.eventId(), item.quantity(), event.refundId(), target.getRemainingQuantity());
        }

        outboxService.save(
            event.refundId().toString(),
            event.refundId().toString(),
            "REFUND_STOCK_DONE",
            KafkaTopics.REFUND_STOCK_DONE,
            new RefundStockDoneEvent(event.refundId(), event.orderId(), Instant.now())
        );

        deduplicationService.markProcessed(messageId, topic);
    }

    /**
     * 비즈니스 실패 시 Consumer 가 호출. 별도 트랜잭션으로 failed 이벤트를 발행하고 dedup 기록.
     * 본 트랜잭션이 분리되어 있으므로 위 handleRefundStockRestore 의 롤백 영향을 받지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishFailedAndMarkProcessed(UUID messageId, String topic,
        UUID refundId, UUID orderId, String reason) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }

        outboxService.save(
            refundId.toString(),
            refundId.toString(),
            "REFUND_STOCK_FAILED",
            KafkaTopics.REFUND_STOCK_FAILED,
            new RefundStockFailedEvent(refundId, orderId, reason, Instant.now())
        );

        deduplicationService.markProcessed(messageId, topic);
    }

    private RefundStockRestoreEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, RefundStockRestoreEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("RefundStockRestoreEvent 역직렬화 실패", e);
        }
    }

    /**
     * Event 가 존재하지 않는 비즈니스 실패. Consumer 가 잡아 failed 이벤트를 발행한다.
     */
    public static class EventNotFoundForRefundException extends RuntimeException {
        public EventNotFoundForRefundException(String message) {
            super(message);
        }
    }
}
