package com.devticket.commerce.order.presentation.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.order.application.usecase.OrderUsecase;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedConsumerTest {

    @Mock private OrderUsecase orderUsecase;
    @Mock private Acknowledgment ack;

    private PaymentCompletedConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        consumer = new PaymentCompletedConsumer(orderUsecase, objectMapper);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> recordWithHeader(UUID messageId, String payload) {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(KafkaTopics.PAYMENT_COMPLETED, 0, 0L, null, payload);
        record.headers().add(new RecordHeader(
                "X-Message-Id", messageId.toString().getBytes(StandardCharsets.UTF_8)));
        return record;
    }

    private ConsumerRecord<String, String> recordWithoutHeader(String payload) {
        return new ConsumerRecord<>(KafkaTopics.PAYMENT_COMPLETED, 0, 0L, null, payload);
    }

    // ── 테스트 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("정상 흐름")
    class HappyPath {

        @Test
        @DisplayName("X-Message-Id 헤더 정상 → OrderUsecase 호출 + ACK")
        void 헤더_정상_처리시_서비스를_호출하고_ACK한다() {
            // given
            UUID messageId = UUID.randomUUID();
            String payload = "{\"orderId\":\"" + UUID.randomUUID() + "\"}";
            ConsumerRecord<String, String> record = recordWithHeader(messageId, payload);

            // when
            consumer.consume(record, ack);

            // then
            then(orderUsecase).should()
                    .processPaymentCompleted(messageId, KafkaTopics.PAYMENT_COMPLETED, payload);
            then(ack).should().acknowledge();
        }

        @Test
        @DisplayName("헤더 없음 + 본문 messageId fallback → 처리 + ACK")
        void 헤더가_없어도_본문_messageId로_fallback하여_처리한다() {
            // given — Payment Outbox 발행 시 헤더 누락 시나리오
            UUID messageId = UUID.randomUUID();
            String payload = "{\"messageId\":\"" + messageId + "\",\"orderId\":\"xyz\"}";
            ConsumerRecord<String, String> record = recordWithoutHeader(payload);

            // when
            consumer.consume(record, ack);

            // then
            then(orderUsecase).should()
                    .processPaymentCompleted(eq(messageId), eq(KafkaTopics.PAYMENT_COMPLETED), any());
            then(ack).should().acknowledge();
        }

        @Test
        @DisplayName("Outbox wrapper(payload 필드) → payload 문자열만 추출되어 전달")
        void Outbox_wrapper_payload_필드를_추출하여_전달한다() {
            // given — {"messageId":"...","payload":"inner-json-string"}
            UUID messageId = UUID.randomUUID();
            String innerPayload = "inner-payload-content";
            String wrapped = "{\"messageId\":\"" + messageId + "\","
                    + "\"payload\":\"" + innerPayload + "\"}";
            ConsumerRecord<String, String> record = recordWithoutHeader(wrapped);

            // when
            consumer.consume(record, ack);

            // then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            then(orderUsecase).should()
                    .processPaymentCompleted(eq(messageId), eq(KafkaTopics.PAYMENT_COMPLETED),
                            payloadCaptor.capture());
            assertThat(payloadCaptor.getValue()).isEqualTo(innerPayload);
            then(ack).should().acknowledge();
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("DataIntegrityViolationException(processed_message UNIQUE 충돌) → 상태 변화 없이 ACK")
        void 중복_메시지_수신시_상태변화_없이_ACK한다() {
            // given — processed_message UNIQUE 충돌 시나리오 (동일 messageId 재수신)
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record = recordWithHeader(messageId, "{}");
            org.hibernate.exception.ConstraintViolationException hibernateCause =
                    new org.hibernate.exception.ConstraintViolationException(
                            "duplicate key", null, "uk_processed_message_message_id_topic");
            willThrow(new DataIntegrityViolationException("duplicate key", hibernateCause))
                    .given(orderUsecase).processPaymentCompleted(any(), any(), any());

            // when
            consumer.consume(record, ack);

            // then — 예외는 삼켜지고 ACK만 호출 (재시도 없이 종료)
            then(ack).should().acknowledge();
        }

        @Test
        @DisplayName("UNIQUE 외 constraint 위반 DIV → rethrow + ACK 없음 (Kafka 재시도 → DLT)")
        void UNIQUE_외_무결성_위반은_rethrow된다() {
            // given — 티켓/Outbox 등 다른 제약 위반 시나리오
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record = recordWithHeader(messageId, "{}");
            org.hibernate.exception.ConstraintViolationException hibernateCause =
                    new org.hibernate.exception.ConstraintViolationException(
                            "fk violation", null, "fk_ticket_order_id");
            willThrow(new DataIntegrityViolationException("fk violation", hibernateCause))
                    .given(orderUsecase).processPaymentCompleted(any(), any(), any());

            // when & then — 재시도 경로로 rethrow, ACK 호출 없음
            assertThatThrownBy(() -> consumer.consume(record, ack))
                    .isInstanceOf(DataIntegrityViolationException.class);
            then(ack).should(never()).acknowledge();
        }

        @Test
        @DisplayName("Hibernate cause 없는 DIV → rethrow + ACK 없음")
        void Hibernate_cause_없는_DIV는_rethrow된다() {
            // given — DIV는 발생했으나 ConstraintViolationException 원인 체인 없음
            UUID messageId = UUID.randomUUID();
            ConsumerRecord<String, String> record = recordWithHeader(messageId, "{}");
            willThrow(new DataIntegrityViolationException("unknown"))
                    .given(orderUsecase).processPaymentCompleted(any(), any(), any());

            // when & then
            assertThatThrownBy(() -> consumer.consume(record, ack))
                    .isInstanceOf(DataIntegrityViolationException.class);
            then(ack).should(never()).acknowledge();
        }
    }

    @Nested
    @DisplayName("messageId 추출 실패")
    class InvalidMessageId {

        @Test
        @DisplayName("헤더·본문 모두 messageId 없음 → IllegalArgumentException + ACK 없음")
        void messageId_추출_실패시_예외를_던지고_ACK하지_않는다() {
            // given
            ConsumerRecord<String, String> record = recordWithoutHeader("{\"orderId\":\"xyz\"}");

            // when & then
            assertThatThrownBy(() -> consumer.consume(record, ack))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("messageId");

            then(ack).should(never()).acknowledge();
            then(orderUsecase).shouldHaveNoInteractions();
        }
    }
}
