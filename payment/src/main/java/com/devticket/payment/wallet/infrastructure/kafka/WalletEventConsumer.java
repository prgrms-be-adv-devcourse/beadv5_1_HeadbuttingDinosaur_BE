package com.devticket.payment.wallet.infrastructure.kafka;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
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

    /**
     * refund.completed 소비 — dedup 기록 전용.
     *
     * WALLET / WALLET_PG / PG 모두 {@link com.devticket.payment.refund.application.saga.RefundSagaOrchestrator}
     * 가 saga 내부에서 PG 취소 + Wallet 복구를 수행한다. Consumer 에서 restore 를 다시 실행하면
     * 이중 적립이 발생하므로 여기서는 dedup 마킹만 남긴다.
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
        String messageId = extractMessageId(record);

        if (deduplicationService.isDuplicate(messageId)) {
            log.info("[Consumer] 중복 메시지 스킵 — topic={}, offset={}", record.topic(), record.offset());
            ack.acknowledge();
            return;
        }

        try {
            refundCompletedHandler.markProcessedOnly(messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Consumer] refund.completed 처리 실패 — messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw new RuntimeException("refund.completed 처리 실패", e);
        }
    }

    /**
     * Kafka 헤더에서 X-Message-Id를 추출한다.
     * 헤더가 없거나 파싱 실패 시 topic:partition:offset 기반 결정적 UUID(v3)로 폴백한다.
     */
    private String extractMessageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("X-Message-Id");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8)).toString();
            } catch (IllegalArgumentException e) {
                log.warn("[Consumer] X-Message-Id 파싱 실패, 레거시 폴백 사용 — topic={}, offset={}",
                    record.topic(), record.offset());
            }
        }
        // 폴백: 헤더 없거나 파싱 실패 시 topic:partition:offset 기반 결정적 UUID
        String fallback = record.topic() + ":" + record.partition() + ":" + record.offset();
        return UUID.nameUUIDFromBytes(fallback.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
