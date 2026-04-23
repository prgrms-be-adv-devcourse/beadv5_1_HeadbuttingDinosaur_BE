package com.devticket.payment.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * OutboxRepository.findPendingToPublish — 쿼리 경계 조건 회귀 방지.
 *
 * outbox_fix.md §2 결정값:
 *  - 연산자: `o.nextRetryAt < :now` (경계 배제 — `== now`는 다음 틱에서 픽업)
 *  - LIMIT: 50 (배치 상한)
 *
 * @SpringBootTest + @Transactional — 각 테스트 메서드 종료 시 자동 롤백.
 * @DataJpaTest 는 본 프로젝트 Spring Boot 4.0 starter 구성상 사용 불가.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("OutboxRepository — 쿼리 경계 조건")
class OutboxRepositoryTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EntityManager em;

    /**
     * Outbox 엔티티는 `nextRetryAt` 세터가 없으므로 native UPDATE 로 조정.
     */
    private Outbox saveWithNextRetryAt(Instant nextRetryAt) {
        Outbox outbox = Outbox.create(
            "agg-" + System.nanoTime(),
            "pk-1",
            "payment.completed",
            "payment.completed",
            "{}"
        );
        outboxRepository.saveAndFlush(outbox);
        if (nextRetryAt != null) {
            em.createQuery("UPDATE Outbox o SET o.nextRetryAt = :t WHERE o.id = :id")
                .setParameter("t", nextRetryAt)
                .setParameter("id", outbox.getId())
                .executeUpdate();
            em.flush();
            em.clear();
        }
        return outbox;
    }

    @Nested
    @DisplayName("< :now 경계 조건")
    class BoundaryCondition {

        @Test
        void nextRetryAt_NULL은_항상_픽업된다() {
            Outbox saved = saveWithNextRetryAt(null);

            List<Outbox> result = outboxRepository.findPendingToPublish(
                OutboxStatus.PENDING, Instant.now());

            assertThat(result).extracting(Outbox::getId).contains(saved.getId());
        }

        @Test
        void nextRetryAt이_현재보다_과거이면_픽업된다() {
            Instant now = Instant.now();
            Outbox saved = saveWithNextRetryAt(now.minusSeconds(1));

            List<Outbox> result = outboxRepository.findPendingToPublish(
                OutboxStatus.PENDING, now);

            assertThat(result).extracting(Outbox::getId).contains(saved.getId());
        }

        @Test
        void nextRetryAt이_현재와_동일하면_스킵된다() {
            // `<` 연산자라서 경계값은 배제 — 다음 틱에서 픽업됨
            Instant now = Instant.now();
            Outbox saved = saveWithNextRetryAt(now);

            List<Outbox> result = outboxRepository.findPendingToPublish(
                OutboxStatus.PENDING, now);

            assertThat(result).extracting(Outbox::getId).doesNotContain(saved.getId());
        }

        @Test
        void nextRetryAt이_현재보다_미래이면_스킵된다() {
            Instant now = Instant.now();
            Outbox saved = saveWithNextRetryAt(now.plusSeconds(10));

            List<Outbox> result = outboxRepository.findPendingToPublish(
                OutboxStatus.PENDING, now);

            assertThat(result).extracting(Outbox::getId).doesNotContain(saved.getId());
        }

        @Test
        void SENT_상태는_픽업되지_않는다() {
            Outbox outbox = Outbox.create("agg-sent-" + System.nanoTime(), "pk", "t", "t", "{}");
            outbox.markSent();
            outboxRepository.saveAndFlush(outbox);

            List<Outbox> result = outboxRepository.findPendingToPublish(
                OutboxStatus.PENDING, Instant.now());

            assertThat(result).extracting(Outbox::getId).doesNotContain(outbox.getId());
        }
    }

    @Nested
    @DisplayName("LIMIT 50 배치 상한")
    class BatchLimit {

        @Test
        @DisplayName("51건 저장 시 50건만 반환된다")
        void returns_at_most_50() {
            for (int i = 0; i < 51; i++) {
                outboxRepository.save(Outbox.create(
                    "agg-limit-" + System.nanoTime() + "-" + i,
                    "pk-" + i,
                    "payment.completed",
                    "payment.completed",
                    "{\"i\":" + i + "}"
                ));
            }
            em.flush();

            List<Outbox> result = outboxRepository.findPendingToPublish(
                OutboxStatus.PENDING, Instant.now());

            assertThat(result).hasSize(50);
        }
    }
}
