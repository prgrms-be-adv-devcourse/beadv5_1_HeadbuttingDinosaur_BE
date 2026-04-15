package com.devticket.commerce.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
class OutboxRepositoryTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void findTop50ByStatusAndNextRetryAt조건에_맞는_레코드만_createdAt_오름차순으로_조회한다() {
        // given
        Instant now = Instant.now();

        Outbox immediateTarget = createOutbox("aggregate-1", now.minusSeconds(5), null, OutboxStatus.PENDING);
        Outbox retryTarget = createOutbox("aggregate-2", now.minusSeconds(4), now.minusSeconds(1), OutboxStatus.PENDING);
        Outbox futureRetry = createOutbox("aggregate-3", now.minusSeconds(3), now.plusSeconds(30), OutboxStatus.PENDING);
        Outbox sentOutbox = createOutbox("aggregate-4", now.minusSeconds(2), null, OutboxStatus.SENT);

        outboxRepository.saveAll(List.of(immediateTarget, retryTarget, futureRetry, sentOutbox));

        // when
        List<Outbox> found = outboxRepository
                .findTop50ByStatusAndNextRetryAtIsNullOrStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        OutboxStatus.PENDING,
                        now
                );

        // then
        assertThat(found).hasSize(2);
        assertThat(found)
                .extracting(Outbox::getAggregateId)
                .containsExactly("aggregate-1", "aggregate-2");
    }

    private Outbox createOutbox(String aggregateId, Instant createdAt, Instant nextRetryAt, OutboxStatus status) {
        Outbox outbox = Outbox.create(aggregateId, aggregateId, "OrderCreated", "order.created", "{\"id\":1}");
        ReflectionTestUtils.setField(outbox, "createdAt", createdAt);
        ReflectionTestUtils.setField(outbox, "nextRetryAt", nextRetryAt);
        ReflectionTestUtils.setField(outbox, "status", status);
        return outbox;
    }
}
