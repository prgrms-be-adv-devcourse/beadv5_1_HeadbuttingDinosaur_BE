package com.devticket.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.refund.domain.enums.OrderRefundStatus;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.OrderRefund;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderRefundTest {

    private OrderRefund newLedger(int totalAmount, int totalTickets) {
        return OrderRefund.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            PaymentMethod.PG,
            totalAmount,
            totalTickets
        );
    }

    @Nested
    @DisplayName("applyRefund")
    class ApplyRefund {

        @Test
        @DisplayName("초기 상태는 NONE 이며 첫 부분 환불 후 PARTIAL 로 전이")
        void initial_partial_transition() {
            OrderRefund ledger = newLedger(30_000, 3);
            assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.NONE);

            ledger.applyRefund(10_000, 1);

            assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.PARTIAL);
            assertThat(ledger.getRefundedAmount()).isEqualTo(10_000);
            assertThat(ledger.getRefundedTickets()).isEqualTo(1);
            assertThat(ledger.getRemainingAmount()).isEqualTo(20_000);
            assertThat(ledger.getRemainingTickets()).isEqualTo(2);
        }

        @Test
        @DisplayName("누적 티켓이 totalTickets 와 같아지면 FULL 로 전이")
        void cumulative_full_transition() {
            OrderRefund ledger = newLedger(30_000, 3);
            ledger.applyRefund(10_000, 1);
            ledger.applyRefund(20_000, 2);

            assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.FULL);
            assertThat(ledger.isFullyRefunded()).isTrue();
            assertThat(ledger.getRemainingAmount()).isZero();
            assertThat(ledger.getRemainingTickets()).isZero();
        }

        @Test
        @DisplayName("이미 FULL 인 원장에 추가 환불 시도 시 예외")
        void reject_after_full() {
            OrderRefund ledger = newLedger(10_000, 1);
            ledger.applyRefund(10_000, 1);

            assertThatThrownBy(() -> ledger.applyRefund(5_000, 1))
                .isInstanceOf(RefundException.class);
        }

        @Test
        @DisplayName("음수 금액 또는 티켓 수 입력 시 예외")
        void reject_negative() {
            OrderRefund ledger = newLedger(10_000, 1);
            assertThatThrownBy(() -> ledger.applyRefund(-1, 1))
                .isInstanceOf(RefundException.class);
            assertThatThrownBy(() -> ledger.applyRefund(1, -1))
                .isInstanceOf(RefundException.class);
        }

        @Test
        @DisplayName("totalAmount 초과 누적 시 예외")
        void reject_overflow() {
            OrderRefund ledger = newLedger(10_000, 2);
            ledger.applyRefund(5_000, 1);
            assertThatThrownBy(() -> ledger.applyRefund(9_000, 1))
                .isInstanceOf(RefundException.class);
        }
    }

    @Test
    @DisplayName("markFailed 시 상태는 FAILED")
    void mark_failed() {
        OrderRefund ledger = newLedger(10_000, 1);
        ledger.markFailed();
        assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.FAILED);
    }
}
