package com.devticket.commerce.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxRepositoryTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findPendingToPublish_조건에_맞는_레코드만_createdAt_오름차순으로_조회한다() {
        // given
        Instant now = Instant.now();
        // graceCutoff 영향 배제 — 테스트 의도(상태/재시도 시각 필터)에 집중
        Instant graceCutoff = now.plusSeconds(60);

        Outbox immediateTarget = saveWithCreatedAt("aggregate-1", now.minusSeconds(5), null, OutboxStatus.PENDING);
        Outbox retryTarget = saveWithCreatedAt("aggregate-2", now.minusSeconds(4), now.minusSeconds(1), OutboxStatus.PENDING);
        Outbox futureRetry = saveWithCreatedAt("aggregate-3", now.minusSeconds(3), now.plusSeconds(30), OutboxStatus.PENDING);
        Outbox sentOutbox = saveWithCreatedAt("aggregate-4", now.minusSeconds(2), null, OutboxStatus.SENT);

        // when
        List<Outbox> found = outboxRepository
                .findPendingToPublish(OutboxStatus.PENDING, now, graceCutoff);

        // then
        assertThat(found).hasSize(2);
        assertThat(found)
                .extracting(Outbox::getAggregateId)
                .containsExactly("aggregate-1", "aggregate-2");
    }

    @Test
    void findPendingToPublish_graceCutoff_이후에_생성된_row_는_제외한다() {
        // given — 직접발행 경로에 우선권을 주기 위한 grace period 동작 검증
        Instant now = Instant.now();
        Instant graceCutoff = now.minusSeconds(5);

        saveWithCreatedAt("fresh", now.minusSeconds(2), null, OutboxStatus.PENDING);
        saveWithCreatedAt("old", now.minusSeconds(10), null, OutboxStatus.PENDING);

        // when
        List<Outbox> found = outboxRepository
                .findPendingToPublish(OutboxStatus.PENDING, now, graceCutoff);

        // then
        assertThat(found)
                .extracting(Outbox::getAggregateId)
                .containsExactly("old");
    }

    private Outbox saveWithCreatedAt(String aggregateId, Instant createdAt, Instant nextRetryAt, OutboxStatus status) {
        Outbox outbox = Outbox.create(aggregateId, aggregateId, "OrderCreated", "order.created", "{\"id\":1}");
        ReflectionTestUtils.setField(outbox, "nextRetryAt", nextRetryAt);
        ReflectionTestUtils.setField(outbox, "status", status);
        Outbox saved = outboxRepository.saveAndFlush(outbox);
        // @CreatedDate 가 prePersist 단계에서 createdAt 을 덮어쓰므로 native UPDATE 로 강제 반영
        // (createdAt 컬럼은 updatable=false 라 JPA 쓰기 경로로는 변경 불가)
        entityManager.createNativeQuery(
                        "UPDATE commerce.outbox SET created_at = ? WHERE id = ?")
                .setParameter(1, Timestamp.from(createdAt))
                .setParameter(2, saved.getId())
                .executeUpdate();
        entityManager.clear();
        return saved;
    }
}
