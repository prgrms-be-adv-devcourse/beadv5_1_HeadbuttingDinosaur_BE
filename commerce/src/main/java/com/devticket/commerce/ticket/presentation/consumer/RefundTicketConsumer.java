package com.devticket.commerce.ticket.presentation.consumer;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.PayloadExtractor;
import com.devticket.commerce.ticket.application.service.RefundTicketService;
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
import org.springframework.stereotype.Component;

/**
 * Refund Saga — Ticket 관련 Kafka 수신 진입점.
 * <p>
 * - refund.ticket.cancel     : 대상 티켓 ISSUED → CANCELLED 일괄 전이
 * - refund.ticket.compensate : 대상 티켓 CANCELLED → ISSUED 롤백
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundTicketConsumer {

    private final RefundTicketService refundTicketService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.REFUND_TICKET_CANCEL,
        groupId = "commerce-refund.ticket.cancel"
    )
    public void consumeTicketCancel(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        String payload = PayloadExtractor.extract(objectMapper, record.value());

        log.info("[refund.ticket.cancel] raw headers={}", record.headers());
        log.info("[refund.ticket.cancel] raw value={}", record.value());
        log.info("[refund.ticket.cancel] extracted payload={}", payload);

        try {
            refundTicketService.processTicketRefundCancel(messageId, record.topic(), payload);
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            if (isProcessedMessageUniqueConflict(e)) {
                log.warn("[refund.ticket.cancel] messageId={} processed_message UNIQUE 충돌 — 스킵", messageId);
                ack.acknowledge();
                return;
            }
            log.error("[refund.ticket.cancel] DB 예외. messageId={}, payload={}", messageId, payload, e);
            throw e;
        } catch (Exception e) {
            log.error("[refund.ticket.cancel] 처리 실패. messageId={}, payload={}", messageId, payload, e);
            throw e;
        }
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_TICKET_COMPENSATE,
        groupId = "commerce-refund.ticket.compensate"
    )
    public void consumeTicketCompensate(ConsumerRecord<String, String> record, Acknowledgment ack) {
        UUID messageId = extractMessageId(record.headers());
        String payload = PayloadExtractor.extract(objectMapper, record.value());
        try {
            refundTicketService.processTicketCompensate(messageId, record.topic(), payload);
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            if (isProcessedMessageUniqueConflict(e)) {
                log.warn("[refund.ticket.compensate] messageId={} processed_message UNIQUE 충돌 — 스킵", messageId);
                ack.acknowledge();
                return;
            }
            throw e;
        }
    }

    private UUID extractMessageId(Headers headers) {
        Header header = headers.lastHeader("X-Message-Id");
        if (header == null) {
            throw new IllegalArgumentException(
                "X-Message-Id 헤더 누락 — Outbox Producer 설정 확인 필요");
        }
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    }

    private boolean isProcessedMessageUniqueConflict(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                return "uk_processed_message_message_id_topic".equals(constraintName);
            }
            cause = cause.getCause();
        }
        return false;
    }
}
