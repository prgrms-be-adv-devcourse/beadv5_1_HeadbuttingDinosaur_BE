package com.devticket.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.event.OrderCancelledEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderExpirationCancelServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OutboxService outboxService;

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

    private OrderItem buildOrderItem(UUID eventId, int quantity) {
        return OrderItem.builder()
                .orderItemId(UUID.randomUUID())
                .orderId(1L)
                .userId(UUID.randomUUID())
                .eventId(eventId)
                .price(5_000)
                .quantity(quantity)
                .subtotalAmount(5_000 * quantity)
                .build();
    }

    @Nested
    @DisplayName("정상 전이 — PAYMENT_PENDING → CANCELLED + order.cancelled Outbox 발행")
    class HappyPath {

        @Test
        @DisplayName("canTransitionTo 통과 → cancel() + save() + order.cancelled Outbox 발행")
        void cancelsPaymentPendingOrderAndPublishesOutbox() {
            // given
            Order order = buildOrder(OrderStatus.PAYMENT_PENDING);
            UUID eventId1 = UUID.randomUUID();
            UUID eventId2 = UUID.randomUUID();
            given(orderItemRepository.findAllByOrderId(order.getId()))
                    .willReturn(List.of(buildOrderItem(eventId1, 2), buildOrderItem(eventId2, 1)));

            // when
            cancelService.cancelOrder(order);

            // then — 상태 전이 + 저장
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should().save(order);

            // then — Outbox 발행 검증
            ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
            then(outboxService).should().save(
                    eq(order.getOrderId().toString()),
                    eq(order.getOrderId().toString()),
                    eq("OrderCancelled"),
                    eq(KafkaTopics.ORDER_CANCELLED),
                    eventCaptor.capture()
            );
            OrderCancelledEvent published = eventCaptor.getValue();
            assertThat(published.orderId()).isEqualTo(order.getOrderId());
            assertThat(published.userId()).isEqualTo(order.getUserId());
            assertThat(published.reason()).isEqualTo("ORDER_TIMEOUT");
            assertThat(published.orderItems()).hasSize(2);
            assertThat(published.orderItems()).extracting(OrderCancelledEvent.OrderItem::eventId)
                    .containsExactlyInAnyOrder(eventId1, eventId2);
            assertThat(published.orderItems()).extracting(OrderCancelledEvent.OrderItem::quantity)
                    .containsExactlyInAnyOrder(2, 1);
        }

        @Test
        @DisplayName("OrderItem 0건이어도 Outbox는 발행 (빈 리스트)")
        void publishesOutboxEvenWithEmptyOrderItems() {
            // given
            Order order = buildOrder(OrderStatus.PAYMENT_PENDING);
            given(orderItemRepository.findAllByOrderId(order.getId()))
                    .willReturn(Collections.emptyList());

            // when
            cancelService.cancelOrder(order);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should().save(order);

            ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
            then(outboxService).should().save(any(), any(), any(), any(), eventCaptor.capture());
            assertThat(eventCaptor.getValue().orderItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("canTransitionTo 실패 — Outbox 발행 없이 스킵")
    class SkipOnInvalidTransition {

        @Test
        @DisplayName("이미 CANCELLED 상태면 save/Outbox 호출 없이 스킵")
        void skipsWhenAlreadyCancelled() {
            // given
            Order order = buildOrder(OrderStatus.CANCELLED);

            // when
            cancelService.cancelOrder(order);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should(never()).save(any());
            then(outboxService).shouldHaveNoInteractions();
            then(orderItemRepository).shouldHaveNoInteractions();
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
            then(outboxService).shouldHaveNoInteractions();
            then(orderItemRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("낙관적 락 충돌 — Consumer와 동시 상태 전이")
    class OptimisticLockConflict {

        @Test
        @DisplayName("save 시 OptimisticLockException → 재조회 결과 PAID면 스킵 로그 (Outbox 발행 안 함)")
        void skipsAfterConflictWhenRefreshedToPaid() {
            // given — save()에서 예외 발생 → OrderItem 조회/Outbox 발행 경로 미진입
            Order order = buildOrder(OrderStatus.PAYMENT_PENDING);
            willThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getOrderId()))
                    .given(orderRepository).save(order);

            Order refreshed = buildOrder(OrderStatus.PAID);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(refreshed));

            // when / then (예외 전파되지 않음 — 내부 catch)
            cancelService.cancelOrder(order);

            then(orderRepository).should().save(order);
            then(orderRepository).should().findByOrderId(order.getOrderId());
            then(outboxService).shouldHaveNoInteractions();
            then(orderItemRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("save 시 OptimisticLockException → 재조회 실패(null) → 경고 로그 후 종료 (Outbox 발행 안 함)")
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
            then(outboxService).shouldHaveNoInteractions();
            then(orderItemRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("save 시 OptimisticLockException → 재조회 결과 PAYMENT_PENDING(비종단) → 재시도 필요 로그 (Outbox 발행 안 함)")
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
            then(outboxService).shouldHaveNoInteractions();
            then(orderItemRepository).shouldHaveNoInteractions();
        }
    }
}
