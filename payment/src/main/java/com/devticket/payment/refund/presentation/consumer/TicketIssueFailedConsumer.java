package com.devticket.payment.refund.presentation.consumer;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.common.messaging.OutboxPayloadExtractor;
import com.devticket.payment.refund.application.saga.event.TicketIssueFailedEvent;
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

/**
 * ticket.issue-failed 수신 → Saga 진입점 A.
 * 시스템 귀책이므로 100% 환불로 OrderRefund + Refund 를 생성하고 refund.requested 를 발행한다.
 *
 * 실제 비즈니스 로직 + dedup markProcessed 는 {@link TicketIssueFailedHandler} 의
 * @Transactional 메서드에서 원자적으로 처리한다. (self-invocation 회피)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketIssueFailedConsumer {

    private final TicketIssueFailedHandler handler;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.TICKET_ISSUE_FAILED,
        groupId = "payment-ticket.issue-failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = extractMessageId(record);
        if (deduplicationService.isDuplicate(messageId)) {
            ack.acknowledge();
            return;
        }
        try {
            TicketIssueFailedEvent event = OutboxPayloadExtractor.extract(
                objectMapper, record.value(), TicketIssueFailedEvent.class);
            handler.handleAndMark(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Consumer] ticket.issue-failed 처리 실패 — messageId={}", messageId, e);
            throw new RuntimeException("ticket.issue-failed 처리 실패", e);
        }
    }

    private String extractMessageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("X-Message-Id");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8)).toString();
            } catch (IllegalArgumentException e) {
                log.warn("[Consumer] X-Message-Id 파싱 실패 — topic={}, offset={}",
                    record.topic(), record.offset());
            }
        }
        String fallback = record.topic() + ":" + record.partition() + ":" + record.offset();
        return UUID.nameUUIDFromBytes(fallback.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
