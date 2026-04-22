package com.devticket.payment.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.payment.common.outbox.OutboxEventProducer;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Outbox 이중 발행 완화(Issue #481) — Producer 타임아웃과
 * OutboxEventProducer.sendTimeoutMs 간 정합성 회귀 방지.
 *
 * 불변식:
 *   max.block.ms < request.timeout.ms ≤ delivery.timeout.ms < sendTimeoutMs
 * 하나라도 깨지면 앱 타임아웃 이전에 Producer 재시도가 종료되지 않아 이중 발행 위험 재발.
 *
 * 운영값(@Value default): 500/1000/1500 + sendTimeoutMs 2000.
 * 테스트 프로파일(application-test.yml): 3000/5000/8000 + sendTimeoutMs 10000.
 * 두 세트 모두 불변식을 만족해야 한다.
 */
class KafkaProducerConfigTest {

    @Test
    void OutboxEventProducer_sendTimeoutMs_기본값이_운영값_2000ms이다() {
        OutboxEventProducer producer = new OutboxEventProducer(null, null);

        long sendTimeoutMs = (long) ReflectionTestUtils.getField(producer, "sendTimeoutMs");

        assertThat(sendTimeoutMs)
                .as("Spring 주입 실패 시 폴백 초기값 — @Value default 와도 일치해야 함")
                .isEqualTo(2000L);
    }

    @Test
    void producerFactory_운영_기본값이_적용되고_ACKS_idempotence가_고정된다() {
        Map<String, Object> props = buildProps(500, 1000, 1500);

        assertThat(props)
                .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500)
                .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1500)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    }

    @Test
    void 운영_기본값은_불변식을_만족한다() {
        Map<String, Object> props = buildProps(500, 1000, 1500);

        assertInvariant(props, 2000L);
    }

    @Test
    void 테스트_프로파일_완화값도_불변식을_만족한다() {
        Map<String, Object> props = buildProps(3000, 5000, 8000);

        assertInvariant(props, 10000L);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildProps(int maxBlockMs, int requestTimeoutMs, int deliveryTimeoutMs) {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "maxBlockMs", maxBlockMs);
        ReflectionTestUtils.setField(config, "requestTimeoutMs", requestTimeoutMs);
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", deliveryTimeoutMs);

        return ((DefaultKafkaProducerFactory<String, String>) config.producerFactory())
                .getConfigurationProperties();
    }

    private void assertInvariant(Map<String, Object> props, long sendTimeoutMs) {
        int maxBlock = (int) props.get(ProducerConfig.MAX_BLOCK_MS_CONFIG);
        int request = (int) props.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG);
        int delivery = (int) props.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);

        assertThat(maxBlock)
                .as("max.block.ms(%d) < request.timeout.ms(%d) — 메타데이터 블로킹 조기 이탈",
                        maxBlock, request)
                .isLessThan(request);
        assertThat(request)
                .as("request.timeout.ms(%d) ≤ delivery.timeout.ms(%d) — Kafka 클라이언트 필수 제약",
                        request, delivery)
                .isLessThanOrEqualTo(delivery);
        assertThat((long) delivery)
                .as("delivery.timeout.ms(%d) < sendTimeoutMs(%d) — 앱 타임아웃 이전 Producer 재시도 확정 종료",
                        delivery, sendTimeoutMs)
                .isLessThan(sendTimeoutMs);
    }
}
