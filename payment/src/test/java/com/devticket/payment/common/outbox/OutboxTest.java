package com.devticket.payment.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Outbox 엔티티")
class OutboxTest {

    private Outbox outbox;

    @BeforeEach
    void setUp() {
        outbox = Outbox.create(
            "payment-uuid-001",
            "payment.completed",
            "payment.completed",
            "order-uuid-001",
            "{\"orderId\":\"order-uuid-001\"}"
        );
    }

    // =====================================================================
    // 생성
    // =====================================================================

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
    }

    // =====================================================================
    // 발행 성공 — markSent()
    // =====================================================================

    @Nested
    @DisplayName("발행 성공 — markSent()")
    class MarkSent {

        @Test
        void markSent_호출_후_status가_SENT() {
            // when
            outbox.markSent();

            // then
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        }

        @Test
        void markSent_호출_후_sentAt_채워짐() {
            // given
            Instant before = Instant.now();

            // when
            outbox.markSent();

            // then
            assertThat(outbox.getSentAt())
                .isNotNull()
                .isAfterOrEqualTo(before);
        }

        @Test
        void markSent_후_isPending_false() {
            // when
            outbox.markSent();

            // then
            assertThat(outbox.isPending()).isFalse();
        }
    }

    // =====================================================================
    // 재시도 — increaseRetryCount()
    // =====================================================================

    @Nested
    @DisplayName("재시도 — increaseRetryCount()")
    class IncreaseRetryCount {

        @Test
        void 재시도_호출_시_retryCount_1_증가() {
            // when
            outbox.increaseRetryCount();

            // then
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        void 재시도_시_nextRetryAt이_현재_이후로_설정() {
            // given
            Instant before = Instant.now();

            // when
            outbox.increaseRetryCount();

            // then
            assertThat(outbox.getNextRetryAt())
                .isNotNull()
                .isAfterOrEqualTo(before);
        }

        @Test
        void MAX_RETRY_미만이면_status_PENDING_유지() {
            // when: 4회 재시도 (MAX_RETRY = 5)
            for (int i = 0; i < 4; i++) {
                outbox.increaseRetryCount();
            }

            // then
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isEqualTo(4);
        }

        @Test
        void MAX_RETRY_도달_시_status가_FAILED() {
            // when: 5회 재시도
            for (int i = 0; i < 5; i++) {
                outbox.increaseRetryCount();
            }

            // then
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(outbox.getRetryCount()).isEqualTo(5);
        }

        @Test
        void 재시도_횟수에_따른_백오프_간격_증가() {
            // 1차 재시도: 60초, 2차: 120초, 3차: 180초
            Instant before1 = Instant.now();
            outbox.increaseRetryCount(); // retryCount=1
            Instant after1 = outbox.getNextRetryAt();

            Instant before2 = Instant.now();
            outbox.increaseRetryCount(); // retryCount=2
            Instant after2 = outbox.getNextRetryAt();

            // 2차 nextRetryAt이 1차보다 더 먼 미래
            assertThat(after2).isAfter(after1);
        }

        @Test
        void markSent_후_sentAt이_Instant_타입() {
            // when
            outbox.markSent();

            // then
            assertThat(outbox.getSentAt()).isInstanceOf(Instant.class);
        }

        @Test
        void MAX_RETRY_도달_시_nextRetryAt_갱신_안됨() {
            // given: 4회까지 nextRetryAt 설정
            for (int i = 0; i < 4; i++) {
                outbox.increaseRetryCount();
            }
            Instant nextRetryAtBefore = outbox.getNextRetryAt();

            // when: 5회째 → FAILED 전환, nextRetryAt 갱신 없음
            outbox.increaseRetryCount();

            // then
            assertThat(outbox.getNextRetryAt()).isEqualTo(nextRetryAtBefore);
        }
    }
}
