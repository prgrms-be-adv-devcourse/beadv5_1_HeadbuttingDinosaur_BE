package com.devticket.commerce.ticket.application.service;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketCancelEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketCompensateEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketDoneEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundTicketFailedEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.exception.TicketErrorCode;
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
 * Refund Saga — Commerce 측 Ticket 일괄 상태 전이 처리.
 *
 * 공통 처리: isDuplicate → canTransitionTo 검사 → 비즈니스 → Outbox → markProcessed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundTicketService {

    private final TicketRepository ticketRepository;
    private final OutboxService outboxService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    /**
     * refund.ticket.cancel 수신: 대상 Ticket 일괄 ISSUED → CANCELLED.
     * 일부라도 전이 불가 상태(REFUNDED 등)가 있으면 refund.ticket.failed 발행 → Saga 보상.
     */
    @Transactional
    public void processTicketRefundCancel(UUID messageId, String topic, String payload) {
        if (deduplicationService.isDuplicate(messageId)) {
            log.debug("[refund.ticket.cancel] 중복 메시지 스킵. messageId={}", messageId);
            return;
        }

        RefundTicketCancelEvent event = parsePayload(payload, RefundTicketCancelEvent.class);
        List<UUID> requestedIds = event.ticketIds();

        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new IllegalArgumentException(
                "[refund.ticket.cancel] ticketIds 비어있음. orderId=" + event.orderId());
        }

        List<Ticket> tickets = ticketRepository.findAllByTicketIdIn(requestedIds);
        if (tickets.size() != requestedIds.size()) {
            log.error("[refund.ticket.cancel] 티켓 일부 조회 실패 — 요청={}, 조회={}",
                requestedIds.size(), tickets.size());
            publishTicketFailed(event.orderId(), "Some tickets not found");
            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        boolean anyInvalid = tickets.stream()
            .anyMatch(t -> t.getStatus() != TicketStatus.CANCELLED
                && !t.canTransitionTo(TicketStatus.CANCELLED));

        if (anyInvalid) {
            log.warn("[refund.ticket.cancel] 일부 티켓 전이 불가 — orderId={}", event.orderId());
            publishTicketFailed(event.orderId(), "Ticket state not allowed");
            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        for (Ticket ticket : tickets) {
            if (ticket.getStatus() == TicketStatus.CANCELLED) {
                continue;
            }
            ticket.cancelledTicket();
        }

        publishTicketDone(event.orderId(), requestedIds);
        deduplicationService.markProcessed(messageId, topic);
    }

    /**
     * refund.ticket.compensate 수신: Ticket 일괄 CANCELLED → ISSUED 롤백.
     */
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

    private void publishTicketDone(UUID orderId, List<UUID> ticketIds) {
        outboxService.save(
            orderId.toString(),
            orderId.toString(),
            "REFUND_TICKET_DONE",
            KafkaTopics.REFUND_TICKET_DONE,
            new RefundTicketDoneEvent(orderId, ticketIds, Instant.now())
        );
    }

    private void publishTicketFailed(UUID orderId, String reason) {
        outboxService.save(
            orderId.toString(),
            orderId.toString(),
            "REFUND_TICKET_FAILED",
            KafkaTopics.REFUND_TICKET_FAILED,
            new RefundTicketFailedEvent(orderId, reason, Instant.now())
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
