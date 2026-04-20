package com.devticket.payment.refund.application.saga;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.application.dto.PgPaymentCancelCommand;
import com.devticket.payment.payment.application.dto.PgPaymentCancelResult;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.refund.application.saga.event.RefundOrderCancelEvent;
import com.devticket.payment.refund.application.saga.event.RefundOrderCompensateEvent;
import com.devticket.payment.refund.application.saga.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundOrderFailedEvent;
import com.devticket.payment.refund.application.saga.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockFailedEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockRestoreEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketCancelEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketCompensateEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketFailedEvent;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.model.SagaState;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.domain.repository.RefundTicketRepository;
import com.devticket.payment.refund.domain.repository.SagaStateRepository;
import com.devticket.payment.refund.domain.saga.SagaStep;
import com.devticket.payment.wallet.application.event.RefundCompletedEvent;
import com.devticket.payment.wallet.application.service.WalletService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundSagaOrchestrator {

    private final SagaStateRepository sagaStateRepository;
    private final RefundRepository refundRepository;
    private final OrderRefundRepository orderRefundRepository;
    private final RefundTicketRepository refundTicketRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;
    private final PgPaymentClient pgPaymentClient;
    private final WalletService walletService;

    /**
     * Saga 진입점 — refund.requested 수신 또는 ticket.issue-failed 수신 시 호출.
     * SagaState 생성 + refund.order.cancel 발행.
     */
    @Transactional
    public void start(RefundRequestedEvent event) {
        if (sagaStateRepository.findByRefundId(event.refundId()).isPresent()) {
            log.info("[Saga] 중복 start 스킵 — refundId={}", event.refundId());
            return;
        }

        SagaState state = SagaState.create(
            event.refundId(),
            event.orderId(),
            event.paymentMethod(),
            SagaStep.ORDER_CANCELLING
        );
        sagaStateRepository.save(state);

        RefundOrderCancelEvent cancelEvent = new RefundOrderCancelEvent(
            event.refundId(),
            event.orderId(),
            event.wholeOrder(),
            Instant.now()
        );
        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_ORDER_CANCEL,
            KafkaTopics.REFUND_ORDER_CANCEL,
            event.orderId().toString(),
            cancelEvent
        );

        log.info("[Saga] 시작 — refundId={}, orderId={}, method={}, wholeOrder={}",
            event.refundId(), event.orderId(), event.paymentMethod(), event.wholeOrder());
    }

    @Transactional
    public void onOrderDone(RefundOrderDoneEvent event) {
        SagaState state = requireState(event.refundId());
        if (state.getCurrentStep() != SagaStep.ORDER_CANCELLING) {
            log.info("[Saga] 중복/순서벗어난 order.done 스킵 — refundId={}, step={}",
                event.refundId(), state.getCurrentStep());
            return;
        }

        Refund refund = refundRepository.findByRefundId(event.refundId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));

        List<RefundTicket> rts = refundTicketRepository.findByRefundId(event.refundId());
        List<UUID> ticketIds = rts.stream().map(RefundTicket::getTicketId).toList();
        boolean wholeOrder = ticketIds.isEmpty();

        state.advance(SagaStep.TICKET_CANCELLING);
        sagaStateRepository.save(state);

        RefundTicketCancelEvent next = new RefundTicketCancelEvent(
            event.refundId(),
            event.orderId(),
            ticketIds,
            wholeOrder,
            Instant.now()
        );
        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_TICKET_CANCEL,
            KafkaTopics.REFUND_TICKET_CANCEL,
            event.orderId().toString(),
            next
        );

        log.info("[Saga] order.done → ticket.cancel 발행 — refundId={}", event.refundId());
    }

    @Transactional
    public void onOrderFailed(RefundOrderFailedEvent event) {
        SagaState state = requireState(event.refundId());
        if (state.getCurrentStep() == SagaStep.FAILED) {
            return;
        }
        state.markFailed();
        sagaStateRepository.save(state);

        Refund refund = refundRepository.findByRefundId(event.refundId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
        refund.fail();
        refundRepository.save(refund);

        orderRefundRepository.findByOrderId(event.orderId())
            .ifPresent(or -> {
                or.markFailed();
                orderRefundRepository.save(or);
            });

        log.error("[Saga] order 취소 실패 — refundId={}, reason={}",
            event.refundId(), event.reason());
    }

    @Transactional
    public void onTicketDone(RefundTicketDoneEvent event) {
        SagaState state = requireState(event.refundId());
        if (state.getCurrentStep() != SagaStep.TICKET_CANCELLING) {
            log.info("[Saga] 중복/순서벗어난 ticket.done 스킵 — refundId={}, step={}",
                event.refundId(), state.getCurrentStep());
            return;
        }

        // wholeOrder 케이스 — Commerce 가 실제 취소한 ticketIds 를 RefundTicket 으로 채움
        List<RefundTicket> existing = refundTicketRepository.findByRefundId(event.refundId());
        if (existing.isEmpty() && event.cancelledTicketIds() != null && !event.cancelledTicketIds().isEmpty()) {
            List<RefundTicket> rts = event.cancelledTicketIds().stream()
                .map(tid -> RefundTicket.of(event.refundId(), tid))
                .toList();
            refundTicketRepository.saveAll(rts);
        }

        int quantity = event.cancelledTicketIds() != null ? event.cancelledTicketIds().size() : existing.size();

        state.advance(SagaStep.STOCK_RESTORING);
        sagaStateRepository.save(state);

        RefundStockRestoreEvent next = new RefundStockRestoreEvent(
            event.refundId(),
            event.orderId(),
            event.eventId(),
            quantity,
            Instant.now()
        );
        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_STOCK_RESTORE,
            KafkaTopics.REFUND_STOCK_RESTORE,
            event.eventId() != null ? event.eventId().toString() : event.orderId().toString(),
            next
        );

        log.info("[Saga] ticket.done → stock.restore 발행 — refundId={}, quantity={}",
            event.refundId(), quantity);
    }

    @Transactional
    public void onTicketFailed(RefundTicketFailedEvent event) {
        SagaState state = requireState(event.refundId());
        if (state.getStatus() == com.devticket.payment.refund.domain.saga.SagaStatus.COMPENSATING
            || state.getStatus() == com.devticket.payment.refund.domain.saga.SagaStatus.FAILED) {
            return;
        }
        state.markCompensating();
        sagaStateRepository.save(state);

        Refund refund = refundRepository.findByRefundId(event.refundId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
        refund.fail();
        refundRepository.save(refund);

        RefundOrderCompensateEvent comp = new RefundOrderCompensateEvent(
            event.refundId(),
            event.orderId(),
            Instant.now()
        );
        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_ORDER_COMPENSATE,
            KafkaTopics.REFUND_ORDER_COMPENSATE,
            event.orderId().toString(),
            comp
        );

        log.error("[Saga] ticket 취소 실패 → order compensate — refundId={}, reason={}",
            event.refundId(), event.reason());
    }

    @Transactional
    public void onStockDone(RefundStockDoneEvent event) {
        SagaState state = requireState(event.refundId());
        if (state.getCurrentStep() == SagaStep.COMPLETED) {
            return;
        }
        if (state.getCurrentStep() != SagaStep.STOCK_RESTORING) {
            log.info("[Saga] 중복/순서벗어난 stock.done 스킵 — refundId={}, step={}",
                event.refundId(), state.getCurrentStep());
            return;
        }

        completeRefund(state);
    }

    @Transactional
    public void onStockFailed(RefundStockFailedEvent event) {
        SagaState state = requireState(event.refundId());
        if (state.getStatus() == com.devticket.payment.refund.domain.saga.SagaStatus.COMPENSATING
            || state.getStatus() == com.devticket.payment.refund.domain.saga.SagaStatus.FAILED) {
            return;
        }
        state.markCompensating();
        sagaStateRepository.save(state);

        Refund refund = refundRepository.findByRefundId(event.refundId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
        refund.fail();
        refundRepository.save(refund);

        List<UUID> ticketIds = refundTicketRepository.findByRefundId(event.refundId())
            .stream().map(RefundTicket::getTicketId).toList();

        RefundTicketCompensateEvent ticketComp = new RefundTicketCompensateEvent(
            event.refundId(),
            event.orderId(),
            ticketIds,
            Instant.now()
        );
        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_TICKET_COMPENSATE,
            KafkaTopics.REFUND_TICKET_COMPENSATE,
            event.orderId().toString(),
            ticketComp
        );

        RefundOrderCompensateEvent orderComp = new RefundOrderCompensateEvent(
            event.refundId(),
            event.orderId(),
            Instant.now()
        );
        outboxService.save(
            event.refundId().toString(),
            KafkaTopics.REFUND_ORDER_COMPENSATE,
            KafkaTopics.REFUND_ORDER_COMPENSATE,
            event.orderId().toString(),
            orderComp
        );

        log.error("[Saga] stock 복구 실패 → ticket+order compensate — refundId={}, reason={}",
            event.refundId(), event.reason());
    }

    /**
     * 최종 단계 — PG/Wallet 처리 + OrderRefund 누적 + refund.completed Outbox 발행.
     */
    private void completeRefund(SagaState state) {
        Refund refund = refundRepository.findByRefundId(state.getRefundId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
        Payment payment = paymentRepository.findByPaymentId(refund.getPaymentId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.PAYMENT_NOT_FOUND));

        OrderRefund ledger = orderRefundRepository.findByOrderId(state.getOrderId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));

        PaymentMethod method = state.getPaymentMethod();
        LocalDateTime completedAt = LocalDateTime.now();

        switch (method) {
            case PG -> {
                state.advance(SagaStep.PG_CANCELLING);
                sagaStateRepository.save(state);
                completedAt = executePgCancel(payment, refund.getRefundAmount(), completedAt);
            }
            case WALLET -> {
                state.advance(SagaStep.WALLET_RESTORING);
                sagaStateRepository.save(state);
                walletService.restoreBalance(
                    refund.getUserId(), refund.getRefundAmount(), refund.getRefundId(), refund.getOrderId()
                );
            }
            case WALLET_PG -> {
                state.advance(SagaStep.PG_CANCELLING);
                sagaStateRepository.save(state);
                completedAt = executePgCancel(payment, refund.getRefundAmount(), completedAt);
                state.advance(SagaStep.WALLET_RESTORING);
                sagaStateRepository.save(state);
                int walletAmount = payment.getWalletAmount() != null ? payment.getWalletAmount() : 0;
                if (walletAmount > 0) {
                    walletService.restoreBalance(
                        refund.getUserId(), walletAmount, refund.getRefundId(), refund.getOrderId()
                    );
                }
            }
        }

        refund.complete(completedAt);
        refundRepository.save(refund);

        int ticketCount = refundTicketRepository.findByRefundId(refund.getRefundId()).size();
        if (ticketCount == 0) {
            // wholeOrder 환불인데 Commerce 쪽에서 cancelledTicketIds 를 안 넘긴 경우 —
            // ledger.remainingTickets 로 간주
            ticketCount = ledger.getRemainingTickets();
        }
        ledger.applyRefund(refund.getRefundAmount(), ticketCount);
        orderRefundRepository.save(ledger);

        state.markCompleted();
        sagaStateRepository.save(state);

        RefundCompletedEvent completed = RefundCompletedEvent.builder()
            .refundId(refund.getRefundId())
            .orderId(refund.getOrderId())
            .userId(refund.getUserId())
            .paymentId(refund.getPaymentId())
            .paymentMethod(method)
            .refundAmount(refund.getRefundAmount())
            .refundRate(refund.getRefundRate())
            .timestamp(Instant.now())
            .build();

        outboxService.save(
            refund.getRefundId().toString(),
            KafkaTopics.REFUND_COMPLETED,
            KafkaTopics.REFUND_COMPLETED,
            refund.getOrderId().toString(),
            completed
        );

        log.info("[Saga] 완료 — refundId={}, method={}, amount={}",
            refund.getRefundId(), method, refund.getRefundAmount());
    }

    private LocalDateTime executePgCancel(Payment payment, int amount, LocalDateTime fallback) {
        if (payment.getPaymentKey() == null || amount <= 0) {
            return fallback;
        }
        try {
            PgPaymentCancelResult result = pgPaymentClient.cancelPartial(
                new PgPaymentCancelCommand(payment.getPaymentKey(), amount, "refund-saga")
            );
            return result.canceledAt() != null
                ? OffsetDateTime.parse(result.canceledAt()).toLocalDateTime()
                : fallback;
        } catch (Exception e) {
            log.error("[Saga] PG 취소 실패 — paymentKey={}, amount={}",
                payment.getPaymentKey(), amount, e);
            throw new RefundException(RefundErrorCode.PG_REFUND_FAILED);
        }
    }

    private SagaState requireState(UUID refundId) {
        return sagaStateRepository.findByRefundId(refundId)
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
    }
}
