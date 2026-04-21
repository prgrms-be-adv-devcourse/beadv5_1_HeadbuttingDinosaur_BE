package com.devticket.commerce.order.application.service;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.refund.EventForceCancelledEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundRequestedEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
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
 * Admin/Seller 이벤트 강제 취소 fan-out.
 * <p>
 * event.force-cancelled 수신 → 해당 eventId 의 PAID Order 조회 → orderId 별 refund.requested 발행 (refundId Commerce 에서 생성, 원장
 * upsert 는 Payment 담당).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundFanoutService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
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

        List<Order> orders = orderRepository.findAllByEventIdAndStatus(
            event.eventId(), OrderStatus.PAID);

        if (orders.isEmpty()) {
            log.info("[event.force-cancelled] PAID 주문 없음 — fan-out 생략. eventId={}", event.eventId());
            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        Instant now = Instant.now();
        String reason = "event-force-cancelled:" + event.eventId();

        for (Order order : orders) {
            // 대상 티켓 — 이 이벤트에 해당하면서 ISSUED 인 것만
            List<Ticket> orderTickets = ticketRepository
                .findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED);
            List<UUID> ticketIds = orderTickets.stream()
                .filter(t -> t.getEventId().equals(event.eventId()))
                .map(Ticket::getTicketId)
                .toList();

            if (ticketIds.isEmpty()) {
                log.warn("[event.force-cancelled] ISSUED 티켓 없음 — skip. orderId={}, eventId={}",
                    order.getOrderId(), event.eventId());
                continue;
            }

            RefundRequestedEvent request = new RefundRequestedEvent(
                UUID.randomUUID(),           // refundId — Commerce 생성
                null,                         // orderRefundId — Payment 가 upsert
                order.getOrderId(),
                order.getUserId(),
                order.getPaymentId(),         // payment.completed 수신 시 기록된 값
                order.getPaymentMethod(),
                ticketIds,
                order.getTotalAmount(),       // 전체 환불 금액
                100,                          // refundRate — 강제 취소는 100%
                true,                         // wholeOrder — 강제 취소는 항상 전체 환불
                reason,
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
