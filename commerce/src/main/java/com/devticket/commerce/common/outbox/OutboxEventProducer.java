package com.devticket.commerce.common.outbox;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProducer {

    private static final String MESSAGE_ID_HEADER = "X-Message-Id";

    private final KafkaTemplate<String, String> kafkaTemplate;

    // 앱 타임아웃 — Producer delivery.timeout.ms 보다 커야 이중 발행 위험 차단
    // 불변식: max.block < request.timeout ≤ delivery.timeout < sendTimeoutMs
    // 초기값 2000은 Spring 주입 실패 시(@InjectMocks 단위 테스트 등) 폴백
    @Value("${devticket.outbox.send-timeout-ms:2000}")
    private long sendTimeoutMs = 2000;

    public void publish(OutboxEventMessage message) {
        ProducerRecord<String, String> record = buildRecord(message);
        try {
            kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Outbox 발행 성공 — topic={}, messageId={}", message.topic(), message.messageId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Kafka 발행 인터럽트 — topic=" + message.topic(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new OutboxPublishException("Kafka 발행 실패 — topic=" + message.topic(), e);
        }
    }

    private ProducerRecord<String, String> buildRecord(OutboxEventMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                message.topic(),
                null,                   // partition: null → partitionKey 기반 자동 결정
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
