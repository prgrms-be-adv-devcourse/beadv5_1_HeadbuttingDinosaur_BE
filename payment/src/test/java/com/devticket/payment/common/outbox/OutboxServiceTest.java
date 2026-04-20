package com.devticket.payment.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox 서비스 (OutboxService)")
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxEventProducer outboxEventProducer;

    @Mock
    private OutboxAfterCommitPublisher outboxAfterCommitPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    private Outbox createOutbox(String topic, String partitionKey) {
        return Outbox.create(
            "agg-id-001",
            partitionKey,
            "payment.completed",
            topic,
            "{\"orderId\":\"order-uuid-001\"}"
        );
    }

    @Nested
    @DisplayName("processOne — 발행 성공")
    class PublishSuccess {

        @Test
        void topic과_partitionKey_기반으로_발행() {
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");

            outboxService.processOne(outbox);

            ArgumentCaptor<OutboxEventMessage> captor = ArgumentCaptor.forClass(OutboxEventMessage.class);
            then(outboxEventProducer).should(times(1)).publish(captor.capture());
            assertThat(captor.getValue().topic()).isEqualTo("payment.completed");
            assertThat(captor.getValue().partitionKey()).isEqualTo("order-uuid-001");
        }

        @Test
        void 발행_성공_후_status가_SENT() {
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");

            outboxService.processOne(outbox);

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        }

        @Test
        void 발행_성공_후_sentAt_채워짐() {
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");

            outboxService.processOne(outbox);

            assertThat(outbox.getSentAt()).isNotNull();
        }

        @Test
        void partitionKey_null이면_aggregateId를_key로_사용() {
            Outbox outbox = createOutbox("payment.completed", null);

            outboxService.processOne(outbox);

            ArgumentCaptor<OutboxEventMessage> captor = ArgumentCaptor.forClass(OutboxEventMessage.class);
            then(outboxEventProducer).should(times(1)).publish(captor.capture());
            assertThat(captor.getValue().partitionKey()).isEqualTo("agg-id-001");
        }

        @Test
        void 발행_성공_후_save_호출() {
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");

            outboxService.processOne(outbox);

            then(outboxRepository).should(times(1)).save(outbox);
        }
    }

    @Nested
    @DisplayName("processOne — 발행 실패 (오류 격리)")
    class PublishFailure {

        @Test
        void 발행_실패_시_retryCount_증가() {
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
            willThrow(new OutboxPublishException("Kafka 연결 실패", new RuntimeException()))
                .given(outboxEventProducer).publish(any());

            outboxService.processOne(outbox);

            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        void 발행_실패_시_nextRetryAt_설정() {
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
            willThrow(new OutboxPublishException("Kafka 연결 실패", new RuntimeException()))
                .given(outboxEventProducer).publish(any());

            outboxService.processOne(outbox);

            assertThat(outbox.getNextRetryAt()).isNotNull();
        }

        @Test
        void 발행_실패_시_예외_미전파() {
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
            willThrow(new OutboxPublishException("전체 오류", new RuntimeException()))
                .given(outboxEventProducer).publish(any());

            assertThatCode(() -> outboxService.processOne(outbox))
                .doesNotThrowAnyException();
        }

        @Test
        void 발행_실패여도_save_호출() {
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
            willThrow(new OutboxPublishException("Kafka 오류", new RuntimeException()))
                .given(outboxEventProducer).publish(any());

            outboxService.processOne(outbox);

            then(outboxRepository).should(times(1)).save(outbox);
        }
    }
}
