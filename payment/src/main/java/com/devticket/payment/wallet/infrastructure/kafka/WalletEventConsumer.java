package com.devticket.payment.wallet.infrastructure.kafka;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.wallet.application.event.EventCancelledEvent;
import com.devticket.payment.wallet.application.event.RefundCompletedEvent;
import com.devticket.payment.wallet.application.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventConsumer {

    private final WalletService walletService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    /**
     * refund.completed 소비 paymentMethod=WALLET인 경우에만 예치금 잔액 복구
     */
    @KafkaListener(
        topics = KafkaTopics.REFUND_COMPLETED,
        groupId = "payment-wallet-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRefundCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = buildMessageId(record);

        if (deduplicationService.isDuplicate(UUID.fromString(messageId))) {
            log.info("[Consumer] 중복 메시지 스킵 — topic={}, offset={}", record.topic(), record.offset());
            ack.acknowledge();
            return;
        }

        try {
            RefundCompletedEvent event = objectMapper.readValue(record.value(), RefundCompletedEvent.class);

            // 예치금 결제건만 복구 처리 (PG는 이미 PG 취소로 처리됨)
            if ("WALLET".equals(event.paymentMethod())) {
                walletService.restoreBalance(
                    UUID.fromString(event.userId()),
                    event.refundAmount(),
                    UUID.fromString(event.refundId()),
                    event.orderId()
                );
            }

            deduplicationService.markProcessed(UUID.fromString(messageId));
            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Consumer] refund.completed 처리 실패 — messageId={}, error={}",
                messageId, e.getMessage(), e);
            // DefaultErrorHandler가 3회 재시도 후 DLT(refund.completed.DLT)로 이동
            throw new RuntimeException("refund.completed 처리 실패", e);
        }
    }

    /**
     * event.force-cancelled / event.sale-stopped 소비 해당 이벤트의 PAID 주문에 대해 일괄 100% 환불 처리
     */
    @KafkaListener(
        topics = {KafkaTopics.EVENT_FORCE_CANCELLED, KafkaTopics.EVENT_SALE_STOPPED},
        groupId = "payment-event-cancel-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeEventCancelled(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = buildMessageId(record);

        if (deduplicationService.isDuplicate(UUID.fromString(messageId))) {
            log.info("[Consumer] 중복 메시지 스킵 — topic={}, offset={}", record.topic(), record.offset());
            ack.acknowledge();
            return;
        }

        try {
            EventCancelledEvent event = objectMapper.readValue(record.value(), EventCancelledEvent.class);

            log.info("[Consumer] 이벤트 취소 수신 — eventId={}, cancelledBy={}",
                event.getEventId(), event.getCancelledBy());

            walletService.processBatchRefund(event.getEventId());

            deduplicationService.markProcessed(UUID.fromString(messageId));
            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Consumer] event.cancelled 처리 실패 — messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw new RuntimeException("event.cancelled 처리 실패", e);
        }
    }

    private String buildMessageId(ConsumerRecord<String, String> record) {
        return record.topic() + ":" + record.partition() + ":" + record.offset();
    }
}