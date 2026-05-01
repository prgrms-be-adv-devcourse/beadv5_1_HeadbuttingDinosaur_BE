package com.devticket.event.presentation.consumer;

import com.devticket.event.application.RefundCompletedService;
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
import org.springframework.stereotype.Component;

/**
 * refund.completed 수신자.
 * 현재는 모니터링/로깅 only — Saga 최종 완료를 추적하기 위한 dedup 기록만 수행한다.
 * 추후 환불 확정 후 Event 측 정산/집계 트리거가 필요하면 여기에 확장한다.
 *
 * <p>ACK 는 서비스 트랜잭션 커밋 성공 이후에만 호출되어, AckMode.MANUAL 환경에서
 * 오프셋이 먼저 커밋된 뒤 DB 커밋이 실패해 메시지가 유실되는 시나리오를 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundCompletedConsumer {

    private final RefundCompletedService refundCompletedService;

    @KafkaListener(
        topics = KafkaTopics.REFUND_COMPLETED,
        groupId = "event-refund.completed"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        log.info("[refund.completed 수신] messageId={}, key={}", messageId, record.key());

        try {
            refundCompletedService.recordRefundCompleted(
                messageId, record.topic(), record.value());
        } catch (DataIntegrityViolationException e) {
            log.warn("[refund.completed dedup] UNIQUE 충돌로 스킵 — messageId={}", messageId);
        } catch (Exception e) {
            log.error("[refund.completed 처리 실패] messageId={}", messageId, e);
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
