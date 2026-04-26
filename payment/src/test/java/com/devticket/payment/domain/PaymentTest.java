package com.devticket.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.exception.PaymentErrorCode;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.domain.model.Payment;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Payment 상태 전이 가드")
class PaymentTest {

    private Payment createReadyPayment() {
        return Payment.create(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PG, 10000);
    }

    @Nested
    @DisplayName("approve()")
    class ApproveTest {

        @Test
        void READY에서_SUCCESS로_전이_성공() {
            // given
            Payment payment = createReadyPayment();

            // when
            payment.approve("paymentKey-123", LocalDateTime.now());

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPaymentKey()).isEqualTo("paymentKey-123");
        }

        @Test
        void SUCCESS에서_approve_호출시_예외() {
            // given
            Payment payment = createReadyPayment();
            payment.approve("paymentKey-123");

            // when & then
            assertThatThrownBy(() -> payment.approve("paymentKey-456"))
                .isInstanceOf(PaymentException.class)
                .satisfies(ex -> assertThat(((PaymentException) ex).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_STATUS_TRANSITION));
        }

        @Test
        void FAILED에서_approve_호출시_예외() {
            // given
            Payment payment = createReadyPayment();
            payment.fail("테스트 실패");

            // when & then
            assertThatThrownBy(() -> payment.approve("paymentKey-123"))
                .isInstanceOf(PaymentException.class);
        }
    }

    @Nested
    @DisplayName("fail()")
    class FailTest {

        @Test
        void READY에서_FAILED로_전이_성공() {
            // given
            Payment payment = createReadyPayment();

            // when
            payment.fail("PG 거절");

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("PG 거절");
        }

        @Test
        void SUCCESS에서_fail_호출시_예외() {
            // given
            Payment payment = createReadyPayment();
            payment.approve("paymentKey-123");

            // when & then
            assertThatThrownBy(() -> payment.fail("이미 성공한 결제"))
                .isInstanceOf(PaymentException.class);
        }
    }

    @Nested
    @DisplayName("cancel()")
    class CancelTest {

        @Test
        void READY에서_CANCELLED로_전이_성공() {
            // given
            Payment payment = createReadyPayment();

            // when
            payment.cancel();

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        void FAILED에서_cancel_호출시_예외() {
            // given
            Payment payment = createReadyPayment();
            payment.fail("실패");

            // when & then
            assertThatThrownBy(() -> payment.cancel())
                .isInstanceOf(PaymentException.class);
        }
    }

    @Nested
    @DisplayName("refund()")
    class RefundTest {

        @Test
        void SUCCESS에서_REFUNDED로_전이_성공() {
            // given
            Payment payment = createReadyPayment();
            payment.approve("paymentKey-123");

            // when
            payment.refund();

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        void READY에서_refund_호출시_예외() {
            // given
            Payment payment = createReadyPayment();

            // when & then
            assertThatThrownBy(() -> payment.refund())
                .isInstanceOf(PaymentException.class);
        }
    }
}
