package com.devticket.event.presentation.consumer;

import com.devticket.event.application.MessageDeduplicationService;
import com.devticket.event.application.StockRestoreService;
import com.devticket.event.common.messaging.KafkaTopics;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedConsumer {

    private final StockRestoreService stockRestoreService;
    private final MessageDeduplicationService deduplicationService;

    @KafkaListener(
        topics = KafkaTopics.PAYMENT_FAILED,
        groupId = "event-payment.failed"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        log.info("[payment.failed 수신] messageId={}, key={}", messageId, record.key());

        try {
            stockRestoreService.restoreStockForPaymentFailed(
                messageId, record.topic(), record.value());
        } catch (ObjectOptimisticLockingFailureException e) {
            // @Version 충돌: 트랜잭션 롤백됨 (restoreStock + markProcessed 모두 롤백)
            // 다른 스레드가 동일 메시지를 이미 처리했을 수 있으므로 dedup 재확인
            if (deduplicationService.isDuplicate(messageId)) {
                log.info("[payment.failed @Version 충돌 → 이미 처리됨, 스킵] messageId={}", messageId);
            } else {
                // 아직 처리 안 됨 (다른 필드 변경에 의한 충돌) → 재시도 필요
                log.warn("[payment.failed @Version 충돌 → 재시도] messageId={}", messageId);
                throw e;
            }
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 충돌: 다른 요청이 이미 처리 완료 → 스킵
            log.warn("[payment.failed dedup] UNIQUE 충돌로 스킵 — messageId={}", messageId);
        } catch (Exception e) {
            log.error("[payment.failed 처리 실패] messageId={}", messageId, e);
            throw e;
        }

        // Step 5. 커밋 성공 또는 dedup 스킵 후에만 ACK
        ack.acknowledge();
    }

    private UUID extractMessageId(Headers headers) {
        Header header = headers.lastHeader("X-Message-Id");
        if (header == null) {
            throw new IllegalStateException("Kafka 헤더에 X-Message-Id가 없습니다");
        }
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    }
}
