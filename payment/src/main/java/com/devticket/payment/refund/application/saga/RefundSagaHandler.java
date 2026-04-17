package com.devticket.payment.refund.application.saga;

import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.refund.application.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.application.event.RefundOrderFailedEvent;
import com.devticket.payment.refund.application.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.event.RefundStockDoneEvent;
import com.devticket.payment.refund.application.event.RefundStockFailedEvent;
import com.devticket.payment.refund.application.event.RefundTicketDoneEvent;
import com.devticket.payment.refund.application.event.RefundTicketFailedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer 쪽 dedup + orchestrator 호출 + markProcessed 를 하나의 트랜잭션으로 묶는다.
 * Saga 이벤트 1건 = 1 Tx 로 처리되어 중복 처리와 부분 반영을 방지한다.
 */
@Service
@RequiredArgsConstructor
public class RefundSagaHandler {

    private final RefundSagaOrchestrator orchestrator;
    private final MessageDeduplicationService deduplicationService;

    @Transactional
    public void handleRequested(RefundRequestedEvent event, UUID messageId, String topic) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }
        orchestrator.start(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void handleOrderDone(RefundOrderDoneEvent event, UUID messageId, String topic) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }
        orchestrator.onOrderDone(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void handleOrderFailed(RefundOrderFailedEvent event, UUID messageId, String topic) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }
        orchestrator.onOrderFailed(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void handleTicketDone(RefundTicketDoneEvent event, UUID messageId, String topic) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }
        orchestrator.onTicketDone(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void handleTicketFailed(RefundTicketFailedEvent event, UUID messageId, String topic) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }
        orchestrator.onTicketFailed(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void handleStockDone(RefundStockDoneEvent event, UUID messageId, String topic) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }
        orchestrator.onStockDone(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    @Transactional
    public void handleStockFailed(RefundStockFailedEvent event, UUID messageId, String topic) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }
        orchestrator.onStockFailed(event);
        deduplicationService.markProcessed(messageId, topic);
    }
}
