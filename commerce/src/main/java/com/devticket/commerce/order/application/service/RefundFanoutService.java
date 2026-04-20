package com.devticket.commerce.order.application.service;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.refund.EventForceCancelledEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundRequestedEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin 이벤트 강제 취소 / Seller 이벤트 취소 fan-out 처리.
 *
 * event.force-cancelled 수신 시 해당 eventId 의 PAID Order 를 조회하여
 * orderId 별로 refund.requested Outbox 발행 — 개별 Saga 진입은 Payment Orchestrator 담당.
 *
 * 멱등성: 수신자(Payment Orchestrator) 측 dedup 에 위임.
 * 본 서비스 자체 진입은 event.force-cancelled 메시지 단위로 이미 dedup 됨.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundFanoutService {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processEventForceCancelled(UUID messageId, String topic, String payload) {
        if (deduplicationService.isDuplicate(messageId)) {
            log.debug("[event.force-cancelled] 중복 메시지 스킵. messageId={}", messageId);
            return;
        }

        EventForceCancelledEvent event = parsePayload(payload, EventForceCancelledEvent.class);

        // 대량 케이스 대비 페이지네이션은 후속 개선 — 현재는 단일 배치 처리.
        List<Order> orders = orderRepository.findAllByEventIdAndStatus(
            event.eventId(), OrderStatus.PAID);

        if (orders.isEmpty()) {
            log.info("[event.force-cancelled] PAID 주문 없음 — fan-out 생략. eventId={}", event.eventId());
            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        Instant now = Instant.now();
        for (Order order : orders) {
            RefundRequestedEvent request = new RefundRequestedEvent(
                order.getOrderId(),
                order.getUserId(),
                event.reason(),
                null,
                now
            );
            outboxService.save(
                order.getOrderId().toString(),
                order.getOrderId().toString(),
                "REFUND_REQUESTED",
                KafkaTopics.REFUND_REQUESTED,
                request
            );
        }

        log.info("[event.force-cancelled] fan-out 완료 — eventId={}, count={}",
            event.eventId(), orders.size());
        deduplicationService.markProcessed(messageId, topic);
    }

    private <T> T parsePayload(String payload, Class<T> clazz) {
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Kafka 메시지 역직렬화 실패: " + clazz.getSimpleName(), e);
        }
    }
}
