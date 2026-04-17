package com.devticket.payment.refund.application.saga;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.application.dto.PgPaymentCancelCommand;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.refund.application.event.RefundOrderCancelEvent;
import com.devticket.payment.refund.application.event.RefundOrderCompensateEvent;
import com.devticket.payment.refund.application.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.application.event.RefundOrderFailedEvent;
import com.devticket.payment.refund.application.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.event.RefundStockDoneEvent;
import com.devticket.payment.refund.application.event.RefundStockFailedEvent;
import com.devticket.payment.refund.application.event.RefundStockRestoreEvent;
import com.devticket.payment.refund.application.event.RefundTicketCancelEvent;
import com.devticket.payment.refund.application.event.RefundTicketCompensateEvent;
import com.devticket.payment.refund.application.event.RefundTicketDoneEvent;
import com.devticket.payment.refund.application.event.RefundTicketFailedEvent;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.model.SagaState;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.domain.repository.SagaStateRepository;
import com.devticket.payment.refund.domain.saga.SagaStep;
import com.devticket.payment.refund.infrastructure.persistence.RefundTicketJpaRepository;
import com.devticket.payment.wallet.application.event.RefundCompletedEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 Saga 흐름을 단계별로 관리한다.
 *
 * <pre>
 *   start -> ORDER_CANCELLING
 *         -> TICKET_CANCELLING
 *         -> STOCK_RESTORING
 *         -> COMPLETING (PG/Wallet 처리)
 *         -> COMPLETED
 * </pre>
 *
 * 실패 시 이전 단계에 대한 compensate 이벤트를 역순으로 발행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundSagaOrchestrator {

    private final SagaStateRepository sagaStateRepository;
    private final OrderRefundRepository orderRefundRepository;
    private final RefundRepository refundRepository;
    private final RefundTicketJpaRepository refundTicketRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;
    private final PgPaymentClient pgPaymentClient;

    /**
     * Saga 시작.
     * RefundService 에서 Refund 와 OrderRefund 를 이미 저장한 뒤 호출되므로,
     * 여기서는 SagaState 저장 + 첫 번째 단계 Outbox 발행만 수행한다.
     */
    @Transactional
    public void start(RefundRequestedEvent event) {
        if (sagaStateRepository.findByRefundId(event.refundId()).isPresent()) {
            log.info("[Saga] 이미 시작된 refundId — 스킵. refundId={}", event.refundId());
            return;
        }

        SagaState state = SagaState.start(event.refundId(), event.orderId(), event.paymentMethod());
        sagaStateRepository.save(state);

        RefundOrderCancelEvent payload = RefundOrderCancelEvent.builder()
            .refundId(event.refundId())
            .orderId(event.orderId())
            .fullRefund(event.refundRate() == 100)
            .timestamp(Instant.now())
            .build();

        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_ORDER_CANCEL,
            KafkaTopics.REFUND_ORDER_CANCEL,
            event.orderId().toString(),
            payload
        );

        log.info("[Saga] 시작 — refundId={}, orderId={}, method={}",
            event.refundId(), event.orderId(), event.paymentMethod());
    }

    @Transactional
    public void onOrderDone(RefundOrderDoneEvent event) {
        SagaState state = loadState(event.refundId());
        if (state.isTerminal()) {
            return;
        }
        Refund refund = loadRefund(event.refundId());

        state.advance(SagaStep.TICKET_CANCELLING);

        List<UUID> ticketIds = resolveTicketIds(refund);

        RefundTicketCancelEvent payload = RefundTicketCancelEvent.builder()
            .refundId(event.refundId())
            .orderId(event.orderId())
            .ticketIds(ticketIds)
            .timestamp(Instant.now())
            .build();

        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_TICKET_CANCEL,
            KafkaTopics.REFUND_TICKET_CANCEL,
            event.orderId().toString(),
            payload
        );
    }

    @Transactional
    public void onOrderFailed(RefundOrderFailedEvent event) {
        SagaState state = loadState(event.refundId());
        if (state.isTerminal()) {
            return;
        }
        state.markFailed(event.reason());
        markRefundFailed(event.refundId());
        log.warn("[Saga] ORDER_CANCEL 실패 — refundId={}, reason={}", event.refundId(), event.reason());
    }

    @Transactional
    public void onTicketDone(RefundTicketDoneEvent event) {
        SagaState state = loadState(event.refundId());
        if (state.isTerminal()) {
            return;
        }
        state.advance(SagaStep.STOCK_RESTORING);

        RefundStockRestoreEvent payload = RefundStockRestoreEvent.builder()
            .refundId(event.refundId())
            .orderId(event.orderId())
            .eventId(event.eventId())
            .quantity(event.quantity())
            .timestamp(Instant.now())
            .build();

        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_STOCK_RESTORE,
            KafkaTopics.REFUND_STOCK_RESTORE,
            event.eventId() != null ? event.eventId().toString() : event.orderId().toString(),
            payload
        );
    }

    @Transactional
    public void onTicketFailed(RefundTicketFailedEvent event) {
        SagaState state = loadState(event.refundId());
        if (state.isTerminal()) {
            return;
        }
        state.markCompensating(SagaStep.COMPENSATING_ORDER);

        RefundOrderCompensateEvent payload = RefundOrderCompensateEvent.builder()
            .refundId(event.refundId())
            .orderId(event.orderId())
            .reason("ticket-cancel-failed: " + event.reason())
            .timestamp(Instant.now())
            .build();

        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_ORDER_COMPENSATE,
            KafkaTopics.REFUND_ORDER_COMPENSATE,
            event.orderId().toString(),
            payload
        );

        markRefundFailed(event.refundId());
        state.markFailed(event.reason());
    }

    @Transactional
    public void onStockDone(RefundStockDoneEvent event) {
        SagaState state = loadState(event.refundId());
        if (state.isTerminal()) {
            return;
        }
        state.advance(SagaStep.COMPLETING);
        completeRefund(state, event.refundId());
    }

    @Transactional
    public void onStockFailed(RefundStockFailedEvent event) {
        SagaState state = loadState(event.refundId());
        if (state.isTerminal()) {
            return;
        }
        Refund refund = loadRefund(event.refundId());

        state.markCompensating(SagaStep.COMPENSATING_TICKET);

        RefundTicketCompensateEvent ticketComp = RefundTicketCompensateEvent.builder()
            .refundId(event.refundId())
            .orderId(event.orderId())
            .ticketIds(resolveTicketIds(refund))
            .reason("stock-restore-failed: " + event.reason())
            .timestamp(Instant.now())
            .build();

        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_TICKET_COMPENSATE,
            KafkaTopics.REFUND_TICKET_COMPENSATE,
            event.orderId().toString(),
            ticketComp
        );

        RefundOrderCompensateEvent orderComp = RefundOrderCompensateEvent.builder()
            .refundId(event.refundId())
            .orderId(event.orderId())
            .reason("stock-restore-failed: " + event.reason())
            .timestamp(Instant.now())
            .build();

        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_ORDER_COMPENSATE,
            KafkaTopics.REFUND_ORDER_COMPENSATE,
            event.orderId().toString(),
            orderComp
        );

        markRefundFailed(event.refundId());
        state.markFailed(event.reason());
    }

    /**
     * 최종 단계: paymentMethod 분기 → PG 부분취소 또는 Wallet 복구 (Wallet 복구는
     * refund.completed 를 구독하는 기존 WalletEventConsumer 에서 수행).
     * OrderRefund 원장 누적 + Refund COMPLETED + refund.completed Outbox.
     */
    private void completeRefund(SagaState state, UUID refundId) {
        Refund refund = loadRefund(refundId);
        Payment payment = paymentRepository.findByPaymentId(refund.getPaymentId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.PAYMENT_NOT_FOUND));

        OrderRefund ledger = orderRefundRepository.findByOrderIdForUpdate(refund.getOrderId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));

        if (payment.getPaymentMethod() == PaymentMethod.PG) {
            pgPaymentClient.cancelPartial(new PgPaymentCancelCommand(
                payment.getPaymentKey(),
                refund.getRefundAmount(),
                "refund-saga:" + refundId
            ));
        }

        ledger.applyRefund(refund.getRefundAmount(), refund.getTicketCount());
        refund.complete(LocalDateTime.now());
        state.markCompleted();

        RefundCompletedEvent payload = RefundCompletedEvent.builder()
            .refundId(refund.getRefundId())
            .orderId(refund.getOrderId())
            .userId(refund.getUserId())
            .paymentId(refund.getPaymentId())
            .paymentMethod(payment.getPaymentMethod())
            .refundAmount(refund.getRefundAmount())
            .refundRate(refund.getRefundRate())
            .timestamp(Instant.now())
            .build();

        outboxService.save(
            refund.getRefundId().toString(),
            KafkaTopics.REFUND_COMPLETED,
            KafkaTopics.REFUND_COMPLETED,
            refund.getOrderId().toString(),
            payload
        );

        log.info("[Saga] 완료 — refundId={}, orderId={}, amount={}",
            refundId, refund.getOrderId(), refund.getRefundAmount());
    }

    private SagaState loadState(UUID refundId) {
        return sagaStateRepository.findByRefundId(refundId)
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
    }

    private Refund loadRefund(UUID refundId) {
        return refundRepository.findByRefundId(refundId)
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
    }

    private void markRefundFailed(UUID refundId) {
        refundRepository.findByRefundId(refundId).ifPresent(Refund::fail);
    }

    private List<UUID> resolveTicketIds(Refund refund) {
        return refundTicketRepository.findByRefundId(refund.getRefundId()).stream()
            .map(RefundTicket::getTicketId)
            .toList();
    }
}
