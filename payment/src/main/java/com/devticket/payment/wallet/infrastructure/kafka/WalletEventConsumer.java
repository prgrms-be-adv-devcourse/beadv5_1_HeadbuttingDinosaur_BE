package com.devticket.payment.wallet.infrastructure.kafka;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.wallet.application.event.EventCancelledEvent;
import com.devticket.payment.wallet.application.event.RefundCompletedEvent;
import com.devticket.payment.wallet.application.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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

        if (deduplicationService.isDuplicate(toMessageUUID(messageId))) {
            log.info("[Consumer] 중복 메시지 스킵 — topic={}, offset={}", record.topic(), record.offset());
            ack.acknowledge();
            return;
        }

        try {
            RefundCompletedEvent event = objectMapper.readValue(record.value(), RefundCompletedEvent.class);

            // 예치금 결제건만 복구 처리 (PG는 이미 PG 취소로 처리됨)
            if (PaymentMethod.WALLET == event.paymentMethod()) {
                walletService.restoreBalance(
                    event.userId(),
                    event.refundAmount(),
                    event.refundId(),
                    event.orderId()
                );
            }

            deduplicationService.markProcessed(toMessageUUID(messageId), record.topic());
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

        if (deduplicationService.isDuplicate(toMessageUUID(messageId))) {
            log.info("[Consumer] 중복 메시지 스킵 — topic={}, offset={}", record.topic(), record.offset());
            ack.acknowledge();
            return;
        }

        try {
            EventCancelledEvent event = objectMapper.readValue(record.value(), EventCancelledEvent.class);

            log.info("[Consumer] 이벤트 취소 수신 — eventId={}, cancelledBy={}",
                event.eventId(), event.cancelledBy());

            // TODO: Refund 모듈 완성 전까지 일괄 환불 미처리 — ACK하지 않고 예외로 DLT 보존
            // Refund 모듈 완성 후 아래 주석 해제하고 이 예외 블록 제거
            throw new UnsupportedOperationException(
                "event.cancelled 일괄 환불 미구현 — Refund 모듈 완성 후 처리 예정. eventId=" + event.eventId());

            // walletService.processBatchRefund(event.eventId());
            // deduplicationService.markProcessed(toMessageUUID(messageId));
            // ack.acknowledge();

        } catch (Exception e) {
            log.error("[Consumer] event.cancelled 처리 실패 — messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw new RuntimeException("event.cancelled 처리 실패", e);
        }
    }

    private String buildMessageId(ConsumerRecord<String, String> record) {
        return record.topic() + ":" + record.partition() + ":" + record.offset();
    }

    /**
     * topic:partition:offset 형식의 messageId를 결정적 UUID로 변환 MessageDeduplicationService가 UUID를 요구하므로 name-based UUID(v3)
     * 사용
     */
    private UUID toMessageUUID(String messageId) {
        return UUID.nameUUIDFromBytes(messageId.getBytes(StandardCharsets.UTF_8));
    }
}