package com.devticket.commerce.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxTest {

    @Test
    void markFailed_호출시_지수_백오프로_nextRetryAt를_설정한다() {
        // given
        Outbox outbox = Outbox.create("aggregate-1", "partition-1", "OrderCreated", "order.created", "{\"id\":1}");
        Instant beforeFirstRetry = Instant.now();

        // when
        outbox.markFailed();
        Instant firstRetryAt = outbox.getNextRetryAt();
        outbox.markFailed();
        Instant secondRetryAt = outbox.getNextRetryAt();
        outbox.markFailed();
        Instant thirdRetryAt = outbox.getNextRetryAt();
        outbox.markFailed();
        Instant fourthRetryAt = outbox.getNextRetryAt();
        outbox.markFailed();
        Instant fifthRetryAt = outbox.getNextRetryAt();

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(5);
        assertThat(firstRetryAt).isBetween(beforeFirstRetry.plusSeconds(1), beforeFirstRetry.plusSeconds(2));
        assertThat(secondRetryAt).isBetween(firstRetryAt.plusSeconds(1), firstRetryAt.plusSeconds(3));
        assertThat(thirdRetryAt).isBetween(secondRetryAt.plusSeconds(2), secondRetryAt.plusSeconds(5));
        assertThat(fourthRetryAt).isBetween(thirdRetryAt.plusSeconds(3), thirdRetryAt.plusSeconds(9));
        assertThat(fifthRetryAt).isBetween(fourthRetryAt.plusSeconds(7), fourthRetryAt.plusSeconds(17));
    }

    @Test
    void markFailed_여섯번째_호출시_FAILED_상태가_된다() {
        // given
        Outbox outbox = Outbox.create("aggregate-1", "partition-1", "OrderCreated", "order.created", "{\"id\":1}");

        // when
        for (int i = 0; i < 6; i++) {
            outbox.markFailed();
        }

        // then
        assertThat(outbox.getRetryCount()).isEqualTo(6);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(outbox.getNextRetryAt()).isNotNull();
    }

    @Test
    void markSent_호출시_SENT와_sentAt을_설정한다() {
        // given
        Outbox outbox = Outbox.create("aggregate-1", "partition-1", "OrderCreated", "order.created", "{\"id\":1}");

        // when
        outbox.markSent();

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
    }
}
