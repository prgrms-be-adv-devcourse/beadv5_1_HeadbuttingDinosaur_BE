package com.devticket.payment.refund.presentation.consumer;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.refund.application.event.TicketIssueFailedEvent;
import com.devticket.payment.refund.application.service.RefundService;
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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketIssueFailedConsumer {

    private final RefundService refundService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.TICKET_ISSUE_FAILED,
        groupId = "payment-ticket-issue-failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record);
        try {
            TicketIssueFailedEvent event =
                objectMapper.readValue(record.value(), TicketIssueFailedEvent.class);
            handle(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Consumer] ticket.issue-failed 처리 실패 — messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw new RuntimeException("ticket.issue-failed 처리 실패", e);
        }
    }

    @Transactional
    public void handle(TicketIssueFailedEvent event, UUID messageId, String topic) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }
        refundService.initiateAutoRefund(event);
        deduplicationService.markProcessed(messageId, topic);
    }

    private UUID extractMessageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("X-Message-Id");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                log.warn("[Consumer] X-Message-Id 파싱 실패 — topic={}, offset={}",
                    record.topic(), record.offset());
            }
        }
        String fallback = record.topic() + ":" + record.partition() + ":" + record.offset();
        return UUID.nameUUIDFromBytes(fallback.getBytes(StandardCharsets.UTF_8));
    }
}
