package com.devticket.commerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.exception.CommonErrorCode;
import com.devticket.commerce.order.application.usecase.OrderUsecase;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.order.domain.util.CartHashUtil;
import com.devticket.commerce.order.infrastructure.external.client.OrderToEventClient;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalStockAdjustmentResponse;
import com.devticket.commerce.order.presentation.dto.req.CartOrderRequest;
import com.devticket.commerce.order.presentation.dto.res.OrderResponse;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * IT-1 주문생성 플로우 통합테스트 — 동기 Internal API 방식.
 *
 * <p>검증 대상:
 * <ul>
 *   <li>createOrderByCart 단일 호출로 PAYMENT_PENDING 응답 즉시 반환
 *   <li>adjustStocks 실패 시 EXTERNAL_SERVICE_ERROR 예외 즉시 반환
 *   <li>중복 요청(동일 cart) 시 재고 차감 없이 기존 주문 반환
 *   <li>동일 cart 동시 N건 요청 시 CartItem 비관적 락으로 직렬화 → 주문 1건만 생성
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class OrderCreationFlowIntegrationTest {

    @MockitoBean private LockProvider lockProvider;
    @MockitoBean private OrderToEventClient orderToEventClient;

    @Autowired private OrderUsecase orderUsecase;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;

    private UUID userId;
    private UUID eventId;
    private CartItem savedCartItem;

    @BeforeEach
    void setUp() {
        given(lockProvider.lock(any())).willReturn(Optional.of(() -> {}));

        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        Cart cart = cartRepository.save(Cart.create(userId));
        savedCartItem = cartItemRepository.save(CartItem.create(cart.getId(), eventId, 2));

        given(orderToEventClient.getBulkEventInfo(anyList())).willReturn(List.of(
                new InternalEventInfoResponse(eventId, UUID.randomUUID(), "테스트 이벤트",
                        5_000, null, null, 100, 10, 98, null, null, null)
        ));
    }

    @Test
    @DisplayName("IT-1-A: adjustStocks 성공 → createOrderByCart가 PAYMENT_PENDING 즉시 반환")
    void returnsPaymentPendingImmediately() {
        given(orderToEventClient.adjustStocks(any())).willReturn(
                List.of(new InternalStockAdjustmentResponse(eventId, true, 96, "테스트 이벤트", 5_000, 10))
        );

        OrderResponse response = orderUsecase.createOrderByCart(
                userId, new CartOrderRequest(List.of(savedCartItem.getCartItemId())));

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

        Order saved = orderRepository.findByOrderId(response.orderId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("IT-1-B: adjustStocks 실패 → EXTERNAL_SERVICE_ERROR 예외 즉시 반환, Order 미저장")
    void throwsExternalServiceErrorWhenAdjustFails() {
        given(orderToEventClient.adjustStocks(any()))
                .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR));

        assertThatThrownBy(() -> orderUsecase.createOrderByCart(
                userId, new CartOrderRequest(List.of(savedCartItem.getCartItemId()))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.EXTERNAL_SERVICE_ERROR));

        String cartHash = CartHashUtil.compute(List.of(savedCartItem));
        assertThat(orderRepository.findActiveOrder(userId, cartHash, List.of(OrderStatus.PAYMENT_PENDING))).isEmpty();
    }

    @Test
    @DisplayName("IT-1-C: 동일 cart로 중복 요청 시 재고 차감 없이 기존 주문 반환")
    void returnsDuplicateOrderWithoutAdjustingStock() {
        given(orderToEventClient.adjustStocks(any())).willReturn(
                List.of(new InternalStockAdjustmentResponse(eventId, true, 96, "테스트 이벤트", 5_000, 10))
        );

        CartOrderRequest request = new CartOrderRequest(List.of(savedCartItem.getCartItemId()));

        OrderResponse first = orderUsecase.createOrderByCart(userId, request);
        OrderResponse second = orderUsecase.createOrderByCart(userId, request);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        // 두 번째 요청에서는 adjustStocks 추가 호출 없음 (총 1회)
        then(orderToEventClient).should(org.mockito.Mockito.times(1)).adjustStocks(any());
    }

    @Test
    @DisplayName("IT-1-D: 동일 cart로 동시 10건 요청 시 CartItem 비관적 락으로 직렬화 → 주문 1건만 생성, 모든 응답의 orderId 동일")
    void concurrentDuplicateRequestsCreateOnlyOneOrder() throws InterruptedException {
        given(orderToEventClient.adjustStocks(any())).willReturn(
                List.of(new InternalStockAdjustmentResponse(eventId, true, 96, "테스트 이벤트", 5_000, 10))
        );

        int threadCount = 10;
        CartOrderRequest request = new CartOrderRequest(List.of(savedCartItem.getCartItemId()));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<UUID> orderIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    OrderResponse response = orderUsecase.createOrderByCart(userId, request);
                    orderIds.add(response.orderId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        String cartHash = CartHashUtil.compute(List.of(savedCartItem));
        long savedOrderCount = orderRepository.findActiveOrder(
                userId, cartHash, List.of(OrderStatus.PAYMENT_PENDING)
        ).stream().count();

        assertThat(errorCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(orderIds.stream().distinct().count()).isEqualTo(1);
        assertThat(savedOrderCount).isEqualTo(1);
    }
}
