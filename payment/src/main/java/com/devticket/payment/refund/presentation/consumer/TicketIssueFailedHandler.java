package com.devticket.payment.refund.presentation.consumer;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.refund.application.saga.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.saga.event.TicketIssueFailedEvent;
import com.devticket.payment.refund.domain.RefundRateConstants;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.domain.repository.RefundTicketRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TicketIssueFailedConsumer} 의 @Transactional 경계를 제공하는 Handler.
 * 같은 클래스 내 self-invocation 으로 프록시가 우회되는 문제를 피하기 위해 별도 빈으로 분리하여
 * 비즈니스 로직과 dedup markProcessed 를 하나의 트랜잭션에서 원자적으로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketIssueFailedHandler {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderRefundRepository orderRefundRepository;
    private final RefundTicketRepository refundTicketRepository;
    private final OutboxService outboxService;
    private final MessageDeduplicationService deduplicationService;

    @Transactional
    public void handleAndMark(TicketIssueFailedEvent event, String messageId, String topic) {
        Payment payment = paymentRepository.findByPaymentId(event.paymentId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.PAYMENT_NOT_FOUND));

        List<UUID> issuedTicketIds = event.issuedTicketIds() != null ? event.issuedTicketIds() : List.of();
        int ticketCount = issuedTicketIds.isEmpty() ? 1 : issuedTicketIds.size();
        int refundAmount = event.refundAmount() > 0 ? event.refundAmount() : payment.getAmount();

        OrderRefund ledger = orderRefundRepository.findByOrderId(event.orderId())
            .orElseGet(() -> orderRefundRepository.save(
                OrderRefund.create(
                    event.orderId(),
                    event.userId(),
                    payment.getPaymentId(),
                    payment.getPaymentMethod(),
                    payment.getAmount(),
                    ticketCount
                )
            ));

        Refund refund = Refund.create(
            ledger.getOrderRefundId(),
            event.orderId(),
            payment.getPaymentId(),
            event.userId(),
            refundAmount,
            RefundRateConstants.FULL
        );
        refundRepository.save(refund);

        if (!issuedTicketIds.isEmpty()) {
            List<RefundTicket> rts = issuedTicketIds.stream()
                .map(tid -> RefundTicket.of(refund.getRefundId(), tid))
                .toList();
            refundTicketRepository.saveAll(rts);
        }

        RefundRequestedEvent requested = new RefundRequestedEvent(
            refund.getRefundId(),
            ledger.getOrderRefundId(),
            event.orderId(),
            event.userId(),
            payment.getPaymentId(),
            payment.getPaymentMethod(),
            issuedTicketIds,
            refundAmount,
            RefundRateConstants.FULL,
            issuedTicketIds.isEmpty(),
            event.reason(),
            Instant.now(),
            ledger.getTotalTickets()
        );

        outboxService.save(
            refund.getRefundId().toString(),
            event.orderId().toString(),
            KafkaTopics.REFUND_REQUESTED,
            KafkaTopics.REFUND_REQUESTED,
            requested
        );

        deduplicationService.markProcessed(messageId, topic);

        log.info("[Consumer] ticket.issue-failed 환불 진입 — refundId={}, orderId={}, amount={}",
            refund.getRefundId(), event.orderId(), refundAmount);
    }
}
