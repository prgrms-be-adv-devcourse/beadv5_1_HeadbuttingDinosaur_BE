package com.devticket.payment.wallet.infrastructure.kafka;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.wallet.application.event.EventCancelledEvent;
import com.devticket.payment.wallet.application.event.RefundCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventConsumer {

    private final RefundCompletedHandler refundCompletedHandler;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    /**
     * refund.completed 소비 paymentMethod=WALLET인 경우에만 예치금 잔액 복구
     */
    @KafkaListener(
        topics = KafkaTopics.REFUND_COMPLETED,
        groupId = "payment-refund.completed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRefundCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageUUID = extractMessageId(record);

        if (deduplicationService.isDuplicate(messageUUID)) {
            log.info("[Consumer] 중복 메시지 스킵 — topic={}, offset={}", record.topic(), record.offset());
            ack.acknowledge();
            return;
        }

        try {
            RefundCompletedEvent event = objectMapper.readValue(record.value(), RefundCompletedEvent.class);

            if (PaymentMethod.WALLET == event.paymentMethod()) {
                // restoreBalance + markProcessed를 하나의 @Transactional에서 처리
                refundCompletedHandler.restoreBalanceAndMarkProcessed(
                    event.userId(),
                    event.refundAmount(),
                    event.refundId(),
                    event.orderId(),
                    messageUUID,
                    record.topic()
                );
            } else {
                // PG 결제건은 복구 불필요 — dedup만 기록
                refundCompletedHandler.markProcessedOnly(messageUUID, record.topic());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Consumer] refund.completed 처리 실패 — messageId={}, error={}",
                messageUUID, e.getMessage(), e);
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
        UUID messageUUID = extractMessageId(record);

        if (deduplicationService.isDuplicate(messageUUID)) {
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
            // refundCompletedHandler.markProcessedOnly(messageUUID, record.topic());
            // ack.acknowledge();

        } catch (Exception e) {
            log.error("[Consumer] event.cancelled 처리 실패 — messageId={}, error={}",
                messageUUID, e.getMessage(), e);
            throw new RuntimeException("event.cancelled 처리 실패", e);
        }
    }

    /**
     * Kafka 헤더에서 X-Message-Id를 추출한다.
     * 헤더가 없거나 파싱 실패 시 topic:partition:offset 기반 결정적 UUID(v3)로 폴백한다.
     */
    private UUID extractMessageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("X-Message-Id");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                log.warn("[Consumer] X-Message-Id 파싱 실패, 레거시 폴백 사용 — topic={}, offset={}",
                    record.topic(), record.offset());
            }
        }
        // 폴백: 헤더 없거나 파싱 실패 시 topic:partition:offset 기반 결정적 UUID
        String fallback = record.topic() + ":" + record.partition() + ":" + record.offset();
        return UUID.nameUUIDFromBytes(fallback.getBytes(StandardCharsets.UTF_8));
    }
}
