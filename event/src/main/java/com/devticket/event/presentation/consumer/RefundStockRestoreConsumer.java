package com.devticket.event.presentation.consumer;

import com.devticket.event.application.MessageDeduplicationService;
import com.devticket.event.application.RefundStockRestoreService;
import com.devticket.event.application.RefundStockRestoreService.EventNotFoundForRefundException;
import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.common.messaging.KafkaTopics;
import com.devticket.event.common.messaging.event.RefundStockRestoreEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class RefundStockRestoreConsumer {

    private final RefundStockRestoreService refundStockRestoreService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.REFUND_STOCK_RESTORE,
        groupId = "event-refund.stock.restore"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        log.info("[refund.stock.restore 수신] messageId={}, key={}", messageId, record.key());

        try {
            refundStockRestoreService.handleRefundStockRestore(
                messageId, record.topic(), record.value());
        } catch (EventNotFoundForRefundException | BusinessException e) {
            // 비즈니스 실패 — 본 트랜잭션 롤백 후 별도 트랜잭션으로 failed 발행
            log.warn("[refund.stock.restore 비즈니스 실패 → failed 발행] messageId={}, reason={}",
                messageId, e.getMessage());
            RefundStockRestoreEvent payload = parsePayloadForFailure(record.value());
            refundStockRestoreService.publishFailedAndMarkProcessed(
                messageId, record.topic(),
                payload.refundId(), payload.orderId(), e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            // @Version 충돌: 트랜잭션 롤백됨 → dedup 재확인
            if (deduplicationService.isDuplicate(messageId)) {
                log.info("[refund.stock.restore @Version 충돌 → 이미 처리됨, 스킵] messageId={}", messageId);
            } else {
                log.warn("[refund.stock.restore @Version 충돌 → 재시도] messageId={}", messageId);
                throw e;
            }
        } catch (DataIntegrityViolationException e) {
            // dedup UNIQUE 충돌: 다른 요청이 이미 처리 완료 → 스킵
            log.warn("[refund.stock.restore dedup] UNIQUE 충돌로 스킵 — messageId={}", messageId);
        } catch (Exception e) {
            log.error("[refund.stock.restore 처리 실패] messageId={}", messageId, e);
            throw e;
        }

        ack.acknowledge();
    }

    private RefundStockRestoreEvent parsePayloadForFailure(String payload) {
        try {
            return objectMapper.readValue(payload, RefundStockRestoreEvent.class);
        } catch (Exception e) {
            // 페이로드 자체가 손상 — failed 발행 불가, 재시도/DLT 로 위임
            throw new IllegalStateException("RefundStockRestoreEvent 역직렬화 실패", e);
        }
    }

    private UUID extractMessageId(Headers headers) {
        Header header = headers.lastHeader("X-Message-Id");
        if (header == null) {
            throw new IllegalStateException("Kafka 헤더에 X-Message-Id가 없습니다");
        }
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    }
}
