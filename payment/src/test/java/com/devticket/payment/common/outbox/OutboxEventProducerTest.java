package com.devticket.payment.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox 이벤트 Producer (OutboxEventProducer)")
class OutboxEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, String>> future;

    @Mock
    private SendResult<String, String> sendResult;

    @Mock
    private RecordMetadata recordMetadata;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private OutboxEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new OutboxEventProducer(kafkaTemplate, objectMapper);
    }

    private OutboxEventMessage createMessage(UUID messageId) {
        return new OutboxEventMessage(
            messageId,
            "payment.completed",
            "{\"orderId\":\"order-001\"}",
            Instant.now()
        );
    }

    private void stubSendSuccess() throws Exception {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);
        given(future.get(2L, TimeUnit.SECONDS)).willReturn(sendResult);
        given(sendResult.getRecordMetadata()).willReturn(recordMetadata);
        given(recordMetadata.offset()).willReturn(42L);
    }

    @Nested
    @DisplayName("발행 성공")
    class PublishSuccess {

        @Test
        void 발행_성공_시_예외_없음() throws Exception {
            OutboxEventMessage message = createMessage(UUID.randomUUID());
            stubSendSuccess();

            assertThatCode(() -> producer.send("payment.completed", "order-001", message))
                .doesNotThrowAnyException();
        }

        @Test
        @SuppressWarnings("unchecked")
        void X_Message_Id_헤더_세팅됨() throws Exception {
            UUID messageId = UUID.randomUUID();
            OutboxEventMessage message = createMessage(messageId);
            stubSendSuccess();

            producer.send("payment.completed", "order-001", message);

            ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
            then(kafkaTemplate).should().send(captor.capture());
            Header header = captor.getValue().headers().lastHeader("X-Message-Id");
            assertThat(header).isNotNull();
            assertThat(new String(header.value(), StandardCharsets.UTF_8))
                .isEqualTo(messageId.toString());
        }
    }

    @Nested
    @DisplayName("발행 실패 — OutboxPublishException 래핑")
    class PublishFailureWrapping {

        @Test
        void TimeoutException_래핑() throws Exception {
            OutboxEventMessage message = createMessage(UUID.randomUUID());
            given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);
            given(future.get(2L, TimeUnit.SECONDS))
                .willThrow(new TimeoutException("send timed out"));

            assertThatThrownBy(() -> producer.send("payment.completed", "order-001", message))
                .isInstanceOf(OutboxPublishException.class)
                .hasMessageContaining("Kafka 발행 실패");
        }

        @Test
        void ExecutionException_래핑() throws Exception {
            OutboxEventMessage message = createMessage(UUID.randomUUID());
            given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);
            given(future.get(2L, TimeUnit.SECONDS))
                .willThrow(new ExecutionException("broker error", new RuntimeException()));

            assertThatThrownBy(() -> producer.send("payment.completed", "order-001", message))
                .isInstanceOf(OutboxPublishException.class)
                .hasMessageContaining("Kafka 발행 실패");
        }

        @Test
        void KafkaException_래핑() {
            OutboxEventMessage message = createMessage(UUID.randomUUID());
            given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willThrow(new KafkaException("producer closed"));

            assertThatThrownBy(() -> producer.send("payment.completed", "order-001", message))
                .isInstanceOf(OutboxPublishException.class)
                .hasMessageContaining("Kafka 발행 실패");
        }
    }
}
