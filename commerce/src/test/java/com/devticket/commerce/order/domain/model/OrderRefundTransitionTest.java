package com.devticket.commerce.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderRefundTransitionTest {

    private Order orderIn(OrderStatus status) {
        Order order = Order.create(UUID.randomUUID(), 10_000, "hash");
        setStatus(order, status);
        return order;
    }

    private static void setStatus(Order order, OrderStatus status) {
        try {
            Field field = Order.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(order, status);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("canTransitionTo — 환불 관련 전이")
    class CanTransition {

        @Test
        void PAID_에서_REFUND_PENDING_과_CANCELLED_로_전이_가능() {
            Order order = orderIn(OrderStatus.PAID);
            assertThat(order.canTransitionTo(OrderStatus.REFUND_PENDING)).isTrue();
            assertThat(order.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            assertThat(order.canTransitionTo(OrderStatus.REFUNDED)).isFalse();
        }

        @Test
        void REFUND_PENDING_에서_REFUNDED_와_PAID_로_전이_가능() {
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            assertThat(order.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
            assertThat(order.canTransitionTo(OrderStatus.PAID)).isTrue();
            assertThat(order.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        }

        @Test
        void REFUNDED_는_종단_상태() {
            Order order = orderIn(OrderStatus.REFUNDED);
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(order.canTransitionTo(target))
                    .as("REFUNDED → %s 전이", target)
                    .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("requestRefund")
    class RequestRefund {

        @Test
        void PAID_에서_REFUND_PENDING_으로_전이() {
            Order order = orderIn(OrderStatus.PAID);
            order.requestRefund();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_PENDING);
        }

        @Test
        void 종단_상태에서는_예외() {
            Order order = orderIn(OrderStatus.REFUNDED);
            assertThatThrownBy(order::requestRefund)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.REFUND_NOT_REFUNDABLE);
        }
    }

    @Nested
    @DisplayName("completeRefund")
    class CompleteRefund {

        @Test
        void REFUND_PENDING_에서_REFUNDED_로_전이() {
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            order.completeRefund();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        }

        @Test
        void PAID_상태에서는_예외() {
            Order order = orderIn(OrderStatus.PAID);
            assertThatThrownBy(order::completeRefund)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.REFUND_COMPLETE_INVALID);
        }
    }

    @Nested
    @DisplayName("rollbackRefund")
    class RollbackRefund {

        @Test
        void REFUND_PENDING_에서_PAID_로_롤백() {
            Order order = orderIn(OrderStatus.REFUND_PENDING);
            order.rollbackRefund();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        void PAID_상태에서는_예외() {
            Order order = orderIn(OrderStatus.PAID);
            assertThatThrownBy(order::rollbackRefund)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.REFUND_ROLLBACK_INVALID);
        }
    }
}
