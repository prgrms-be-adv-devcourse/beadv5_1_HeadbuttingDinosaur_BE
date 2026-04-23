package com.devticket.payment.refund.presentation.consumer;

import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.refund.application.saga.RefundSagaOrchestrator;
import com.devticket.payment.refund.application.saga.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundOrderFailedEvent;
import com.devticket.payment.refund.application.saga.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockFailedEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketFailedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga Consumer 의 @Transactional 경계를 제공하는 Handler.
 * 비즈니스 로직과 markProcessed 를 같은 트랜잭션 안에서 실행해
 * 메시지 중복 처리를 원자적으로 차단한다.
 */
@Service
@RequiredArgsConstructor
public class RefundSagaHandler {

    private final RefundSagaOrchestrator orchestrator;
    private final MessageDeduplicationService deduplicationService;

    @Transactional
    public void startAndMark(RefundRequestedEvent event, UUID messageId, String topic) {
        orchestrator.start(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void onOrderDoneAndMark(RefundOrderDoneEvent event, UUID messageId, String topic) {
        orchestrator.onOrderDone(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void onOrderFailedAndMark(RefundOrderFailedEvent event, UUID messageId, String topic) {
        orchestrator.onOrderFailed(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void onTicketDoneAndMark(RefundTicketDoneEvent event, UUID messageId, String topic) {
        orchestrator.onTicketDone(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void onTicketFailedAndMark(RefundTicketFailedEvent event, UUID messageId, String topic) {
        orchestrator.onTicketFailed(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void onStockDoneAndMark(RefundStockDoneEvent event, UUID messageId, String topic) {
        orchestrator.onStockDone(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void onStockFailedAndMark(RefundStockFailedEvent event, UUID messageId, String topic) {
        orchestrator.onStockFailed(event);
        deduplicationService.markProcessed(messageId, topic);
    }
}
