package com.devticket.event.application;

import com.devticket.event.common.messaging.PayloadExtractor;
import com.devticket.event.common.messaging.event.RefundCompletedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * refund.completed 처리 서비스.
 * dedup 체크 + 모니터링 로깅 + dedup 기록을 한 트랜잭션으로 묶어,
 * 컨슈머가 트랜잭션 커밋 이후에만 ACK 할 수 있도록 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundCompletedService {

    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordRefundCompleted(UUID messageId, String topic, String payload) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }

        RefundCompletedEvent event = deserialize(payload);
        log.info("[refund.completed 처리] refundId={}, orderId={}, paymentMethod={}, amount={}, rate={}, ts={}",
            event.refundId(), event.orderId(), event.paymentMethod(),
            event.refundAmount(), event.refundRate(), event.timestamp());

        deduplicationService.markProcessed(messageId, topic);
    }

    private RefundCompletedEvent deserialize(String payload) {
        try {
            String actualPayload = PayloadExtractor.extract(objectMapper, payload);
            return objectMapper.readValue(actualPayload, RefundCompletedEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("RefundCompletedEvent 역직렬화 실패", e);
        }
    }
}
