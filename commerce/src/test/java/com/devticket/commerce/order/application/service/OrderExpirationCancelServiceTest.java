package com.devticket.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderExpirationCancelServiceTest {

    @Mock private OrderRepository orderRepository;

    @InjectMocks private OrderExpirationCancelService cancelService;

    private Order buildOrder(OrderStatus status) {
        Order order = Order.builder()
                .orderId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .orderNumber("20260417-TEST0001")
                .totalAmount(10_000)
                .status(status)
                .orderedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }

    @Nested
    @DisplayName("정상 전이 — PAYMENT_PENDING → CANCELLED")
    class HappyPath {

        @Test
        @DisplayName("canTransitionTo 통과 → cancel() + save() 호출")
        void cancelsPaymentPendingOrder() {
            // given
            Order order = buildOrder(OrderStatus.PAYMENT_PENDING);

            // when
            cancelService.cancelOrder(order);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should().save(order);
        }
    }

    @Nested
    @DisplayName("canTransitionTo 실패 — 스킵")
    class SkipOnInvalidTransition {

        @Test
        @DisplayName("이미 CANCELLED 상태면 save 호출 없이 스킵")
        void skipsWhenAlreadyCancelled() {
            // given
            Order order = buildOrder(OrderStatus.CANCELLED);

            // when
            cancelService.cancelOrder(order);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("PAID 상태도 canTransitionTo(CANCELLED)는 가능하지만 호출 경로상 만료 대상 아님 — 직접 호출 시 cancel 수행")
        void paidOrderCanStillTransitionToCancelled() {
            // given — 스케줄러는 PAYMENT_PENDING만 조회하므로 실제 호출 안 되지만 도메인 전이는 가능
            Order order = buildOrder(OrderStatus.PAID);

            // when
            cancelService.cancelOrder(order);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should().save(order);
        }

        @Test
        @DisplayName("FAILED 상태 (종단) → canTransitionTo false → 스킵")
        void skipsWhenFailed() {
            // given
            Order order = buildOrder(OrderStatus.FAILED);

            // when
            cancelService.cancelOrder(order);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            then(orderRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("낙관적 락 충돌 — Consumer와 동시 상태 전이")
    class OptimisticLockConflict {

        @Test
        @DisplayName("save 시 OptimisticLockException → 재조회 결과 PAID면 스킵 로그")
        void skipsAfterConflictWhenRefreshedToPaid() {
            // given
            Order order = buildOrder(OrderStatus.PAYMENT_PENDING);
            willThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getOrderId()))
                    .given(orderRepository).save(order);

            Order refreshed = buildOrder(OrderStatus.PAID);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(refreshed));

            // when / then (예외 전파되지 않음 — 내부 catch)
            cancelService.cancelOrder(order);

            then(orderRepository).should().save(order);
            then(orderRepository).should().findByOrderId(order.getOrderId());
        }

        @Test
        @DisplayName("save 시 OptimisticLockException → 재조회 실패(null) → 경고 로그 후 종료")
        void warnsWhenRefreshReturnsEmpty() {
            // given
            Order order = buildOrder(OrderStatus.PAYMENT_PENDING);
            willThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getOrderId()))
                    .given(orderRepository).save(order);

            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.empty());

            // when
            cancelService.cancelOrder(order);

            // then
            then(orderRepository).should().save(order);
            then(orderRepository).should().findByOrderId(order.getOrderId());
        }

        @Test
        @DisplayName("save 시 OptimisticLockException → 재조회 결과 PAYMENT_PENDING(비종단) → 재시도 필요 로그")
        void retryNeededWhenStillPending() {
            // given
            Order order = buildOrder(OrderStatus.PAYMENT_PENDING);
            willThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getOrderId()))
                    .given(orderRepository).save(order);

            Order refreshed = buildOrder(OrderStatus.PAYMENT_PENDING);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(refreshed));

            // when
            cancelService.cancelOrder(order);

            // then — 예외 전파 없음, catch 블록 진입 확인
            then(orderRepository).should().save(order);
            then(orderRepository).should().findByOrderId(order.getOrderId());
        }
    }
}
