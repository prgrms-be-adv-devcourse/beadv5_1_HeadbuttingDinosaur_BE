package com.devticket.payment.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Outbox 스케줄러 E2E — EmbeddedKafka.
 *
 * 검증:
 * 1. Outbox row PENDING → 스케줄러 → Kafka 발행 + DB SENT 전이
 * 2. X-Message-Id 헤더 세팅 + partitionKey → Kafka record key 보존
 *
 * 선례: Commerce PR #482 `OutboxSchedulerIntegrationTest`
 * (프로젝트 일관성을 위해 PaymentKafkaIntegrationTest 동일한 @KafkaListener 패턴 채택)
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"payment.completed"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DisplayName("Outbox Scheduler 통합 테스트 (EmbeddedKafka)")
class OutboxSchedulerIntegrationTest {

    // ShedLock JdbcTemplateLockProvider 는 H2(테스트 프로파일)에서 usingDbTime 등
    // 호환성 이슈가 있고, @MockitoBean Mock stub 타이밍이 AOP 첫 tick 전에
    // 반영되지 않아 "It's locked" 로 건너뛰는 케이스가 발생함.
    // ShedLock 6.3.1의 BeanNameSelectingLockProviderSupplier 는 Bean 이름 기반 선택이므로
    // 이름을 `lockProvider` 로 맞춰 main 빈을 오버라이드한다.
    @TestConfiguration
    static class NoOpLockProviderConfig {
        @Bean(name = "lockProvider")
        @Primary
        LockProvider lockProvider() {
            return lockConfiguration -> Optional.of(() -> {});
        }
    }

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final List<ConsumerRecord<String, String>> received =
            Collections.synchronizedList(new ArrayList<>());

    @KafkaListener(topics = "payment.completed", groupId = "outbox-scheduler-it")
    void listen(ConsumerRecord<String, String> record) {
        received.add(record);
    }

    @BeforeEach
    void setUp() {
        received.clear();
    }

    @Test
    void publishPendingEvents_대기중인_레코드를_발행하고_SENT로_변경한다() throws Exception {
        // given
        Outbox saved = outboxRepository.save(
                Outbox.create(
                        "aggregate-1",
                        "partition-1",
                        "PaymentCompleted",
                        "payment.completed",
                        "{\"orderId\":1}"
                )
        );
        String expectedMessageId = saved.getMessageId();

        // when & then: Kafka 레코드 도착 검증
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(received).isNotEmpty());

        ConsumerRecord<String, String> record = received.get(0);

        assertThat(record.key()).isEqualTo("partition-1");
        assertThat(record.headers().lastHeader("X-Message-Id")).isNotNull();
        assertThat(new String(record.headers().lastHeader("X-Message-Id").value()))
                .isEqualTo(expectedMessageId);

        // OutboxEventMessage JSON 래핑 구조 검증 — payload 필드 내 실제 도메인 이벤트 보존
        JsonNode envelope = objectMapper.readTree(record.value());
        assertThat(envelope.get("eventType").asText()).isEqualTo("PaymentCompleted");
        assertThat(envelope.get("topic").asText()).isEqualTo("payment.completed");
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("partition-1");
        JsonNode payloadNode = objectMapper.readTree(envelope.get("payload").asText());
        assertThat(payloadNode.get("orderId").asInt()).isEqualTo(1);

        // Outbox 상태 갱신은 발행 직후 별도 트랜잭션으로 커밋되므로 독립 대기
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Outbox outbox = outboxRepository.findById(saved.getId()).orElseThrow();
                    assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
                    assertThat(outbox.getSentAt()).isNotNull();
                });
    }
}
