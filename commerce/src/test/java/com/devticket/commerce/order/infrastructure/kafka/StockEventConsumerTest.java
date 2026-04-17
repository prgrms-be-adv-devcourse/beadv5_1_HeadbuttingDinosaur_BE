package com.devticket.commerce.order.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.order.application.service.OrderService;
import com.devticket.commerce.order.presentation.consumer.StockEventConsumer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class StockEventConsumerTest {

    @Mock private OrderService orderService;
    @Mock private Acknowledgment ack;

    @InjectMocks private StockEventConsumer consumer;

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> recordWith(String topic, UUID messageId, String payload) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 0L, null, payload);
        record.headers().add(new RecordHeader(
                "X-Message-Id", messageId.toString().getBytes(StandardCharsets.UTF_8)));
        return record;
    }

    private ConsumerRecord<String, String> recordWithoutHeader(String topic) {
        return new ConsumerRecord<>(topic, 0, 0L, null, "{}");
    }

    // ── consumeStockDeducted ──────────────────────────────────────────

    @Nested
    class ConsumeStockDeducted {

        @Test
        void 정상_처리시_서비스를_호출하고_ACK한다() {
            // given
            UUID messageId = UUID.randomUUID();
            String payload = "{\"orderId\":\"" + UUID.randomUUID() + "\"}";
            ConsumerRecord<String, String> record =
                    recordWith(KafkaTopics.STOCK_DEDUCTED, messageId, payload);

            // when
            consumer.consumeStockDeducted(record, ack);

            // then
            then(orderService).should()
                    .processStockDeducted(messageId, KafkaTopics.STOCK_DEDUCTED, payload);
            then(ack).should().acknowledge();
        }

        @Test
        void DataIntegrityViolation_발생시에도_ACK를_호출한다() {
            // given — processed_message UNIQUE 충돌 시나리오
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record =
                    recordWith(KafkaTopics.STOCK_DEDUCTED, messageId, "{}");
            org.hibernate.exception.ConstraintViolationException hibernateCause =
                    new org.hibernate.exception.ConstraintViolationException(
                            "duplicate key", null, "uk_processed_message_message_id_topic");
            willThrow(new DataIntegrityViolationException("duplicate key", hibernateCause))
                    .given(orderService).processStockDeducted(any(), any(), any());

            // when
            consumer.consumeStockDeducted(record, ack);

            // then
            then(ack).should().acknowledge();
        }

        @Test
        void X_Message_Id_헤더가_없으면_예외를_던지고_ACK하지_않는다() {
            // given — Outbox Producer가 헤더를 누락한 케이스
            ConsumerRecord<String, String> record = recordWithoutHeader(KafkaTopics.STOCK_DEDUCTED);

            // when & then
            assertThatThrownBy(() -> consumer.consumeStockDeducted(record, ack))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("X-Message-Id");

            then(ack).should(never()).acknowledge();
            then(orderService).shouldHaveNoInteractions();
        }
    }

    // ── consumeStockFailed ────────────────────────────────────────────

    @Nested
    class ConsumeStockFailed {

        @Test
        void 정상_처리시_서비스를_호출하고_ACK한다() {
            // given
            UUID messageId = UUID.randomUUID();
            String payload = "{\"orderId\":\"" + UUID.randomUUID() + "\"}";
            ConsumerRecord<String, String> record =
                    recordWith(KafkaTopics.STOCK_FAILED, messageId, payload);

            // when
            consumer.consumeStockFailed(record, ack);

            // then
            then(orderService).should()
                    .processStockFailed(messageId, KafkaTopics.STOCK_FAILED, payload);
            then(ack).should().acknowledge();
        }

        @Test
        void DataIntegrityViolation_발생시에도_ACK를_호출한다() {
            // given — processed_message UNIQUE 충돌 시나리오
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record =
                    recordWith(KafkaTopics.STOCK_FAILED, messageId, "{}");
            org.hibernate.exception.ConstraintViolationException hibernateCause =
                    new org.hibernate.exception.ConstraintViolationException(
                            "duplicate key", null, "uk_processed_message_message_id_topic");
            willThrow(new DataIntegrityViolationException("duplicate key", hibernateCause))
                    .given(orderService).processStockFailed(any(), any(), any());

            // when
            consumer.consumeStockFailed(record, ack);

            // then
            then(ack).should().acknowledge();
        }

        @Test
        void X_Message_Id_헤더가_없으면_예외를_던지고_ACK하지_않는다() {
            // given
            ConsumerRecord<String, String> record = recordWithoutHeader(KafkaTopics.STOCK_FAILED);

            // when & then
            assertThatThrownBy(() -> consumer.consumeStockFailed(record, ack))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("X-Message-Id");

            then(ack).should(never()).acknowledge();
            then(orderService).shouldHaveNoInteractions();
        }
    }
}
