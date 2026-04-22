package com.devticket.payment.refund.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.refund.domain.enums.OrderRefundStatus;
import com.devticket.payment.refund.domain.exception.RefundException;
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
    @DisplayName("create")
    class CreateTest {

        @Test
        @DisplayName("정상 생성 — 초기 상태 NONE, refundedAmount/Tickets = 0")
        void 정상_생성() {
            OrderRefund ledger = newLedger(10_000, 3);

            assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.NONE);
            assertThat(ledger.getRefundedAmount()).isEqualTo(0);
            assertThat(ledger.getRefundedTickets()).isEqualTo(0);
            assertThat(ledger.getTotalAmount()).isEqualTo(10_000);
            assertThat(ledger.getTotalTickets()).isEqualTo(3);
            assertThat(ledger.getOrderRefundId()).isNotNull();
        }

        @Test
        @DisplayName("totalAmount <= 0 — 예외")
        void 잘못된_금액() {
            assertThatThrownBy(() -> newLedger(0, 1))
                .isInstanceOf(RefundException.class);
        }

        @Test
        @DisplayName("totalTickets <= 0 — 예외")
        void 잘못된_티켓수() {
            assertThatThrownBy(() -> newLedger(1000, 0))
                .isInstanceOf(RefundException.class);
        }
    }

    @Nested
    @DisplayName("applyRefund")
    class ApplyRefundTest {

        @Test
        @DisplayName("부분 적용 — PARTIAL 전이")
        void 부분_적용() {
            OrderRefund ledger = newLedger(30_000, 3);

            ledger.applyRefund(10_000, 1);

            assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.PARTIAL);
            assertThat(ledger.getRefundedAmount()).isEqualTo(10_000);
            assertThat(ledger.getRefundedTickets()).isEqualTo(1);
            assertThat(ledger.getRemainingAmount()).isEqualTo(20_000);
            assertThat(ledger.getRemainingTickets()).isEqualTo(2);
        }

        @Test
        @DisplayName("누적 적용 — PARTIAL → FULL 전이")
        void 누적_적용_FULL() {
            OrderRefund ledger = newLedger(30_000, 3);

            ledger.applyRefund(10_000, 1);
            ledger.applyRefund(20_000, 2);

            assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.FULL);
            assertThat(ledger.getRefundedAmount()).isEqualTo(30_000);
            assertThat(ledger.getRefundedTickets()).isEqualTo(3);
            assertThat(ledger.isFullyRefunded()).isTrue();
        }

        @Test
        @DisplayName("이미 FULL 상태 — 재적용 시 예외")
        void 이미_FULL_예외() {
            OrderRefund ledger = newLedger(10_000, 1);
            ledger.applyRefund(10_000, 1);

            assertThatThrownBy(() -> ledger.applyRefund(1, 1))
                .isInstanceOf(RefundException.class);
        }

        @Test
        @DisplayName("한도 초과 — 예외")
        void 한도_초과_예외() {
            OrderRefund ledger = newLedger(10_000, 2);

            assertThatThrownBy(() -> ledger.applyRefund(10_001, 1))
                .isInstanceOf(RefundException.class);
            assertThatThrownBy(() -> ledger.applyRefund(1_000, 3))
                .isInstanceOf(RefundException.class);
        }

        @Test
        @DisplayName("음수 입력 — 예외")
        void 음수_입력_예외() {
            OrderRefund ledger = newLedger(10_000, 2);
            assertThatThrownBy(() -> ledger.applyRefund(-1, 1))
                .isInstanceOf(RefundException.class);
            assertThatThrownBy(() -> ledger.applyRefund(100, -1))
                .isInstanceOf(RefundException.class);
        }
    }

    @Test
    @DisplayName("markFailed — status=FAILED")
    void markFailed() {
        OrderRefund ledger = newLedger(10_000, 1);
        ledger.markFailed();
        assertThat(ledger.getStatus()).isEqualTo(OrderRefundStatus.FAILED);
    }
}
