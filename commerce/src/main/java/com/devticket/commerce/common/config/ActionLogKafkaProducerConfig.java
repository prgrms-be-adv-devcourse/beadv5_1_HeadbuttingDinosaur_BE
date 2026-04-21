package com.devticket.commerce.common.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * action.log 토픽 전용 Producer 설정 — fire-and-forget 정책 (at-most-once).
 * 기존 Saga용 {@link KafkaProducerConfig}와 설정이 완전히 다르므로 Bean 격리 필수.
 * 상세 정책: BE_log/docs/actionLog.md §4 ②, kafka-design.md §6
 */
@Configuration
public class ActionLogKafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> actionLogProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // fire-and-forget — 브로커 응답 대기 없음, 손실 허용
        props.put(ProducerConfig.ACKS_CONFIG, "0");
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        // 비동기 발행 전제 — UX 무영향 + batch 효율 ↑
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        // 1차 기능 검증 우선 — lz4 전환은 성능 테스트 후 별도 Task로 분리
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("actionLogKafkaTemplate")
    public KafkaTemplate<String, String> actionLogKafkaTemplate(
            @Qualifier("actionLogProducerFactory") ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }
}
