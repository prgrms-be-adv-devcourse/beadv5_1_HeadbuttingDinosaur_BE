package com.devticket.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.payment.payment.domain.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PaymentStatus 상태 전이 규칙")
class PaymentStatusTest {

    @Nested
    @DisplayName("READY 상태에서")
    class ReadyState {

        @Test
        void SUCCESS로_전이_가능() {
            assertThat(PaymentStatus.READY.canTransitionTo(PaymentStatus.SUCCESS)).isTrue();
        }

        @Test
        void FAILED로_전이_가능() {
            assertThat(PaymentStatus.READY.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        }

        @Test
        void CANCELLED로_전이_가능() {
            assertThat(PaymentStatus.READY.canTransitionTo(PaymentStatus.CANCELLED)).isTrue();
        }

        @Test
        void REFUNDED로_전이_불가() {
            assertThat(PaymentStatus.READY.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
        }
    }

    @Nested
    @DisplayName("SUCCESS 상태에서")
    class SuccessState {

        @Test
        void REFUNDED로_전이_가능() {
            assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
        }

        @Test
        void CANCELLED로_전이_가능() {
            assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.CANCELLED)).isTrue();
        }

        @Test
        void READY로_전이_불가() {
            assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.READY)).isFalse();
        }

        @Test
        void FAILED로_전이_불가() {
            assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.FAILED)).isFalse();
        }
    }

    @Nested
    @DisplayName("종단 상태에서")
    class TerminalStates {

        @Test
        void FAILED는_어디로도_전이_불가() {
            for (PaymentStatus target : PaymentStatus.values()) {
                assertThat(PaymentStatus.FAILED.canTransitionTo(target)).isFalse();
            }
        }

        @Test
        void CANCELLED는_어디로도_전이_불가() {
            for (PaymentStatus target : PaymentStatus.values()) {
                assertThat(PaymentStatus.CANCELLED.canTransitionTo(target)).isFalse();
            }
        }

        @Test
        void REFUNDED는_어디로도_전이_불가() {
            for (PaymentStatus target : PaymentStatus.values()) {
                assertThat(PaymentStatus.REFUNDED.canTransitionTo(target)).isFalse();
            }
        }
    }
}
