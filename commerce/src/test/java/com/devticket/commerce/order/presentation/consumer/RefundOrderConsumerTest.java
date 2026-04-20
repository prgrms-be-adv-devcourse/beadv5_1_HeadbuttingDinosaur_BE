package com.devticket.commerce.order.presentation.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.order.application.service.RefundOrderService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class RefundOrderConsumerTest {

    @Mock private RefundOrderService refundOrderService;
    @Mock private Acknowledgment ack;

    @InjectMocks private RefundOrderConsumer consumer;

    private ConsumerRecord<String, String> recordWith(String topic, UUID messageId, String payload) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 0L, null, payload);
        record.headers().add(new RecordHeader(
            "X-Message-Id", messageId.toString().getBytes(StandardCharsets.UTF_8)));
        return record;
    }

    @Test
    void consumeOrderCancel_정상_처리시_서비스_호출하고_ACK() {
        UUID messageId = UUID.randomUUID();
        ConsumerRecord<String, String> record = recordWith(KafkaTopics.REFUND_ORDER_CANCEL, messageId, "{}");

        consumer.consumeOrderCancel(record, ack);

        then(refundOrderService).should().processOrderRefundCancel(messageId, KafkaTopics.REFUND_ORDER_CANCEL, "{}");
        then(ack).should().acknowledge();
    }

    @Test
    void consumeOrderCancel_processed_message_UNIQUE_충돌시_ACK() {
        UUID messageId = UUID.randomUUID();
        ConsumerRecord<String, String> record = recordWith(KafkaTopics.REFUND_ORDER_CANCEL, messageId, "{}");
        org.hibernate.exception.ConstraintViolationException cause =
            new org.hibernate.exception.ConstraintViolationException(
                "dup", null, "uk_processed_message_message_id_topic");
        willThrow(new DataIntegrityViolationException("dup", cause))
            .given(refundOrderService).processOrderRefundCancel(any(), any(), any());

        consumer.consumeOrderCancel(record, ack);

        then(ack).should().acknowledge();
    }

    @Test
    void consumeOrderCancel_X_Message_Id_누락시_예외() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            KafkaTopics.REFUND_ORDER_CANCEL, 0, 0L, null, "{}");

        assertThatThrownBy(() -> consumer.consumeOrderCancel(record, ack))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("X-Message-Id");
        then(ack).should(never()).acknowledge();
    }

    @Test
    void consumeRefundCompleted_정상_처리시_서비스_호출하고_ACK() {
        UUID messageId = UUID.randomUUID();
        ConsumerRecord<String, String> record = recordWith(KafkaTopics.REFUND_COMPLETED, messageId, "{}");

        consumer.consumeRefundCompleted(record, ack);

        then(refundOrderService).should().processRefundCompleted(messageId, KafkaTopics.REFUND_COMPLETED, "{}");
        then(ack).should().acknowledge();
    }

    @Test
    void consumeOrderCompensate_정상_처리시_서비스_호출하고_ACK() {
        UUID messageId = UUID.randomUUID();
        ConsumerRecord<String, String> record = recordWith(KafkaTopics.REFUND_ORDER_COMPENSATE, messageId, "{}");

        consumer.consumeOrderCompensate(record, ack);

        then(refundOrderService).should().processOrderCompensate(messageId, KafkaTopics.REFUND_ORDER_COMPENSATE, "{}");
        then(ack).should().acknowledge();
    }
}
