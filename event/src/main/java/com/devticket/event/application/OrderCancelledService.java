package com.devticket.event.application;

import com.devticket.event.common.messaging.event.OrderCancelledEvent;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelledService {
    private final EventRepository eventRepository;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void restoreStockForOrderCancelled(UUID messageId, String topic, String payload) {
        // Step 1. Dedup 체크
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }

        OrderCancelledEvent event = deserializeOrderCancelled(payload);
        // eventId 정렬 → 비관적 락 조회 (데드락 방지)
        List<UUID> sortedEventIds = event.orderItems().stream()
            .map(OrderCancelledEvent.OrderItem::eventId)
            .distinct()
            .sorted()
            .toList();

        Map<UUID, Event> eventMap = eventRepository.findAllByEventIdInWithLock(sortedEventIds)
            .stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));

        // 정렬된 순서로 처리
        List<OrderCancelledEvent.OrderItem> sortedItems = event.orderItems().stream()
            .sorted(Comparator.comparing(OrderCancelledEvent.OrderItem::eventId))
            .toList();

        for (OrderCancelledEvent.OrderItem item : sortedItems) {
            Event targetEvent = eventMap.get(item.eventId());
            if (targetEvent == null) {
                throw new IllegalStateException(
                    "Event not found for stock restore: eventId=" + item.eventId()
                        + ", orderId=" + event.orderId());
            }

            // Step 2. EventStatus 검증 — CANCELLED/FORCE_CANCELLED면 정책적 스킵
            if (targetEvent.getStatus() == EventStatus.CANCELLED
                || targetEvent.getStatus() == EventStatus.FORCE_CANCELLED) {
                log.warn("[정책적 스킵] order.cancelled 재고 복구 생략 — eventId={}, status={}, orderId={}",
                    item.eventId(), targetEvent.getStatus(), event.orderId());
                continue;
            }

            // Step 3. 비즈니스 로직 — 재고 복구
            targetEvent.restoreStock(item.quantity());
            log.info("[재고 복구] eventId={}, quantity={}, orderId={}, remaining={}",
                item.eventId(), item.quantity(), event.orderId(), targetEvent.getRemainingQuantity());
        }

        // Step 4. Dedup 기록 (같은 트랜잭션)
        deduplicationService.markProcessed(messageId, topic);
    }

    private OrderCancelledEvent deserializeOrderCancelled(String payload) {
        try {
            return objectMapper.readValue(payload, OrderCancelledEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("OrderCancelledEvent 역직렬화 실패", e);
        }
    }
}
