package com.devticket.commerce.ticket.application.service;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketCancelEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketCompensateEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketDoneEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketFailedEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.exception.TicketErrorCode;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refund Saga — Commerce 측 Ticket 일괄 상태 전이.
 * <p>
 * refund.ticket.done 은 Stock 복구 단계 연결용으로 eventId·quantity 를 포함해 발행. 동일 오더 내 티켓이 여러 이벤트에 걸쳐 있으면 eventId 별로 메시지를 분할 발행.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundTicketService {

    private final TicketRepository ticketRepository;
    private final OutboxService outboxService;
    private final MessageDeduplicationService deduplicationService;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processTicketRefundCancel(UUID messageId, String topic, String payload) {
        if (deduplicationService.isDuplicate(messageId)) {
            log.debug("[refund.ticket.cancel] 중복 메시지 스킵. messageId={}", messageId);
            return;
        }

        RefundTicketCancelEvent event = parsePayload(payload, RefundTicketCancelEvent.class);
        List<UUID> requestedIds = event.ticketIds();

        List<Ticket> tickets;

        // wholeOrder=true AND ticketIds 비어있음 → Order 의 ISSUED 티켓 전체 조회
        if (requestedIds == null || requestedIds.isEmpty()) {
            if (!event.wholeOrder()) {
                throw new IllegalArgumentException(
                    "[refund.ticket.cancel] ticketIds 비어있음 + wholeOrder=false. orderId=" + event.orderId());
            }
            Order order = orderRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
            tickets = ticketRepository.findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED);
            if (tickets.isEmpty()) {
                log.warn("[refund.ticket.cancel] ISSUED 티켓 없음 — wholeOrder 이지만 취소할 티켓 없음. orderId={}",
                    event.orderId());
                publishTicketFailed(event.refundId(), event.orderId(), "No ISSUED tickets for wholeOrder refund");
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            log.info("[refund.ticket.cancel] wholeOrder — Order 의 ISSUED 티켓 {}건 조회. orderId={}",
                tickets.size(), event.orderId());
        } else {
            // 부분 환불 — 요청된 ticketIds 만
            tickets = ticketRepository.findAllByTicketIdIn(requestedIds);
            if (tickets.size() != requestedIds.size()) {
                log.error("[refund.ticket.cancel] 티켓 일부 조회 실패 — 요청={}, 조회={}",
                    requestedIds.size(), tickets.size());
                publishTicketFailed(event.refundId(), event.orderId(), "Some tickets not found");
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
        }

        boolean anyInvalid = tickets.stream()
            .anyMatch(t -> t.getStatus() != TicketStatus.CANCELLED
                && !t.canTransitionTo(TicketStatus.CANCELLED));

        if (anyInvalid) {
            log.warn("[refund.ticket.cancel] 일부 티켓 전이 불가 — orderId={}", event.orderId());
            publishTicketFailed(event.refundId(), event.orderId(), "Ticket state not allowed");
            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        for (Ticket ticket : tickets) {
            if (ticket.getStatus() == TicketStatus.CANCELLED) {
                continue;
            }
            ticket.cancelledTicket();
        }

        // eventId 별 (eventId, quantity) 를 items 리스트로 묶어 refund.ticket.done 1건 발행.
        // 다중 이벤트 오더에서도 Payment 가 단일 stock.restore 로 재고 복구를 일괄 요청할 수 있도록.
        Map<UUID, List<Ticket>> byEvent = tickets.stream()
            .collect(Collectors.groupingBy(Ticket::getEventId));

        List<RefundTicketDoneEvent.Item> items = byEvent.entrySet().stream()
            .map(e -> new RefundTicketDoneEvent.Item(e.getKey(), e.getValue().size()))
            .toList();
        List<UUID> allTicketIds = tickets.stream().map(Ticket::getTicketId).toList();

        publishTicketDone(event.refundId(), event.orderId(), allTicketIds, items);

        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void processTicketCompensate(UUID messageId, String topic, String payload) {
        if (deduplicationService.isDuplicate(messageId)) {
            log.debug("[refund.ticket.compensate] 중복 메시지 스킵. messageId={}", messageId);
            return;
        }

        RefundTicketCompensateEvent event = parsePayload(payload, RefundTicketCompensateEvent.class);
        List<UUID> requestedIds = event.ticketIds();

        if (requestedIds == null || requestedIds.isEmpty()) {
            log.warn("[refund.ticket.compensate] ticketIds 비어있음 — 스킵. orderId={}", event.orderId());
            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        List<Ticket> tickets = ticketRepository.findAllByTicketIdIn(requestedIds);
        if (tickets.isEmpty()) {
            throw new BusinessException(TicketErrorCode.TICKET_NOT_FOUND);
        }

        for (Ticket ticket : tickets) {
            if (ticket.getStatus() == TicketStatus.ISSUED) {
                continue;
            }
            if (!ticket.canTransitionTo(TicketStatus.ISSUED)) {
                log.warn("[refund.ticket.compensate] 티켓 롤백 불가 — ticketId={}, status={}",
                    ticket.getTicketId(), ticket.getStatus());
                continue;
            }
            ticket.restoreToIssued();
        }

        deduplicationService.markProcessed(messageId, topic);
    }

    //---- 내부 헬퍼 ----

    private void publishTicketDone(UUID refundId, UUID orderId, List<UUID> ticketIds,
        List<RefundTicketDoneEvent.Item> items) {
        outboxService.save(
            orderId.toString(),
            orderId.toString(),
            "REFUND_TICKET_DONE",
            KafkaTopics.REFUND_TICKET_DONE,
            new RefundTicketDoneEvent(refundId, orderId, ticketIds, items, Instant.now())
        );
    }

    private void publishTicketFailed(UUID refundId, UUID orderId, String reason) {
        outboxService.save(
            orderId.toString(),
            orderId.toString(),
            "REFUND_TICKET_FAILED",
            KafkaTopics.REFUND_TICKET_FAILED,
            new RefundTicketFailedEvent(refundId, orderId, reason, Instant.now())
        );
    }

    private <T> T parsePayload(String payload, Class<T> clazz) {
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Kafka 메시지 역직렬화 실패: " + clazz.getSimpleName(), e);
        }
    }
}
