package com.devticket.payment.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Outbox 엔티티")
class OutboxTest {

    private Outbox outbox;

    @BeforeEach
    void setUp() {
        outbox = Outbox.create(
            "payment-uuid-001",
            "order-uuid-001",
            "payment.completed",
            "payment.completed",
            "{\"orderId\":\"order-uuid-001\"}"
        );
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        void 생성_후_초기_status는_PENDING() {
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        }

        @Test
        void 생성_후_retryCount는_0() {
            assertThat(outbox.getRetryCount()).isZero();
        }

        @Test
        void 생성_후_sentAt과_nextRetryAt은_null() {
            assertThat(outbox.getSentAt()).isNull();
            assertThat(outbox.getNextRetryAt()).isNull();
        }

        @Test
        void 생성_후_isPending_true() {
            assertThat(outbox.isPending()).isTrue();
        }

        @Test
        void 생성_시_전달한_topic과_partitionKey_저장() {
            assertThat(outbox.getTopic()).isEqualTo("payment.completed");
            assertThat(outbox.getPartitionKey()).isEqualTo("order-uuid-001");
        }

        @Test
        void messageId는_36자리_UUID_문자열() {
            assertThat(outbox.getMessageId())
                .isNotNull()
                .hasSize(36)
                .matches("^[0-9a-fA-F-]{36}$");
        }
    }

    @Nested
    @DisplayName("발행 성공 — markSent()")
    class MarkSent {

        @Test
        void markSent_호출_후_status가_SENT() {
            outbox.markSent();
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        }

        @Test
        void markSent_호출_후_sentAt_채워짐() {
            Instant before = Instant.now();
            outbox.markSent();
            assertThat(outbox.getSentAt())
                .isNotNull()
                .isAfterOrEqualTo(before);
        }

        @Test
        void markSent_후_isPending_false() {
            outbox.markSent();
            assertThat(outbox.isPending()).isFalse();
        }
    }

    @Nested
    @DisplayName("재시도 — markFailed() 지수 백오프")
    class MarkFailed {

        @Test
        void 재시도_호출_시_retryCount_1_증가() {
            outbox.markFailed();
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        void 재시도_시_nextRetryAt이_현재_이후로_설정() {
            Instant before = Instant.now();
            outbox.markFailed();
            assertThat(outbox.getNextRetryAt())
                .isNotNull()
                .isAfterOrEqualTo(before);
        }

        @Test
        void MAX_RETRY_미만이면_status_PENDING_유지() {
            // 5회 재시도 (MAX_RETRY = 6)
            for (int i = 0; i < 5; i++) {
                outbox.markFailed();
            }
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isEqualTo(5);
        }

        @Test
        void MAX_RETRY_도달_시_status가_FAILED() {
            // 6회 재시도
            for (int i = 0; i < 6; i++) {
                outbox.markFailed();
            }
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(outbox.getRetryCount()).isEqualTo(6);
        }

        /**
         * 회차별 지수 백오프 정확값 — 2^(retryCount-1) 초.
         * 1→1s / 2→2s / 3→4s / 4→8s / 5→16s (6회차는 FAILED 전환으로 nextRetryAt 미세팅)
         */
        @ParameterizedTest(name = "retryCount={0} → {1}초")
        @CsvSource({
            "1, 1",
            "2, 2",
            "3, 4",
            "4, 8",
            "5, 16"
        })
        void 회차별_지수_백오프_정확값(int attempts, long expectedSeconds) {
            Instant before = Instant.now();
            for (int i = 0; i < attempts; i++) {
                outbox.markFailed();
            }

            assertThat(outbox.getRetryCount()).isEqualTo(attempts);
            // 허용 오차 ±1초 (markFailed() 내부 Instant.now() 호출 타이밍)
            long actualDelta = outbox.getNextRetryAt().getEpochSecond() - before.getEpochSecond();
            assertThat(actualDelta)
                .as("retryCount=%d 에서 expectedSeconds=%d 근사 (±1초 허용)", attempts, expectedSeconds)
                .isBetween(expectedSeconds - 1, expectedSeconds + 1);
        }

        @Test
        void MAX_RETRY_도달_시_nextRetryAt_갱신_안됨() {
            // 5회까지 nextRetryAt 설정
            for (int i = 0; i < 5; i++) {
                outbox.markFailed();
            }
            Instant nextRetryAtBefore = outbox.getNextRetryAt();

            // 6회째 → FAILED 전환, nextRetryAt 갱신 없음
            outbox.markFailed();

            assertThat(outbox.getNextRetryAt()).isEqualTo(nextRetryAtBefore);
        }
    }
}
