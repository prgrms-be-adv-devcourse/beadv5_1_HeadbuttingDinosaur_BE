package com.devticket.payment.refund.presentation.consumer;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.common.messaging.OutboxPayloadExtractor;
import com.devticket.payment.refund.application.saga.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundOrderFailedEvent;
import com.devticket.payment.refund.application.saga.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockFailedEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketFailedEvent;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.exception.RefundInconsistencyException;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundSagaConsumer {

    private static final int PAYLOAD_SNAPSHOT_MAX_LEN = 2_000;

    private final RefundSagaHandler handler;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.REFUND_REQUESTED,
        groupId = "payment-refund.requested",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRefundRequested(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = extractMessageId(record);
        if (deduplicationService.isDuplicate(messageId)) {
            ack.acknowledge();
            return;
        }
        try {
            RefundRequestedEvent event = OutboxPayloadExtractor.extract(objectMapper, record.value(), RefundRequestedEvent.class);
            handler.startAndMark(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            handleConsumeFailure(record, messageId, e);
        }
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_ORDER_DONE,
        groupId = "payment-refund.order.done",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderDone(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = extractMessageId(record);
        if (deduplicationService.isDuplicate(messageId)) {
            ack.acknowledge();
            return;
        }
        try {
            RefundOrderDoneEvent event = OutboxPayloadExtractor.extract(objectMapper, record.value(), RefundOrderDoneEvent.class);
            handler.onOrderDoneAndMark(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            handleConsumeFailure(record, messageId, e);
        }
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_ORDER_FAILED,
        groupId = "payment-refund.order.failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = extractMessageId(record);
        if (deduplicationService.isDuplicate(messageId)) {
            ack.acknowledge();
            return;
        }
        try {
            RefundOrderFailedEvent event = OutboxPayloadExtractor.extract(objectMapper, record.value(), RefundOrderFailedEvent.class);
            handler.onOrderFailedAndMark(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            handleConsumeFailure(record, messageId, e);
        }
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_TICKET_DONE,
        groupId = "payment-refund.ticket.done",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTicketDone(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = extractMessageId(record);
        if (deduplicationService.isDuplicate(messageId)) {
            ack.acknowledge();
            return;
        }
        try {
            RefundTicketDoneEvent event = OutboxPayloadExtractor.extract(objectMapper, record.value(), RefundTicketDoneEvent.class);
            handler.onTicketDoneAndMark(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            handleConsumeFailure(record, messageId, e);
        }
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_TICKET_FAILED,
        groupId = "payment-refund.ticket.failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTicketFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = extractMessageId(record);
        if (deduplicationService.isDuplicate(messageId)) {
            ack.acknowledge();
            return;
        }
        try {
            RefundTicketFailedEvent event = OutboxPayloadExtractor.extract(objectMapper, record.value(), RefundTicketFailedEvent.class);
            handler.onTicketFailedAndMark(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            handleConsumeFailure(record, messageId, e);
        }
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_STOCK_DONE,
        groupId = "payment-refund.stock.done",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStockDone(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = extractMessageId(record);
        if (deduplicationService.isDuplicate(messageId)) {
            ack.acknowledge();
            return;
        }
        try {
            RefundStockDoneEvent event = OutboxPayloadExtractor.extract(objectMapper, record.value(), RefundStockDoneEvent.class);
            handler.onStockDoneAndMark(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            handleConsumeFailure(record, messageId, e);
        }
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_STOCK_FAILED,
        groupId = "payment-refund.stock.failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStockFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = extractMessageId(record);
        if (deduplicationService.isDuplicate(messageId)) {
            ack.acknowledge();
            return;
        }
        try {
            RefundStockFailedEvent event = OutboxPayloadExtractor.extract(objectMapper, record.value(), RefundStockFailedEvent.class);
            handler.onStockFailedAndMark(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            handleConsumeFailure(record, messageId, e);
        }
    }

    /**
     * 컨슈머 공통 실패 처리.
     * - REFUND_NOT_FOUND 가 원인이면 부정합으로 분류 → 마커 로그 + 페이로드 스냅샷 + 재시도 없이 DLT
     * - 그 외 일반 처리 실패는 기존대로 재시도 후 DLT
     */
    private void handleConsumeFailure(ConsumerRecord<String, String> record, String messageId, Exception e) {
        if (isRefundNotFound(e)) {
            String snapshot = truncatePayload(record.value());
            log.error("[Saga.Inconsistency] {} — Refund/SagaState 레코드 미발견. "
                    + "messageId={}, partition={}, offset={}, payload={}",
                record.topic(), messageId, record.partition(), record.offset(), snapshot, e);
            throw new RefundInconsistencyException(record.topic(), messageId, snapshot, e);
        }

        log.error("[Saga.Consumer] {} 처리 실패 — messageId={}", record.topic(), messageId, e);
        throw new RuntimeException(record.topic() + " 처리 실패", e);
    }

    private static boolean isRefundNotFound(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof RefundException re && re.getErrorCode() == RefundErrorCode.REFUND_NOT_FOUND) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static String truncatePayload(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= PAYLOAD_SNAPSHOT_MAX_LEN
            ? value
            : value.substring(0, PAYLOAD_SNAPSHOT_MAX_LEN) + "...(truncated)";
    }

    private String extractMessageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("X-Message-Id");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8)).toString();
            } catch (IllegalArgumentException e) {
                log.warn("[Saga.Consumer] X-Message-Id 파싱 실패 — topic={}, offset={}",
                    record.topic(), record.offset());
            }
        }
        String fallback = record.topic() + ":" + record.partition() + ":" + record.offset();
        return UUID.nameUUIDFromBytes(fallback.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
