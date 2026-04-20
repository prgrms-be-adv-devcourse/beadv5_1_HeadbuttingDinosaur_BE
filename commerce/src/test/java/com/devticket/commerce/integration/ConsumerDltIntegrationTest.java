package com.devticket.commerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.devticket.commerce.common.enums.PaymentMethod;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.event.PaymentCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockProvider;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;

/**
 * IT-3 Consumer DLT 이동 통합테스트 — 멘토 피드백 "무한 재시도 아님" 증명.
 *
 * <p>운영 설정과 차이 (테스트 속도 최적화 목적):
 * <ul>
 *   <li>운영: {@code KafkaConsumerConfig.kafkaListenerContainerFactory()} —
 *       {@code ExponentialBackOff(2→4→8초, maxElapsedTime=14s)} + {@code DeadLetterPublishingRecoverer}
 *       로 {@code {topic}.DLT} 로 이동. 실행에 최소 14초 필요
 *   <li>테스트: {@link TestDltConfig} 에서 {@link DefaultErrorHandler} 를 {@code @Primary} 로 덮어써
 *       {@code FixedBackOff(100ms, 2회)} 로 동작 → 수백 ms 내 DLT 도달 확인
 *   <li>검증 대상은 동일한 "예외 → 제한 횟수 재시도 → DLT 이동" 경로 — 운영 빈도 동일한 구조 사용
 * </ul>
 *
 * <p>시나리오: 존재하지 않는 orderId로 payment.completed 발행 → OrderService.processPaymentCompleted
 * 에서 {@code BusinessException(ORDER_NOT_FOUND)} → 재시도 2회 소진 → {@code payment.completed.DLT}
 * 수신 + 원본 X-Message-Id 헤더 보존 확인.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.PAYMENT_COMPLETED, KafkaTopics.PAYMENT_COMPLETED + ".DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
@Import(ConsumerDltIntegrationTest.TestDltConfig.class)
class ConsumerDltIntegrationTest {

    @TestConfiguration
    static class TestDltConfig {
        /**
         * 테스트 전용 ErrorHandler — 운영 {@code KafkaConsumerConfig} 의 빈을 {@code @Primary} 로 덮어씀.
         * FixedBackOff(100ms, 2): 100ms 간격 2회 재시도 후 DLT 이동. 테스트 속도 최적화 목적.
         */
        @Bean
        @Primary
        public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
            DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
            return new DefaultErrorHandler(recoverer, new FixedBackOff(100L, 2L));
        }
    }

    @MockitoBean private LockProvider lockProvider;

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate txTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        given(lockProvider.lock(any())).willReturn(Optional.of(() -> {}));
    }

    @AfterEach
    void cleanup() {
        txTemplate.executeWithoutResult(s -> {
            entityManager.createQuery("DELETE FROM Outbox").executeUpdate();
            entityManager.createQuery("DELETE FROM ProcessedMessage").executeUpdate();
        });
    }

    @Test
    @DisplayName("IT-3: payment.completed 예외 → 재시도 소진 → payment.completed.DLT 수신 (무한 재시도 아님 증명)")
    void movesToDltAfterRetriesExhausted() throws Exception {
        // given — 존재하지 않는 orderId로 PaymentCompletedEvent 발행
        //   OrderService.processPaymentCompleted → orderRepository.findByOrderId → empty → BusinessException(ORDER_NOT_FOUND)
        UUID nonExistentOrderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                nonExistentOrderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentMethod.PG,
                10_000,
                List.of(),
                Instant.now()
        );

        try (Consumer<String, String> dltConsumer =
                     createTestConsumer(KafkaTopics.PAYMENT_COMPLETED + ".DLT")) {

            // when
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaTopics.PAYMENT_COMPLETED,
                    nonExistentOrderId.toString(),
                    objectMapper.writeValueAsString(event)
            );
            record.headers().add("X-Message-Id",
                    messageId.toString().getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

            // then — DLT 토픽에 메시지 도달 (재시도 소진 후 이동)
            ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(
                    dltConsumer, KafkaTopics.PAYMENT_COMPLETED + ".DLT", Duration.ofSeconds(30));

            assertThat(dltRecord.value()).contains(nonExistentOrderId.toString());

            // X-Message-Id 헤더가 DLT 메시지에도 보존되는지 (재처리 워커의 dedup 전제)
            assertThat(dltRecord.headers().lastHeader("X-Message-Id")).isNotNull();
            String preservedMessageId = new String(
                    dltRecord.headers().lastHeader("X-Message-Id").value(), StandardCharsets.UTF_8);
            assertThat(preservedMessageId).isEqualTo(messageId.toString());
        }
    }

    private Consumer<String, String> createTestConsumer(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "dlt-test-consumer-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
