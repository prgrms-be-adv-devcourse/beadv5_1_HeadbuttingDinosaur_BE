package com.devticket.event.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Outbox 엔티티 단위 테스트 — B5-3 markFailed 지수 백오프 + markSent 상태 전이 실증.
 *
 * <p>지수 백오프 공식: {@code nextRetryAt = now + 2^(retryCount-1)s}<br>
 * 6회 실패 도달 시 {@link OutboxStatus#FAILED} 전환 + 재시도 중단.
 *
 * <p>{@link OutboxServiceTest} 는 Service 계층 협력(markSent/markFailed/save 호출)만 검증하며,
 * 본 테스트가 엔티티 내부 상태 전이의 유일한 실증 가드.
 */
class OutboxEntityTest {

    @Test
    void markSent_호출시_SENT로_전이되고_sentAt_세팅_retryCount는_불변이다() {
        Outbox outbox = outbox();

        outbox.markSent();

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
        assertThat(outbox.getRetryCount()).isZero();
    }

    @ParameterizedTest(name = "retryCount {0}→{1}회 실패시 nextRetryAt은 {2}초 후 (PENDING 유지)")
    @CsvSource({
            "0, 1, 1",   // 2^0 = 1
            "1, 2, 2",   // 2^1 = 2
            "2, 3, 4",   // 2^2 = 4
            "3, 4, 8",   // 2^3 = 8
            "4, 5, 16"   // 2^4 = 16
    })
    void markFailed_지수_백오프_2의_n승_초_후로_예약된다(int initialRetryCount,
                                              int expectedRetryCount,
                                              long expectedDelaySec) {
        Outbox outbox = outbox();
        ReflectionTestUtils.setField(outbox, "retryCount", initialRetryCount);
        Instant before = Instant.now();

        outbox.markFailed();

        assertThat(outbox.getRetryCount()).isEqualTo(expectedRetryCount);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        // 지수 백오프: Instant.now() 측정 오차 허용 창 (−200ms ~ +500ms)
        assertThat(outbox.getNextRetryAt())
                .isBetween(
                        before.plusSeconds(expectedDelaySec).minusMillis(200),
                        before.plusSeconds(expectedDelaySec).plusMillis(500)
                );
    }

    @Test
    void markFailed_여섯번째_실패시_FAILED로_전이되고_재시도_중단() {
        Outbox outbox = outbox();
        ReflectionTestUtils.setField(outbox, "retryCount", 5);

        outbox.markFailed();

        assertThat(outbox.getRetryCount()).isEqualTo(6);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
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
}
