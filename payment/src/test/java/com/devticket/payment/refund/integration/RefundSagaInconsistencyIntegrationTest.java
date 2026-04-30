package com.devticket.payment.refund.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.refund.application.saga.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.presentation.consumer.RefundSagaHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 환불 saga 부정합(REFUND_NOT_FOUND) 흐름 통합 테스트.
 *
 *  B1) 부정합 메시지 → 재시도 0회로 즉시 DLT 이동
 *  B2) DLT 메시지에 원본 페이로드 + X-Message-Id 헤더 보존
 *  B3) 일반 실패는 기존대로 3회 재시도 후 DLT (회귀 가드)
 *  B4) 정상 처리된 messageId 재발행 시 dedup 동작 (부정합 분류가 dedup 경로를 깨지 않음)
 *
 * RefundSagaHandler 를 MockitoBean 으로 교체해 핸들러 시점에서 원하는 예외를 주입한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {
        KafkaTopics.REFUND_ORDER_DONE,
        KafkaTopics.REFUND_ORDER_DONE + ".DLT"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefundSagaInconsistencyIntegrationTest {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private EmbeddedKafkaBroker broker;

    @MockitoBean private RefundSagaHandler refundSagaHandler;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private KafkaConsumer<String, String> dltConsumer;
    private final LinkedBlockingQueue<ConsumerRecord<String, String>> dltRecords = new LinkedBlockingQueue<>();

    @BeforeEach
    void subscribeDlt() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
            "dlt-test-" + UUID.randomUUID(), "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Properties javaProps = new Properties();
        javaProps.putAll(props);
        dltConsumer = new KafkaConsumer<>(javaProps);
        dltConsumer.subscribe(List.of(KafkaTopics.REFUND_ORDER_DONE + ".DLT"));
        dltRecords.clear();
    }

    @AfterEach
    void closeDlt() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    // =====================================================================
    // B1: REFUND_NOT_FOUND → 재시도 0회로 즉시 DLT 이동
    // =====================================================================
    @Test
    @DisplayName("[B1] REFUND_NOT_FOUND 발생 시 재시도 없이 5초 내 DLT 도달")
    void 부정합_즉시_DLT() throws Exception {
        // given: 핸들러가 REFUND_NOT_FOUND 던지도록 주입
        willThrow(new RefundException(RefundErrorCode.REFUND_NOT_FOUND))
            .given(refundSagaHandler).onOrderDoneAndMark(any(), anyString(), anyString());

        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String messageId = UUID.randomUUID().toString();
        String wrappedPayload = wrapOutboxPayload(messageId, refundId, orderId);

        // when: 메시지 발행
        Instant publishedAt = Instant.now();
        kafkaTemplate.send(buildRecord(messageId, wrappedPayload)).get();

        // then: 같은 messageId 의 DLT 메시지가 5초 내 도달 (재시도 backoff 없이)
        ConsumerRecord<String, String> dlt = waitForDlt(messageId, Duration.ofSeconds(7));
        assertThat(dlt).as("DLT 메시지 미수신").isNotNull();
        Duration elapsed = Duration.between(publishedAt, Instant.now());
        assertThat(elapsed).as("재시도 없이 즉시 DLT 이동해야 함")
            .isLessThan(Duration.ofSeconds(5));

        // 핸들러는 정확히 1회만 호출됨 (재시도 0회)
        then(refundSagaHandler).should(times(1))
            .onOrderDoneAndMark(any(), anyString(), anyString());
    }

    // =====================================================================
    // B2: DLT 메시지의 원본 페이로드 / X-Message-Id 보존
    // =====================================================================
    @Test
    @DisplayName("[B2] DLT 메시지에 원본 페이로드와 X-Message-Id 헤더 보존")
    void DLT_페이로드_헤더_보존() throws Exception {
        willThrow(new RefundException(RefundErrorCode.REFUND_NOT_FOUND))
            .given(refundSagaHandler).onOrderDoneAndMark(any(), anyString(), anyString());

        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String messageId = UUID.randomUUID().toString();
        String wrappedPayload = wrapOutboxPayload(messageId, refundId, orderId);

        kafkaTemplate.send(buildRecord(messageId, wrappedPayload)).get();

        ConsumerRecord<String, String> dlt = waitForDlt(messageId, Duration.ofSeconds(7));
        assertThat(dlt).isNotNull();

        // 페이로드 동일
        assertThat(dlt.value()).isEqualTo(wrappedPayload);

        // X-Message-Id 헤더 보존
        var header = dlt.headers().lastHeader("X-Message-Id");
        assertThat(header).as("X-Message-Id 헤더").isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo(messageId);
    }

    // =====================================================================
    // B3: 일반 실패는 3회 재시도 후 DLT (회귀 가드)
    // =====================================================================
    @Test
    @DisplayName("[B3] 일반 실패(REFUND_INVALID_REQUEST)는 backoff 후 DLT 도착, 핸들러 4회 호출")
    void 일반실패_3회재시도_후_DLT() throws Exception {
        // given: 부정합이 아닌 일반 RefundException
        willThrow(new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST))
            .given(refundSagaHandler).onOrderDoneAndMark(any(), anyString(), anyString());

        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String messageId = UUID.randomUUID().toString();
        String wrappedPayload = wrapOutboxPayload(messageId, refundId, orderId);

        // when
        Instant publishedAt = Instant.now();
        kafkaTemplate.send(buildRecord(messageId, wrappedPayload)).get();

        // then: 핸들러는 1(최초) + 3(재시도) = 총 4회 호출됨, backoff 2+4+8=14s
        // DefaultErrorHandler maxAttempts=3 = 재시도 3회
        then(refundSagaHandler).should(timeout(Duration.ofSeconds(25).toMillis()).times(4))
            .onOrderDoneAndMark(any(), anyString(), anyString());

        // DLT 도착 확인 — 즉시(<5s) 도착하면 회귀
        ConsumerRecord<String, String> dlt = waitForDlt(messageId, Duration.ofSeconds(25));
        assertThat(dlt).isNotNull();
        Duration elapsed = Duration.between(publishedAt, Instant.now());
        assertThat(elapsed).as("일반 실패는 backoff 후에야 DLT 도달")
            .isGreaterThan(Duration.ofSeconds(5));
    }

    // =====================================================================
    // B4: 정상 처리 회귀 가드
    //
    // 부정합 분류 도입이 정상 경로(예외 없음 → ack → DLT 미도달)를 깨지 않는지 회귀 가드.
    // (DB-기반 dedup 자체 검증은 별도 단위 테스트 대상)
    // =====================================================================
    @Test
    @DisplayName("[B4] 정상 처리는 ack 되고 DLT 미도달 (회귀 가드)")
    void 정상_처리_DLT_미도달() throws Exception {
        // given: 핸들러가 예외 없이 정상 반환
        willDoNothing().given(refundSagaHandler).onOrderDoneAndMark(any(), anyString(), anyString());

        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String messageId = UUID.randomUUID().toString();
        String wrappedPayload = wrapOutboxPayload(messageId, refundId, orderId);

        // when
        kafkaTemplate.send(buildRecord(messageId, wrappedPayload)).get();

        // then: 핸들러 1회 호출
        then(refundSagaHandler).should(timeout(Duration.ofSeconds(5).toMillis()).times(1))
            .onOrderDoneAndMark(any(), anyString(), anyString());

        // 같은 messageId 의 DLT 도달 없음
        ConsumerRecord<String, String> dlt = waitForDlt(messageId, Duration.ofSeconds(3));
        assertThat(dlt).as("정상 처리 시 DLT 도달 없어야 함").isNull();

        // 다른 토픽 핸들러는 호출되지 않음
        then(refundSagaHandler).should(never()).startAndMark(any(), anyString(), anyString());
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private ProducerRecord<String, String> buildRecord(String messageId, String wrappedPayload) {
        ProducerRecord<String, String> r = new ProducerRecord<>(
            KafkaTopics.REFUND_ORDER_DONE, /*partition*/ null, "key-" + messageId, wrappedPayload);
        r.headers().add("X-Message-Id", messageId.getBytes(StandardCharsets.UTF_8));
        return r;
    }

    private String wrapOutboxPayload(String messageId, UUID refundId, UUID orderId) throws Exception {
        String inner = objectMapper.writeValueAsString(
            new RefundOrderDoneEvent(refundId, orderId, Instant.now()));
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("messageId", messageId);
        wrapper.put("eventType", "refund.order.done");
        wrapper.put("topic", KafkaTopics.REFUND_ORDER_DONE);
        wrapper.put("partitionKey", orderId.toString());
        wrapper.put("payload", inner);
        wrapper.put("timestamp", Instant.now().toString());
        return objectMapper.writeValueAsString(wrapper);
    }

    /**
     * DLT 토픽을 폴링해 지정된 messageId 의 메시지가 도착할 때까지 대기.
     * 다른 테스트 메시지의 늦은 도달은 무시(테스트 간 격리).
     */
    private ConsumerRecord<String, String> waitForDlt(String expectedMessageId, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> polled = dltConsumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : polled) {
                var h = r.headers().lastHeader("X-Message-Id");
                if (h != null && expectedMessageId.equals(new String(h.value(), StandardCharsets.UTF_8))) {
                    dltRecords.add(r);
                    return r;
                }
            }
        }
        return null;
    }
}
