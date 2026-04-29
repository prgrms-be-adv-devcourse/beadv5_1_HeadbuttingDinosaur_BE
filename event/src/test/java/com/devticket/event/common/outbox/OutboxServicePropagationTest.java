package com.devticket.event.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.event.common.config.JacksonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * OutboxService.save() 의 @Transactional(propagation = MANDATORY) 불변식 검증.
 *
 * audit-report 2026-04-28 P1 #4 (outbox_fix.md T2/5-H 이식 누락 해소).
 *
 * 호출자가 비즈니스 @Transactional 을 깜빡 누락하면 Outbox 행이 비즈니스 트랜잭션과
 * 분리되어 정합성이 깨진다. MANDATORY 가드는 그 분리를 컴파일러 대신 런타임에서
 * IllegalTransactionStateException 으로 즉시 차단하는 최후 방어선이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({OutboxService.class, JacksonConfig.class})
@DisplayName("OutboxService.save — @Transactional(MANDATORY) 불변식")
class OutboxServicePropagationTest {

    @Autowired private OutboxService outboxService;
    @Autowired private OutboxRepository outboxRepository;

    @MockitoBean private OutboxEventProducer outboxEventProducer;
    @MockitoBean private OutboxAfterCommitPublisher outboxAfterCommitPublisher;

    @Test
    @Transactional(propagation = Propagation.NEVER)
    @DisplayName("외부 트랜잭션 없이 호출 시 IllegalTransactionStateException 으로 차단")
    void save_outsideTransaction_throwsIllegalTransactionStateException() {
        assertThatThrownBy(() -> outboxService.save(
            "agg-1", "key-1", "EVT_TEST", "test.topic", new SamplePayload("v1", 1)
        ))
            .isInstanceOf(IllegalTransactionStateException.class)
            .hasMessageContaining("mandatory");
    }

    @Test
    @Transactional
    @DisplayName("외부 트랜잭션 존재 시 join 하여 Outbox 가 정상 저장된다")
    void save_insideTransaction_joinsAndPersists() {
        outboxRepository.deleteAll();

        outboxService.save("agg-2", "key-2", "EVT_TEST", "test.topic", new SamplePayload("v2", 42));

        assertThat(outboxRepository.findAll())
            .singleElement()
            .satisfies(saved -> {
                assertThat(saved.getAggregateId()).isEqualTo("agg-2");
                assertThat(saved.getPartitionKey()).isEqualTo("key-2");
                assertThat(saved.getEventType()).isEqualTo("EVT_TEST");
                assertThat(saved.getTopic()).isEqualTo("test.topic");
                assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
                assertThat(saved.getPayload())
                    .contains("\"name\":\"v2\"")
                    .contains("\"value\":42");
            });
    }

    private record SamplePayload(String name, int value) {}
}
