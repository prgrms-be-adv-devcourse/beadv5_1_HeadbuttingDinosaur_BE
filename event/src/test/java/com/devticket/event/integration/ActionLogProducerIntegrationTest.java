package com.devticket.event.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.event.application.event.ActionLogDomainEvent;
import com.devticket.event.application.event.ActionLogKafkaPublisher;
import com.devticket.event.common.config.ActionLogKafkaProducerConfig;
import com.devticket.event.common.config.AsyncConfig;
import com.devticket.event.common.config.JacksonConfig;
import com.devticket.event.common.messaging.event.ActionLogEvent;
import com.devticket.event.common.messaging.event.ActionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

@EmbeddedKafka(partitions = 1, topics = "action.log")
@SpringBootTest(
    classes = {
        JacksonConfig.class,
        ActionLogKafkaProducerConfig.class,
        AsyncConfig.class,
        ActionLogKafkaPublisher.class
    },
    properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.main.web-application-type=none",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration"
    }
)
class ActionLogProducerIntegrationTest {

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void VIEW_발행시_Kafka_메시지_도달_및_필드_매핑_검증() throws Exception {
        UUID userId = UUID.randomUUID();
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            eventPublisher.publishEvent(new ActionLogDomainEvent(
                userId, null, ActionType.VIEW,
                "spring", "1,2", null, null, null, Instant.now()
            ));

            ConsumerRecord<String, String> record = pollByKey(consumer, userId.toString());
            assertThat(record.key()).isEqualTo(userId.toString());

            ActionLogEvent event = objectMapper.readValue(record.value(), ActionLogEvent.class);
            assertThat(event.userId()).isEqualTo(userId.toString());
            assertThat(event.eventId()).isNull();
            assertThat(event.actionType()).isEqualTo("VIEW");
            assertThat(event.searchKeyword()).isEqualTo("spring");
            assertThat(event.stackFilter()).isEqualTo("1,2");
        }
    }

    @Test
    void DETAIL_VIEW_발행시_eventId_포함() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            eventPublisher.publishEvent(new ActionLogDomainEvent(
                userId, eventId, ActionType.DETAIL_VIEW,
                null, null, null, null, null, Instant.now()
            ));

            ConsumerRecord<String, String> record = pollByKey(consumer, userId.toString());
            ActionLogEvent event = objectMapper.readValue(record.value(), ActionLogEvent.class);
            assertThat(event.eventId()).isEqualTo(eventId.toString());
            assertThat(event.actionType()).isEqualTo("DETAIL_VIEW");
        }
    }

    @Test
    void DWELL_TIME_발행시_dwellTimeSeconds_포함() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            eventPublisher.publishEvent(new ActionLogDomainEvent(
                userId, eventId, ActionType.DWELL_TIME,
                null, null, 30, null, null, Instant.now()
            ));

            ConsumerRecord<String, String> record = pollByKey(consumer, userId.toString());
            ActionLogEvent event = objectMapper.readValue(record.value(), ActionLogEvent.class);
            assertThat(event.dwellTimeSeconds()).isEqualTo(30);
            assertThat(event.actionType()).isEqualTo("DWELL_TIME");
        }
    }

    private KafkaConsumer<String, String> newConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("action.log"));
        return consumer;
    }

    private ConsumerRecord<String, String> pollByKey(KafkaConsumer<String, String> consumer, String expectedKey) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (expectedKey.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("Kafka 메시지 10초 내 미수신 (expected key=" + expectedKey + ")");
    }
}
