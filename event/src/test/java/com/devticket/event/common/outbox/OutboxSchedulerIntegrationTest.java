package com.devticket.event.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.devticket.event.infrastructure.search.EventSearchRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"stock.deducted"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class OutboxSchedulerIntegrationTest {

    // ShedLock JdbcTemplateLockProvider 는 H2(테스트 프로파일)에서 usingDbTime 등
    // 호환성 이슈가 있고, Mock stub 타이밍이 AOP 첫 tick 전에 반영되지 않아
    // "It's locked" 로 건너뛰는 케이스가 발생함.
    // 테스트에서는 @Primary NoOp Provider 로 대체해 매 tick 락 획득 성공을 보장.
    @TestConfiguration
    static class NoOpLockProviderConfig {
        @Bean
        @Primary
        LockProvider testLockProvider() {
            return lockConfiguration -> Optional.of(() -> {});
        }
    }

    // ES 미기동 CI 환경에서 SimpleElasticsearchRepository 초기화 시점의 연결 시도를 차단.
    // Outbox 스코프는 ES 와 무관하므로 Mock 대체로 충분 (CI run 24766734663 재현 차단).
    @MockitoBean
    private EventSearchRepository eventSearchRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        // Spring Kafka 4.x 신 시그니처 (broker, group, autoCommit:boolean) — 구 시그니처 (group, autoCommit:String, broker) 는 deprecated
        Map<String, Object> consumerProperties =
                KafkaTestUtils.consumerProps(embeddedKafkaBroker, "outbox-scheduler-test", true);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "stock.deducted");
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void publishPendingEvents_대기중인_레코드를_발행하고_SENT로_변경한다() {
        // given
        Outbox saved = outboxRepository.save(
                Outbox.create(
                        "aggregate-1",
                        "partition-1",
                        "StockDeducted",
                        "stock.deducted",
                        "{\"eventId\":1}"
                )
        );

        // when & then
        // Kafka 레코드 도착 검증 — poll 후 offset 이동하므로 1회만
        ConsumerRecord<String, String> record = await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .until(
                        () -> {
                            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(
                                    consumer, Duration.ofSeconds(1));
                            return records.count() > 0 ? records.iterator().next() : null;
                        },
                        r -> r != null
                );

        assertThat(record.key()).isEqualTo("partition-1");
        assertThat(record.value()).isEqualTo("{\"eventId\":1}");
        assertThat(record.headers().lastHeader("X-Message-Id")).isNotNull();

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
