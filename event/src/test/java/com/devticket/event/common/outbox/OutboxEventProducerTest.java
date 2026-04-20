package com.devticket.event.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * OutboxEventProducer 단위 테스트 — B3 예외 감싸기 계약 실증.
 *
 * <p>Producer는 Kafka 계열 예외 4종(ExecutionException / TimeoutException / KafkaException /
 * InterruptedException)을 전부 {@link OutboxPublishException}으로 감싸 호출부에 전달해야 한다.
 * {@link OutboxServiceTest}는 Producer를 Mock으로 감추므로, 본 테스트가 유일한 감싸기 실증 가드.
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventProducerTest {

    private static final long TEST_SEND_TIMEOUT_MS = 100L;

    @Mock
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    private OutboxEventProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        producer = new OutboxEventProducer(kafkaTemplate);
        // @Value 주입 대신 Reflection 으로 짧은 타임아웃 세팅 — TimeoutException 테스트 시간 단축
        ReflectionTestUtils.setField(producer, "sendTimeoutMs", TEST_SEND_TIMEOUT_MS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_성공시_예외없이_반환한다() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);

        assertThatCode(() -> producer.publish(message())).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_ProducerRecord_key와_X_Message_Id_헤더가_설정된다() throws OutboxPublishException {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);

        OutboxEventMessage msg = message();
        producer.publish(msg);

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();

        assertThat(record.topic()).isEqualTo(msg.topic());
        assertThat(record.key()).isEqualTo(msg.partitionKey());
        assertThat(record.value()).isEqualTo(msg.payload());

        Header header = record.headers().lastHeader("X-Message-Id");
        assertThat(header).as("X-Message-Id 헤더 세팅 필수").isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8))
                .as("헤더 값 = messageId 원문")
                .isEqualTo(msg.messageId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_ExecutionException_발생시_OutboxPublishException으로_감싸_던진다() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker ack 실패"));
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);

        assertThatThrownBy(() -> producer.publish(message()))
                .isInstanceOf(OutboxPublishException.class)
                .hasCauseInstanceOf(ExecutionException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_TimeoutException_발생시_OutboxPublishException으로_감싸_던진다() {
        // 절대 완료되지 않는 future → sendTimeoutMs(100ms) 경과 후 TimeoutException
        CompletableFuture<SendResult<String, String>> neverDone = new CompletableFuture<>();
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(neverDone);

        assertThatThrownBy(() -> producer.publish(message()))
                .isInstanceOf(OutboxPublishException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_KafkaException_발생시_OutboxPublishException으로_감싸_던진다() {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willThrow(new KafkaException("max.block.ms 초과 / 메타데이터 실패"));

        assertThatThrownBy(() -> producer.publish(message()))
                .isInstanceOf(OutboxPublishException.class)
                .hasCauseInstanceOf(KafkaException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_InterruptedException_발생시_interrupt_플래그를_복구하고_OutboxPublishException을_던진다() {
        // CompletableFuture 커스텀 subclass 로 get() 호출 시 InterruptedException 강제
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>() {
            @Override
            public SendResult<String, String> get(long timeout, TimeUnit unit) throws InterruptedException {
                throw new InterruptedException("테스트용 interrupt");
            }
        };
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(future);

        try {
            assertThatThrownBy(() -> producer.publish(message()))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted())
                    .as("Thread.currentThread().interrupt() 호출로 플래그 복구 필수")
                    .isTrue();
        } finally {
            // 테스트 격리 — 다음 테스트로 interrupt 플래그 전파 차단
            Thread.interrupted();
        }
    }

    private OutboxEventMessage message() {
        return new OutboxEventMessage(
                1L,
                UUID.randomUUID().toString(),
                "stock.deducted",
                "partition-1",
                "{\"k\":1}"
        );
    }
}
