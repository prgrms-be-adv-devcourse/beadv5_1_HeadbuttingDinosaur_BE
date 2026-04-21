package com.devticket.commerce.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.commerce.common.outbox.OutboxEventProducer;
import java.lang.reflect.Field;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Outbox 이중 발행 완화(Issue #481, commit 5e2c3d5) — Producer 타임아웃과
 * OutboxEventProducer.SEND_TIMEOUT_SECONDS 간 정합성 회귀 방지.
 *
 * 불변식:
 *   max.block.ms  <  request.timeout.ms  <=  delivery.timeout.ms  <  SEND_TIMEOUT_SECONDS * 1000
 * 하나라도 깨지면 앱 타임아웃 이전에 Producer 재시도가 종료되지 않아 이중 발행 위험 재발.
 */
class KafkaProducerConfigTest {

    @Test
    void producerFactory_타임아웃_설정이_커밋된_기준값과_일치한다() {
        // given
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");

        // when
        ProducerFactory<String, String> factory = config.producerFactory();
        Map<String, Object> props =
                ((DefaultKafkaProducerFactory<String, String>) factory).getConfigurationProperties();

        // then
        assertThat(props)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1500)
                .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000)
                .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    }

    @Test
    void delivery_timeout_ms는_OutboxEventProducer_SEND_TIMEOUT_SECONDS_미만으로_설정된다() throws Exception {
        // given
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        Map<String, Object> props = ((DefaultKafkaProducerFactory<String, String>) config.producerFactory())
                .getConfigurationProperties();

        Field sendTimeoutField = OutboxEventProducer.class.getDeclaredField("SEND_TIMEOUT_SECONDS");
        sendTimeoutField.setAccessible(true);
        long sendTimeoutMs = (long) sendTimeoutField.get(null) * 1000L;

        int deliveryTimeoutMs = (int) props.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);
        int requestTimeoutMs = (int) props.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG);
        int maxBlockMs = (int) props.get(ProducerConfig.MAX_BLOCK_MS_CONFIG);

        // then — 앱 .get(SEND_TIMEOUT_SECONDS) 이전에 Producer 재시도가 확정 종료되어야 함
        assertThat((long) deliveryTimeoutMs)
                .as("delivery.timeout.ms(%d) < SEND_TIMEOUT_SECONDS*1000(%d) — 앱 타임아웃 이전 종료 보장",
                        deliveryTimeoutMs, sendTimeoutMs)
                .isLessThan(sendTimeoutMs);

        assertThat(requestTimeoutMs)
                .as("request.timeout.ms(%d) <= delivery.timeout.ms(%d) — Kafka 클라이언트 필수 제약",
                        requestTimeoutMs, deliveryTimeoutMs)
                .isLessThanOrEqualTo(deliveryTimeoutMs);

        assertThat(maxBlockMs)
                .as("max.block.ms(%d) < request.timeout.ms(%d) — 메타데이터 블로킹 조기 이탈",
                        maxBlockMs, requestTimeoutMs)
                .isLessThan(requestTimeoutMs);
    }
}
