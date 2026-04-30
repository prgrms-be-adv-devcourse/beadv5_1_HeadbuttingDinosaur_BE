package com.devticket.commerce.order.application.service;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.refund.RefundCompletedEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundOrderCancelEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundOrderCompensateEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundOrderDoneEvent;
import com.devticket.commerce.common.messaging.event.refund.RefundOrderFailedEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
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
 * Refund Saga — Commerce 측 Order 상태 전이 처리.
 * <p>
 * 공통 처리 순서: isDuplicate → canTransitionTo(3분류) → 비즈니스 → Outbox → markProcessed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundOrderService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final OutboxService outboxService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    /**
     * refund.order.cancel 수신: PAID → REFUND_PENDING 전이 후 refund.order.done 발행. 실패 시 refund.order.failed 발행으로 Saga 중단.
     */
    @Transactional
    public void processOrderRefundCancel(UUID messageId, String topic, String payload) {
        log.info("[RefundOrderService] processOrderRefundCancel start. messageId={}, topic={}, payload={}",
            messageId, topic, payload);
        if (deduplicationService.isDuplicate(messageId)) {
            log.debug("[refund.order.cancel] 중복 메시지 스킵. messageId={}", messageId);
            return;
        }

        RefundOrderCancelEvent event = parsePayload(payload, RefundOrderCancelEvent.class);

        log.info("[RefundOrderService] order 조회 시작. orderId={}", event.orderId());
        Order order = orderRepository.findByOrderId(event.orderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        log.info("[RefundOrderService] order 조회 성공. orderId={}, status={}",
            order.getOrderId(), order.getStatus());

        // 3분류 ① 멱등 스킵
        if (order.getStatus() == OrderStatus.REFUND_PENDING) {
            log.info("[refund.order.cancel] 멱등 스킵 — 이미 REFUND_PENDING. orderId={}", event.orderId());
            publishOrderDone(event.refundId(), event.orderId());
            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        log.info("[RefundOrderService] 주문 상태 변경 시도. orderId={}, currentStatus={}, targetStatus=REFUND_PENDING",
            order.getOrderId(), order.getStatus());
        if (!order.canTransitionTo(OrderStatus.REFUND_PENDING)) {
            // ② 정책적 스킵 — 이미 환불 완료/취소/만료된 주문
            if (isExplainableSkip(order.getStatus())) {
                log.warn("[refund.order.cancel] 정책적 스킵 — orderId={}, 현재상태={}",
                    event.orderId(), order.getStatus());
                publishOrderFailed(event.refundId(), event.orderId(),
                    "Order already in terminal state: " + order.getStatus());
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            // ③ 이상 상태 — throw → DLT
            throw new IllegalStateException(String.format(
                "[refund.order.cancel] 허용되지 않는 전이: %s → REFUND_PENDING, orderId=%s",
                order.getStatus(), event.orderId()));
        }

        order.requestRefund();
        publishOrderDone(event.refundId(), event.orderId());
        deduplicationService.markProcessed(messageId, topic);
    }

    /**
     * refund.order.compensate 수신: REFUND_PENDING → PAID 보상 롤백.
     */
    @Transactional
    public void processOrderCompensate(UUID messageId, String topic, String payload) {
        if (deduplicationService.isDuplicate(messageId)) {
            log.debug("[refund.order.compensate] 중복 메시지 스킵. messageId={}", messageId);
            return;
        }

        RefundOrderCompensateEvent event = parsePayload(payload, RefundOrderCompensateEvent.class);

        Order order = orderRepository.findByOrderId(event.orderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("[refund.order.compensate] 멱등 스킵 — 이미 PAID. orderId={}", event.orderId());
            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        if (!order.canTransitionTo(OrderStatus.PAID)) {
            if (order.getStatus() == OrderStatus.REFUNDED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.FAILED) {
                log.warn("[refund.order.compensate] 정책적 스킵 — orderId={}, 현재상태={}",
                    event.orderId(), order.getStatus());
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            throw new IllegalStateException(String.format(
                "[refund.order.compensate] 허용되지 않는 전이: %s → PAID, orderId=%s",
                order.getStatus(), event.orderId()));
        }

        order.rollbackRefund();
        deduplicationService.markProcessed(messageId, topic);
    }

    /**
     * refund.completed 수신: Saga 최종 확정.
     * - Order REFUND_PENDING → REFUNDED + 총액 차감
     * - 해당 Order 의 CANCELLED 티켓 → REFUNDED 일괄 전이 페이로드에 ticketIds 없음 — orderId 기준으로 찾아 일괄 전이.
     */
    @Transactional
    public void processRefundCompleted(UUID messageId, String topic, String payload) {
        if (deduplicationService.isDuplicate(messageId)) {
            log.debug("[refund.completed] 중복 메시지 스킵. messageId={}", messageId);
            return;
        }

        RefundCompletedEvent event = parsePayload(payload, RefundCompletedEvent.class);

        Order order = orderRepository.findByOrderId(event.orderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.info("[refund.completed] 멱등 스킵 — 이미 REFUNDED. orderId={}", event.orderId());
            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        if (!order.canTransitionTo(OrderStatus.REFUNDED)) {
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FAILED) {
                log.warn("[refund.completed] 정책적 스킵 — orderId={}, 현재상태={}",
                    event.orderId(), order.getStatus());
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            throw new IllegalStateException(String.format(
                "[refund.completed] 허용되지 않는 전이: %s → REFUNDED, orderId=%s",
                order.getStatus(), event.orderId()));
        }

        // CANCELLED 티켓 → REFUNDED 전이
        List<Ticket> cancelledTickets = ticketRepository
            .findAllByOrderIdAndStatus(order.getId(), TicketStatus.CANCELLED);
        for (Ticket ticket : cancelledTickets) {
            ticket.refundTicket();
        }

        // total_amount 는 주문 당시 결제액 스냅샷으로 불변 — 환불 진행도는 Ticket.status 가 SSoT.
        // 잔여 ISSUED 티켓이 있으면 부분환불 — PAID로 복귀해 다음 티켓 환불 허용
        // 모두 환불됐으면 REFUNDED로 확정
        List<Ticket> remainingTickets = ticketRepository
            .findAllByOrderIdAndStatus(order.getId(), TicketStatus.ISSUED);
        if (remainingTickets.isEmpty()) {
            order.completeRefund();
        } else {
            order.rollbackRefund();
            log.info("[refund.completed] 부분환불 완료, 잔여 티켓={}매 — orderId={}",
                remainingTickets.size(), event.orderId());
        }

        deduplicationService.markProcessed(messageId, topic);
    }

    //---- 내부 헬퍼 ----

    private void publishOrderDone(UUID refundId, UUID orderId) {
        outboxService.save(
            orderId.toString(),
            orderId.toString(),
            "REFUND_ORDER_DONE",
            KafkaTopics.REFUND_ORDER_DONE,
            new RefundOrderDoneEvent(refundId, orderId, Instant.now())
        );
    }

    private void publishOrderFailed(UUID refundId, UUID orderId, String reason) {
        outboxService.save(
            orderId.toString(),
            orderId.toString(),
            "REFUND_ORDER_FAILED",
            KafkaTopics.REFUND_ORDER_FAILED,
            new RefundOrderFailedEvent(refundId, orderId, reason, Instant.now())
        );
    }

    private boolean isExplainableSkip(OrderStatus current) {
        return current == OrderStatus.CANCELLED
            || current == OrderStatus.FAILED
            || current == OrderStatus.REFUNDED;
    }

    private <T> T parsePayload(String payload, Class<T> clazz) {
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Kafka 메시지 역직렬화 실패: " + clazz.getSimpleName(), e);
        }
    }
}
