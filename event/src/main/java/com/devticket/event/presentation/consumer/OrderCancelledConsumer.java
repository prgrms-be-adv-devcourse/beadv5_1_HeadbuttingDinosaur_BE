package com.devticket.event.presentation.consumer;

import com.devticket.event.application.MessageDeduplicationService;
import com.devticket.event.application.OrderCancelledService;
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
public class OrderCancelledConsumer {

    private final OrderCancelledService orderCancelledService;
    private final MessageDeduplicationService deduplicationService;

    @KafkaListener(
        topics = KafkaTopics.ORDER_CANCELLED,
        groupId = "event-order.cancelled"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        log.info("[order.cancelled 수신] messageId={}, key={}", messageId, record.key());

        try {
            orderCancelledService.restoreStockForOrderCancelled(
                messageId, record.topic(), record.value());
        } catch (ObjectOptimisticLockingFailureException e) {
            // @Version 충돌: 트랜잭션 롤백됨 (restoreStock + markProcessed 모두 롤백)
            // 다른 스레드가 동일 메시지를 이미 처리했을 수 있으므로 dedup 재확인
            if (deduplicationService.isDuplicate(messageId)) {
                log.info("[order.cancelled @Version 충돌 → 이미 처리됨, 스킵] messageId={}", messageId);
            } else {
                log.warn("[order.cancelled @Version 충돌 → 재시도] messageId={}", messageId);
                throw e;
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("[order.cancelled dedup] UNIQUE 충돌로 스킵 — messageId={}", messageId);
        } catch (Exception e) {
            log.error("[order.cancelled 처리 실패] messageId={}", messageId, e);
            throw e;
        }

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
