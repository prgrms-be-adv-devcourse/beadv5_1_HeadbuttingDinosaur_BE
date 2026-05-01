package com.devticket.payment.refund.presentation.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.messaging.MessageDeduplicationService;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.exception.RefundInconsistencyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundSagaConsumer — 부정합/일반 실패 분기")
class RefundSagaConsumerTest {

    @Mock private RefundSagaHandler handler;
    @Mock private MessageDeduplicationService deduplicationService;
    @Mock private Acknowledgment ack;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private RefundSagaConsumer consumer;

    @BeforeEach
    void setUp() {
        // ObjectMapper 는 @InjectMocks 가 잡지 못하므로 직접 주입
        org.springframework.test.util.ReflectionTestUtils.setField(
            consumer, "objectMapper", objectMapper);
    }

    @Test
    void REFUND_NOT_FOUND_은_RefundInconsistencyException_으로_변환되어_throw() throws Exception {
        // given: order.done 메시지 도착, 핸들러가 REFUND_NOT_FOUND 던짐
        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = wrapOutboxPayload(orderDonePayload(refundId, orderId));
        ConsumerRecord<String, String> record = recordOf(KafkaTopics.REFUND_ORDER_DONE, payload);

        given(deduplicationService.isDuplicate(anyString())).willReturn(false);
        willThrow(new RefundException(RefundErrorCode.REFUND_NOT_FOUND))
            .given(handler).onOrderDoneAndMark(any(), anyString(), eq(KafkaTopics.REFUND_ORDER_DONE));

        // when & then: 부정합 예외로 래핑되어 던져짐 (KafkaConsumerConfig 가 not-retryable 로 인식)
        assertThatThrownBy(() -> consumer.consumeOrderDone(record, ack))
            .isInstanceOf(RefundInconsistencyException.class)
            .satisfies(ex -> {
                RefundInconsistencyException ie = (RefundInconsistencyException) ex;
                assertThat(ie.getTopic()).isEqualTo(KafkaTopics.REFUND_ORDER_DONE);
                assertThat(ie.getMessageId()).isNotBlank();
                assertThat(ie.getPayloadSnapshot()).isEqualTo(payload);
                assertThat(ie.getCause()).isInstanceOf(RefundException.class);
            });

        then(ack).should(never()).acknowledge();
    }

    @Test
    void 원인체인에서_REFUND_NOT_FOUND_탐색_가능() throws Exception {
        // given: 핸들러가 wrapping 한 RuntimeException 안에 REFUND_NOT_FOUND 가 들어 있어도 감지돼야 함
        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = wrapOutboxPayload(orderDonePayload(refundId, orderId));
        ConsumerRecord<String, String> record = recordOf(KafkaTopics.REFUND_ORDER_DONE, payload);

        RuntimeException wrapped = new RuntimeException(
            "outer", new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
        given(deduplicationService.isDuplicate(anyString())).willReturn(false);
        willThrow(wrapped).given(handler).onOrderDoneAndMark(any(), anyString(), anyString());

        // when & then
        assertThatThrownBy(() -> consumer.consumeOrderDone(record, ack))
            .isInstanceOf(RefundInconsistencyException.class);
    }

    @Test
    void 일반_처리실패는_RuntimeException_그대로_재시도_경로() throws Exception {
        // given: REFUND_NOT_FOUND 가 아닌 일반 예외 → 기존 동작(재시도 후 DLT) 유지
        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = wrapOutboxPayload(orderDonePayload(refundId, orderId));
        ConsumerRecord<String, String> record = recordOf(KafkaTopics.REFUND_ORDER_DONE, payload);

        given(deduplicationService.isDuplicate(anyString())).willReturn(false);
        willThrow(new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST))
            .given(handler).onOrderDoneAndMark(any(), anyString(), anyString());

        // when & then: 부정합이 아니므로 일반 RuntimeException
        assertThatThrownBy(() -> consumer.consumeOrderDone(record, ack))
            .isInstanceOf(RuntimeException.class)
            .isNotInstanceOf(RefundInconsistencyException.class);
    }

    @Test
    void 정상_처리시_핸들러_호출_및_ack() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = wrapOutboxPayload(orderDonePayload(refundId, orderId));
        ConsumerRecord<String, String> record = recordOf(KafkaTopics.REFUND_ORDER_DONE, payload);

        given(deduplicationService.isDuplicate(anyString())).willReturn(false);

        consumer.consumeOrderDone(record, ack);

        then(handler).should(times(1)).onOrderDoneAndMark(any(), anyString(), eq(KafkaTopics.REFUND_ORDER_DONE));
        then(ack).should(times(1)).acknowledge();
    }

    @Test
    void 중복_메시지는_핸들러_미호출_즉시_ack() {
        ConsumerRecord<String, String> record = recordOf(KafkaTopics.REFUND_ORDER_DONE, "{}");
        given(deduplicationService.isDuplicate(anyString())).willReturn(true);

        consumer.consumeOrderDone(record, ack);

        then(handler).should(never()).onOrderDoneAndMark(any(), anyString(), anyString());
        then(ack).should(times(1)).acknowledge();
    }

    // ===========================================================
    // helpers
    // ===========================================================

    private ConsumerRecord<String, String> recordOf(String topic, String value) {
        ConsumerRecord<String, String> r = new ConsumerRecord<>(
            topic, 0, 8L, "key", value);
        // X-Message-Id 헤더 부착(없어도 fallback 으로 채워지지만 명시적으로 둠)
        r.headers().add("X-Message-Id",
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        return r;
    }

    private String orderDonePayload(UUID refundId, UUID orderId) throws Exception {
        return objectMapper.writeValueAsString(new com.devticket.payment.refund.application.saga.event.RefundOrderDoneEvent(
            refundId, orderId, Instant.now()));
    }

    private String wrapOutboxPayload(String inner) throws Exception {
        // OutboxPayloadExtractor 가 fallback 으로 직접 파싱하므로 래핑 없이도 통과 가능.
        // 운영 메시지처럼 보이도록 wrapper 구조를 흉내낸다.
        return objectMapper.writeValueAsString(new OutboxWrapper(
            UUID.randomUUID().toString(), "refund.order.done", inner, Instant.now().toString()));
    }

    private record OutboxWrapper(String messageId, String eventType, String payload, String timestamp) {}
}
