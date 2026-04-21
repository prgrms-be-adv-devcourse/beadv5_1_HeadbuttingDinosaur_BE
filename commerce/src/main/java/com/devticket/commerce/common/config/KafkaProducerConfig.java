package com.devticket.commerce.common.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    @Primary
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 모든 ISR 응답 대기 — 메시지 유실 방지
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        // Producer 멱등성 — 재전송 시 중복 발행 방지 (acks=all 필수)
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // OutboxEventProducer.get(2s)와 정합 — 앱 타임아웃 이전에 Producer 재시도 확정 종료
        // 일시 실패는 Outbox markFailed() 지수 백오프가 단일 재시도 메커니즘으로 수행
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1500);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
