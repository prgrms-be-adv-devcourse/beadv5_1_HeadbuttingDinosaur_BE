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
            log.error("[Saga.Consumer] refund.requested 처리 실패 — messageId={}", messageId, e);
            throw new RuntimeException("refund.requested 처리 실패", e);
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
            log.error("[Saga.Consumer] refund.order.done 처리 실패 — messageId={}", messageId, e);
            throw new RuntimeException("refund.order.done 처리 실패", e);
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
            log.error("[Saga.Consumer] refund.order.failed 처리 실패 — messageId={}", messageId, e);
            throw new RuntimeException("refund.order.failed 처리 실패", e);
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
            log.error("[Saga.Consumer] refund.ticket.done 처리 실패 — messageId={}", messageId, e);
            throw new RuntimeException("refund.ticket.done 처리 실패", e);
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
            log.error("[Saga.Consumer] refund.ticket.failed 처리 실패 — messageId={}", messageId, e);
            throw new RuntimeException("refund.ticket.failed 처리 실패", e);
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
            log.error("[Saga.Consumer] refund.stock.done 처리 실패 — messageId={}", messageId, e);
            throw new RuntimeException("refund.stock.done 처리 실패", e);
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
            log.error("[Saga.Consumer] refund.stock.failed 처리 실패 — messageId={}", messageId, e);
            throw new RuntimeException("refund.stock.failed 처리 실패", e);
        }
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
