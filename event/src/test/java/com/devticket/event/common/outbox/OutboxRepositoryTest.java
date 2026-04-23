package com.devticket.event.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Outbox Repository JPA 쿼리 회귀 방지.
 *
 * <p>핵심 가드: `nextRetryAt < :now` 경계 배제 (kafka-design.md §4).
 * {@code <=} 으로의 조용한 회귀는 운영에서 즉시 감지되지 않으므로 본 테스트가 유일한 차단선.
 */
@DataJpaTest
// application-test.yml 의 H2(MODE=PostgreSQL;INIT=CREATE SCHEMA event) 설정 유지.
// 기본 Replace.ANY 로 치환되면 event 스키마가 생성되지 않아 "Schema EVENT not found" 발생.
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class OutboxRepositoryTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void nextRetryAt_null이면_즉시_발행_대상으로_포함된다() {
        Outbox immediate = save(outbox(), null);

        List<Outbox> result = outboxRepository.findPendingToPublish(OutboxStatus.PENDING, Instant.now());

        assertThat(result).extracting(Outbox::getId).contains(immediate.getId());
    }

    @Test
    void nextRetryAt_과거_시각이면_발행_대상으로_포함된다() {
        Instant now = Instant.now();
        Outbox past = save(outbox(), now.minusSeconds(1));

        List<Outbox> result = outboxRepository.findPendingToPublish(OutboxStatus.PENDING, now);

        assertThat(result).extracting(Outbox::getId).contains(past.getId());
    }

    @Test
    @DisplayName("nextRetryAt == now 는 경계 배제 — 다음 틱에서 픽업 (< :now)")
    void nextRetryAt_현재_시각이면_경계_배제로_제외된다() {
        // H2/Hibernate 의 timestamp 저장 정밀도(microseconds) 에 맞춰
        // nextRetryAt 과 쿼리 파라미터 :now 가 정확히 동일 바이트가 되도록 절삭.
        // 절삭하지 않으면 저장 시 나노초가 절단되어 nextRetryAt < now 상태가 되어 경계 테스트가 깨짐.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Outbox boundary = save(outbox(), now);

        List<Outbox> result = outboxRepository.findPendingToPublish(OutboxStatus.PENDING, now);

        assertThat(result).extracting(Outbox::getId).doesNotContain(boundary.getId());
    }

    @Test
    void nextRetryAt_미래_시각이면_제외된다() {
        Instant now = Instant.now();
        Outbox future = save(outbox(), now.plusSeconds(1));

        List<Outbox> result = outboxRepository.findPendingToPublish(OutboxStatus.PENDING, now);

        assertThat(result).extracting(Outbox::getId).doesNotContain(future.getId());
    }

    @Test
    void SENT_FAILED_상태는_조회되지_않는다() {
        Outbox sent = save(outbox(), null);
        sent.markSent();
        outboxRepository.save(sent);

        Outbox failed = save(outbox(), null);
        ReflectionTestUtils.setField(failed, "status", OutboxStatus.FAILED);
        outboxRepository.save(failed);

        List<Outbox> result = outboxRepository.findPendingToPublish(OutboxStatus.PENDING, Instant.now());

        assertThat(result)
                .extracting(Outbox::getId)
                .doesNotContain(sent.getId(), failed.getId());
    }

    private Outbox outbox() {
        return Outbox.create(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "STOCK_DEDUCTED",
                "stock.deducted",
                "{\"k\":1}"
        );
    }

    private Outbox save(Outbox outbox, Instant nextRetryAt) {
        if (nextRetryAt != null) {
            ReflectionTestUtils.setField(outbox, "nextRetryAt", nextRetryAt);
        }
        return outboxRepository.save(outbox);
    }
}
