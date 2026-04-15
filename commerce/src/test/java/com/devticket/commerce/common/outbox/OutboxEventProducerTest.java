package com.devticket.commerce.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, String>> sendFuture;

    @InjectMocks
    private OutboxEventProducer outboxEventProducer;

    @Test
    void publish_성공시_topic_key_header를_포함해_메시지를_발행한다() throws Exception {
        // given
        OutboxEventMessage message = new OutboxEventMessage(
                1L, "message-1", "payment.completed", "order-1", "{\"orderId\":1}"
        );
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        given(kafkaTemplate.send(recordCaptor.capture())).willReturn(sendFuture);
        given(sendFuture.get(2L, TimeUnit.SECONDS)).willReturn(null);

        // when
        outboxEventProducer.publish(message);

        // then
        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("payment.completed");
        assertThat(record.key()).isEqualTo("order-1");
        assertThat(record.value()).isEqualTo("{\"orderId\":1}");
        assertThat(new String(
                record.headers().lastHeader("X-Message-Id").value(),
                StandardCharsets.UTF_8
        )).isEqualTo("message-1");
    }

    @Test
    void publish_ExecutionException이_발생하면_OutboxPublishException을_던진다() throws Exception {
        // given
        OutboxEventMessage message = new OutboxEventMessage(1L, "message-1", "payment.completed", "order-1", "{}");
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(sendFuture);
        given(sendFuture.get(2L, TimeUnit.SECONDS)).willThrow(new ExecutionException(new RuntimeException("fail")));

        // when & then
        assertThatThrownBy(() -> outboxEventProducer.publish(message))
                .isInstanceOf(OutboxPublishException.class)
                .hasCauseInstanceOf(ExecutionException.class);
    }

    @Test
    void publish_TimeoutException이_발생하면_OutboxPublishException을_던진다() throws Exception {
        // given
        OutboxEventMessage message = new OutboxEventMessage(1L, "message-1", "payment.completed", "order-1", "{}");
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(sendFuture);
        given(sendFuture.get(2L, TimeUnit.SECONDS)).willThrow(new TimeoutException("timeout"));

        // when & then
        assertThatThrownBy(() -> outboxEventProducer.publish(message))
                .isInstanceOf(OutboxPublishException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void publish_InterruptedException이_발생하면_인터럽트를_복원하고_OutboxPublishException을_던진다() throws Exception {
        // given
        OutboxEventMessage message = new OutboxEventMessage(1L, "message-1", "payment.completed", "order-1", "{}");
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(sendFuture);
        given(sendFuture.get(2L, TimeUnit.SECONDS)).willThrow(new InterruptedException("interrupted"));

        // when
        Throwable thrown = null;
        try {
            outboxEventProducer.publish(message);
        } catch (Throwable exception) {
            thrown = exception;
        }

        // then
        assertThat(thrown).isInstanceOf(OutboxPublishException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }
}
