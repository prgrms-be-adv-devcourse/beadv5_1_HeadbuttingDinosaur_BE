package com.devticket.payment.refund.presentation.consumer;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.refund.application.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.application.event.RefundOrderFailedEvent;
import com.devticket.payment.refund.application.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.event.RefundStockDoneEvent;
import com.devticket.payment.refund.application.event.RefundStockFailedEvent;
import com.devticket.payment.refund.application.event.RefundTicketDoneEvent;
import com.devticket.payment.refund.application.event.RefundTicketFailedEvent;
import com.devticket.payment.refund.application.saga.RefundSagaHandler;
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
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.REFUND_REQUESTED,
        groupId = "payment-refund-saga-requested",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRequested(ConsumerRecord<String, String> record, Acknowledgment ack) {
        dispatch(record, ack, RefundRequestedEvent.class, handler::handleRequested);
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_ORDER_DONE,
        groupId = "payment-refund-saga-order-done",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderDone(ConsumerRecord<String, String> record, Acknowledgment ack) {
        dispatch(record, ack, RefundOrderDoneEvent.class, handler::handleOrderDone);
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_ORDER_FAILED,
        groupId = "payment-refund-saga-order-failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        dispatch(record, ack, RefundOrderFailedEvent.class, handler::handleOrderFailed);
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_TICKET_DONE,
        groupId = "payment-refund-saga-ticket-done",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTicketDone(ConsumerRecord<String, String> record, Acknowledgment ack) {
        dispatch(record, ack, RefundTicketDoneEvent.class, handler::handleTicketDone);
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_TICKET_FAILED,
        groupId = "payment-refund-saga-ticket-failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTicketFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        dispatch(record, ack, RefundTicketFailedEvent.class, handler::handleTicketFailed);
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_STOCK_DONE,
        groupId = "payment-refund-saga-stock-done",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onStockDone(ConsumerRecord<String, String> record, Acknowledgment ack) {
        dispatch(record, ack, RefundStockDoneEvent.class, handler::handleStockDone);
    }

    @KafkaListener(
        topics = KafkaTopics.REFUND_STOCK_FAILED,
        groupId = "payment-refund-saga-stock-failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onStockFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        dispatch(record, ack, RefundStockFailedEvent.class, handler::handleStockFailed);
    }

    private <T> void dispatch(
        ConsumerRecord<String, String> record,
        Acknowledgment ack,
        Class<T> type,
        SagaCallback<T> callback
    ) {
        UUID messageId = extractMessageId(record);
        try {
            T event = objectMapper.readValue(record.value(), type);
            callback.apply(event, messageId, record.topic());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Saga-Consumer] 처리 실패 — topic={}, messageId={}, error={}",
                record.topic(), messageId, e.getMessage(), e);
            throw new RuntimeException(
                "Saga 메시지 처리 실패: " + record.topic(), e);
        }
    }

    private UUID extractMessageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("X-Message-Id");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                log.warn("[Saga-Consumer] X-Message-Id 파싱 실패 — topic={}, offset={}",
                    record.topic(), record.offset());
            }
        }
        String fallback = record.topic() + ":" + record.partition() + ":" + record.offset();
        return UUID.nameUUIDFromBytes(fallback.getBytes(StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    private interface SagaCallback<T> {
        void apply(T event, UUID messageId, String topic);
    }
}
