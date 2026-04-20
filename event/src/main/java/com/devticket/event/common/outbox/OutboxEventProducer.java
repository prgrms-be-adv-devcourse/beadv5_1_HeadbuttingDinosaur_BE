package com.devticket.event.common.outbox;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbox → Kafka 발행 컴포넌트
 *
 * <p>ProducerRecord 생성 규칙:
 * <ul>
 *   <li>Key: partitionKey (orderId 또는 eventId) — 같은 주문/이벤트 관련 메시지를 같은 파티션으로 라우팅</li>
 *   <li>Header: X-Message-Id = outbox.messageId — Consumer dedup 키</li>
 *   <li>Value: JSON 직렬화된 이벤트 페이로드</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProducer {

    private static final String MESSAGE_ID_HEADER = "X-Message-Id";
    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Outbox 메시지를 Kafka에 동기 발행한다.
     *
     * @return 발행 성공 여부
     */
    public boolean publish(OutboxEventMessage message) {
        ProducerRecord<String, String> record = buildRecord(message);

        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Outbox 발행 성공 — topic={}, messageId={}", message.topic(), message.messageId());
            return true;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            log.error("Outbox 발행 실패 — topic={}, messageId={}, error={}",
                    message.topic(), message.messageId(), e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private ProducerRecord<String, String> buildRecord(OutboxEventMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                message.topic(),
                null,               // partition: null → partitionKey 기반 자동 결정
                message.partitionKey(),
                message.payload()
        );
        record.headers().add(new RecordHeader(
                MESSAGE_ID_HEADER,
                message.messageId().getBytes(StandardCharsets.UTF_8)
        ));
        return record;
    }
}
