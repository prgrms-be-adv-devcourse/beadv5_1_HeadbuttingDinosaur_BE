package com.devticket.payment.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.payment.wallet.domain.enums.WalletChargeStatus;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WalletCharge 상태 전이")
class WalletChargeStateTest {

    private WalletCharge charge;

    @BeforeEach
    void setUp() {
        charge = WalletCharge.create(1L, UUID.randomUUID(), 10_000, "idem-key-001");
    }

    @Nested
    @DisplayName("PENDING → PROCESSING 선점")
    class MarkProcessing {

        @Test
        void PENDING에서_PROCESSING으로_전이_성공() {
            // when
            charge.markProcessing();

            // then
            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.PROCESSING);
            assertThat(charge.isProcessing()).isTrue();
            assertThat(charge.isPending()).isFalse();
        }

        @Test
        void COMPLETED에서_PROCESSING_전이_불가() {
            // given
            charge.complete("pk-123");

            // when & then
            assertThatThrownBy(() -> charge.markProcessing())
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void FAILED에서_PROCESSING_전이_불가() {
            // given
            charge.fail();

            // when & then
            assertThatThrownBy(() -> charge.markProcessing())
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("PROCESSING → PENDING 원복")
    class RevertToPending {

        @Test
        void PROCESSING에서_PENDING으로_원복_성공() {
            // given
            charge.markProcessing();

            // when
            charge.revertToPending();

            // then
            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.PENDING);
            assertThat(charge.isPending()).isTrue();
        }

        @Test
        void PENDING에서_원복_시도_불가() {
            // when & then
            assertThatThrownBy(() -> charge.revertToPending())
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void COMPLETED에서_원복_시도_불가() {
            // given
            charge.complete("pk-123");

            // when & then
            assertThatThrownBy(() -> charge.revertToPending())
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("PROCESSING → 최종 상태")
    class ProcessingToFinal {

        @Test
        void PROCESSING에서_COMPLETED_전이_성공() {
            // given
            charge.markProcessing();

            // when
            charge.complete("pk-456");

            // then
            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
            assertThat(charge.getPaymentKey()).isEqualTo("pk-456");
        }

        @Test
        void PROCESSING에서_FAILED_전이_성공() {
            // given
            charge.markProcessing();

            // when
            charge.fail();

            // then
            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("전체 흐름 시나리오")
    class FullFlow {

        @Test
        void 정상_흐름_PENDING_PROCESSING_COMPLETED() {
            assertThat(charge.isPending()).isTrue();

            charge.markProcessing();
            assertThat(charge.isProcessing()).isTrue();

            charge.complete("pk-789");
            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
        }

        @Test
        void 실패_후_재시도_PENDING_PROCESSING_revert_PENDING_PROCESSING_COMPLETED() {
            // 1차 시도: PG 조회 실패 → 원복
            charge.markProcessing();
            charge.revertToPending();
            assertThat(charge.isPending()).isTrue();

            // 2차 시도: 성공
            charge.markProcessing();
            charge.complete("pk-retry-ok");
            assertThat(charge.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
        }
    }
}
