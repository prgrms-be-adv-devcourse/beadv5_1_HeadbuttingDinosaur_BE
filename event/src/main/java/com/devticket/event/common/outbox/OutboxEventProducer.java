package com.devticket.event.common.outbox;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
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

    private final KafkaTemplate<String, String> kafkaTemplate;

    // 앱 레벨 send().get() 타임아웃(ms). Spring 주입 실패 시 폴백 = @Value default 와 일치.
    // 불변식: deliveryTimeoutMs < sendTimeoutMs (Producer 재시도가 앱 타임아웃 이전에 종료)
    @Value("${kafka.outbox-producer.send-timeout-ms:2000}")
    private long sendTimeoutMs = 2000L;

    /**
     * Outbox 메시지를 Kafka에 동기 발행한다.
     *
     * <p>성공 시 정상 반환, 실패 시 {@link OutboxPublishException}으로 감싸 던진다.
     * Kafka/Future 계열 예외는 호출부가 단일 타입으로만 catch할 수 있도록 일원화한다.
     *
     * @throws OutboxPublishException 발행 실패 (broker ack 실패 / 타임아웃 / 메타데이터 실패 등)
     */
    public void publish(OutboxEventMessage message) throws OutboxPublishException {
        ProducerRecord<String, String> record = buildRecord(message);

        try {
            kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Outbox 발행 성공 — topic={}, messageId={}", message.topic(), message.messageId());
        } catch (ExecutionException | TimeoutException e) {
            // future 결과 대기 중 실패 — broker ack 실패 / send 타임아웃
            log.error("Outbox 발행 실패 (future) — topic={}, messageId={}, error={}",
                    message.topic(), message.messageId(), e.getMessage());
            throw new OutboxPublishException("Outbox 발행 실패 (future) — messageId=" + message.messageId(), e);
        } catch (InterruptedException e) {
            log.error("Outbox 발행 인터럽트 — topic={}, messageId={}", message.topic(), message.messageId());
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Outbox 발행 인터럽트 — messageId=" + message.messageId(), e);
        } catch (KafkaException e) {
            // send() 호출 시점 런타임 예외 — max.block.ms 초과(TimeoutException),
            // 직렬화 실패(SerializationException), 메타데이터 실패 등
            log.error("Outbox 발행 실패 (send) — topic={}, messageId={}, error={}",
                    message.topic(), message.messageId(), e.getMessage());
            throw new OutboxPublishException("Outbox 발행 실패 (send) — messageId=" + message.messageId(), e);
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
