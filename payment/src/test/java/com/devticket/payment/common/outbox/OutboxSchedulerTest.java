package com.devticket.payment.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox 스케줄러 (OutboxScheduler)")
class OutboxSchedulerTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxEventProducer outboxEventProducer;

    @InjectMocks
    private OutboxScheduler scheduler;

    private Outbox createOutbox(String topic, String partitionKey) {
        return Outbox.create(
            "agg-id-001",
            "payment.completed",
            topic,
            partitionKey,
            "{\"orderId\":\"order-uuid-001\"}"
        );
    }

    // =====================================================================
    // PENDING 조회
    // =====================================================================

    @Nested
    @DisplayName("PENDING 조회")
    class FindPending {

        @Test
        void PENDING_없으면_producer_미호출() {
            // given
            given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of());

            // when
            scheduler.publishPendingEvents();

            // then
            then(outboxEventProducer).should(never()).send(any(), any(), any());
        }
    }

    // =====================================================================
    // 발행 성공
    // =====================================================================

    @Nested
    @DisplayName("발행 성공")
    class PublishSuccess {

        @Test
        void topic과_partitionKey_기반으로_발행() {
            // given
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
            given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of(outbox));

            // when
            scheduler.publishPendingEvents();

            // then
            then(outboxEventProducer).should(times(1))
                .send(eq("payment.completed"), eq("order-uuid-001"), any());
        }

        @Test
        void 발행_성공_후_status가_SENT() {
            // given
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
            given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of(outbox));

            // when
            scheduler.publishPendingEvents();

            // then
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        }

        @Test
        void 발행_성공_후_sentAt_채워짐() {
            // given
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
            given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of(outbox));

            // when
            scheduler.publishPendingEvents();

            // then
            assertThat(outbox.getSentAt()).isNotNull();
        }

        @Test
        void partitionKey_null이면_aggregateId를_key로_사용() {
            // given
            Outbox outbox = createOutbox("payment.completed", null);
            given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of(outbox));

            // when
            scheduler.publishPendingEvents();

            // then
            then(outboxEventProducer).should(times(1))
                .send(eq("payment.completed"), eq("agg-id-001"), any());
        }

        @Test
        void 여러_건_모두_발행() {
            // given
            Outbox outbox1 = createOutbox("payment.completed", "order-001");
            Outbox outbox2 = createOutbox("payment.completed", "order-002");
            Outbox outbox3 = createOutbox("payment.completed", "order-003");
            given(outboxRepository.findPendingForRetry(any(), any()))
                .willReturn(List.of(outbox1, outbox2, outbox3));

            // when
            scheduler.publishPendingEvents();

            // then
            then(outboxEventProducer).should(times(3)).send(any(), any(), any());
            assertThat(outbox1.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(outbox2.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(outbox3.getStatus()).isEqualTo(OutboxStatus.SENT);
        }
    }

    // =====================================================================
    // 발행 실패 / 오류 격리
    // =====================================================================

    @Nested
    @DisplayName("발행 실패 — 오류 격리")
    class PublishFailure {

        @Test
        void 발행_실패_시_retryCount_증가() {
            // given
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
            given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of(outbox));
            willThrow(new OutboxPublishException("Kafka 연결 실패", new RuntimeException()))
                .given(outboxEventProducer).send(any(), any(), any());

            // when
            scheduler.publishPendingEvents();

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        void 발행_실패_시_nextRetryAt_설정() {
            // given
            Outbox outbox = createOutbox("payment.completed", "order-uuid-001");
            given(outboxRepository.findPendingForRetry(any(), any())).willReturn(List.of(outbox));
            willThrow(new OutboxPublishException("Kafka 연결 실패", new RuntimeException()))
                .given(outboxEventProducer).send(any(), any(), any());

            // when
            scheduler.publishPendingEvents();

            // then
            assertThat(outbox.getNextRetryAt()).isNotNull();
        }

        @Test
        void 특정_건_실패해도_나머지_건_계속_처리() {
            // given: outbox2 발행 시 예외
            Outbox outbox1 = createOutbox("payment.completed", "order-001");
            Outbox outbox2 = createOutbox("payment.completed", "order-002");
            Outbox outbox3 = createOutbox("payment.completed", "order-003");
            given(outboxRepository.findPendingForRetry(any(), any()))
                .willReturn(List.of(outbox1, outbox2, outbox3));
            lenient().doThrow(new OutboxPublishException("Kafka 오류", new RuntimeException()))
                .when(outboxEventProducer).send(any(), eq("order-002"), any());

            // when
            scheduler.publishPendingEvents();

            // then
            assertThat(outbox1.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(outbox2.getRetryCount()).isEqualTo(1);
            assertThat(outbox3.getStatus()).isEqualTo(OutboxStatus.SENT);
        }

        @Test
        void 모든_건_실패해도_스케줄러_예외_미전파() {
            // given
            Outbox outbox1 = createOutbox("payment.completed", "order-001");
            Outbox outbox2 = createOutbox("payment.completed", "order-002");
            given(outboxRepository.findPendingForRetry(any(), any()))
                .willReturn(List.of(outbox1, outbox2));
            willThrow(new OutboxPublishException("전체 오류", new RuntimeException()))
                .given(outboxEventProducer).send(any(), any(), any());

            // when & then: 스케줄러 자체는 예외 없이 정상 종료
            org.assertj.core.api.Assertions.assertThatCode(scheduler::publishPendingEvents)
                .doesNotThrowAnyException();
        }
    }
}
