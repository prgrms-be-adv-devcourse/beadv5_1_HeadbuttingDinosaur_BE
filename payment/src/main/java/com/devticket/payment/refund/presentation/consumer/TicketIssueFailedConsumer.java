package com.devticket.payment.refund.presentation.consumer;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.common.messaging.OutboxPayloadExtractor;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ticket.issue-failed 수신 → Saga 진입점 A.
 * 시스템 귀책이므로 100% 환불로 OrderRefund + Refund 를 생성하고 refund.requested 를 발행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketIssueFailedConsumer {

    private final MessageDeduplicationService deduplicationService;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderRefundRepository orderRefundRepository;
    private final RefundTicketRepository refundTicketRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.TICKET_ISSUE_FAILED,
        groupId = "payment-ticket.issue-failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record);
        if (deduplicationService.isDuplicate(messageId)) {
            ack.acknowledge();
            return;
        }
        try {
            TicketIssueFailedEvent event = OutboxPayloadExtractor.extract(
                objectMapper, record.value(), TicketIssueFailedEvent.class);
            handle(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Consumer] ticket.issue-failed 처리 실패 — messageId={}", messageId, e);
            throw new RuntimeException("ticket.issue-failed 처리 실패", e);
        }
    }

    @Transactional
    protected void handle(TicketIssueFailedEvent event, UUID messageId, String topic) {
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
            event.orderId(),
            event.userId(),
            payment.getPaymentId(),
            payment.getPaymentMethod(),
            issuedTicketIds,
            refundAmount,
            RefundRateConstants.FULL,
            issuedTicketIds.isEmpty(),
            Instant.now()
        );

        outboxService.save(
            refund.getRefundId().toString(),
            KafkaTopics.REFUND_REQUESTED,
            KafkaTopics.REFUND_REQUESTED,
            event.orderId().toString(),
            requested
        );

        deduplicationService.markProcessed(messageId, topic);

        log.info("[Consumer] ticket.issue-failed 환불 진입 — refundId={}, orderId={}, amount={}",
            refund.getRefundId(), event.orderId(), refundAmount);
    }

    private UUID extractMessageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("X-Message-Id");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                log.warn("[Consumer] X-Message-Id 파싱 실패 — topic={}, offset={}",
                    record.topic(), record.offset());
            }
        }
        String fallback = record.topic() + ":" + record.partition() + ":" + record.offset();
        return UUID.nameUUIDFromBytes(fallback.getBytes(StandardCharsets.UTF_8));
    }
}
