package com.devticket.payment.wallet.infrastructure.kafka;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.common.messaging.OutboxPayloadExtractor;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
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
     *
     * NOTE: event.force-cancelled / event.sale-stopped 의 fan-out 은 Commerce 서비스 책임이다.
     * Payment 는 Commerce 가 orderId 별로 발행한 refund.requested 를 소비하여 Saga 를 진행하므로
     * 본 Consumer 에서 이벤트 취소 토픽을 직접 처리하지 않는다.
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
            RefundCompletedEvent event = OutboxPayloadExtractor.extract(
                objectMapper, record.value(), RefundCompletedEvent.class);

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
                // PG/WALLET_PG 결제건은 Saga 내부에서 이미 PG 취소 + Wallet 복구 수행 — dedup만 기록
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
