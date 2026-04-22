package com.devticket.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.devticket.commerce.cart.domain.exception.CartErrorCode;
import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.StockDeductedEvent;
import com.devticket.commerce.common.messaging.event.StockFailedEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.order.infrastructure.external.client.OrderToEventClient;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import com.devticket.commerce.order.presentation.dto.req.CartOrderRequest;
import com.devticket.commerce.order.presentation.dto.res.OrderResponse;
import com.devticket.commerce.order.presentation.dto.res.OrderStatusResponse;
import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderToEventClient orderToEventClient;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private TicketUsecase ticketUsecase;
    @Mock private TicketRepository ticketRepository;
    @Mock private OutboxService outboxService;
    @Mock private MessageDeduplicationService deduplicationService;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderToEventClient, cartRepository, cartItemRepository,
                orderRepository, orderItemRepository, ticketUsecase,
                ticketRepository, outboxService, deduplicationService, objectMapper
        );
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private static final Long CART_ID = 1L;

    private Cart cart(UUID userId) {
        return Cart.builder()
                .id(CART_ID)
                .userId(userId)
                .build();
    }

    private CartItem cartItem(Long cartId, UUID eventId, int quantity) {
        return CartItem.create(cartId, eventId, quantity);
    }

    private InternalEventInfoResponse eventInfo(UUID eventId, int price, int maxQuantity) {
        return new InternalEventInfoResponse(
                eventId, UUID.randomUUID(), "테스트 이벤트", price,
                null, null, 100, maxQuantity, 50,
                null, null, null
        );
    }

    private OrderItem orderItem(Long orderId, UUID userId, UUID eventId, int price, int quantity) {
        return OrderItem.create(orderId, userId, eventId, price, quantity, quantity + 10);
    }

    private Order savedOrder(UUID userId, int totalAmount, String cartHash) {
        return Order.create(userId, totalAmount, cartHash);
    }

    private Order createdOrder() {
        return Order.create(UUID.randomUUID(), 10_000, "hash");
    }

    private Order paymentPendingOrder() {
        Order order = createdOrder();
        order.pendingPayment();
        return order;
    }

    private Order failedOrder() {
        Order order = createdOrder();
        order.failByStock();
        return order;
    }

    private Order cancelledOrder() {
        Order order = paymentPendingOrder();
        order.cancel();
        return order;
    }

    private Order paidOrder() {
        Order order = paymentPendingOrder();
        order.completePayment();
        return order;
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String stockDeductedPayload(UUID orderId) {
        return toJson(new StockDeductedEvent(orderId, UUID.randomUUID(), 2, Instant.now()));
    }

    private String stockFailedPayload(UUID orderId) {
        return toJson(new StockFailedEvent(orderId, UUID.randomUUID(), "재고 부족", Instant.now()));
    }

    // ── 신규_주문_생성 ────────────────────────────────────────────────

    @Nested
    class 신규_주문_생성 {

        @Test
        void 정상_흐름_신규_주문을_생성하고_Outbox에_저장한다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            UUID cartItemId = UUID.randomUUID();

            CartItem item = cartItem(1L, eventId, 2);
            InternalEventInfoResponse info = eventInfo(eventId, 5_000, 10);
            Order order = savedOrder(userId, 10_000, "hash");
            OrderItem savedItem = orderItem(order.getId(), userId, eventId, 5_000, 2);

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findAllByCartItemIdWithLock(anyList())).willReturn(List.of(item));
            given(orderRepository.findActiveOrder(eq(userId), anyString(), anyList()))
                    .willReturn(Optional.empty());
            given(orderToEventClient.getBulkEventInfo(anyList())).willReturn(List.of(info));
            given(orderRepository.save(any(Order.class))).willReturn(order);
            given(orderItemRepository.saveAll(anyList())).willReturn(List.of(savedItem));

            CartOrderRequest request = new CartOrderRequest(List.of(cartItemId));

            OrderResponse response = orderService.createOrderByCart(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.orderStatus()).isEqualTo(OrderStatus.CREATED);
            then(orderRepository).should().save(any(Order.class));
            then(orderItemRepository).should().saveAll(anyList());
            then(outboxService).should().save(
                    anyString(), anyString(),
                    eq("OrderCreated"),
                    eq(KafkaTopics.ORDER_CREATED),
                    any()
            );
        }

        @Test
        void 총_주문_금액은_가격_곱하기_수량의_합계다() {
            UUID userId = UUID.randomUUID();
            UUID eventId1 = UUID.randomUUID();
            UUID eventId2 = UUID.randomUUID();

            CartItem item1 = cartItem(1L, eventId1, 2);
            CartItem item2 = cartItem(1L, eventId2, 3);

            List<InternalEventInfoResponse> infos = List.of(
                    eventInfo(eventId1, 3_000, 10),
                    eventInfo(eventId2, 4_000, 10)
            );

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findAllByCartItemIdWithLock(anyList())).willReturn(List.of(item1, item2));
            given(orderRepository.findActiveOrder(eq(userId), anyString(), anyList()))
                    .willReturn(Optional.empty());
            given(orderToEventClient.getBulkEventInfo(anyList())).willReturn(infos);
            given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
            given(orderItemRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

            CartOrderRequest request = new CartOrderRequest(List.of(UUID.randomUUID(), UUID.randomUUID()));

            OrderResponse response = orderService.createOrderByCart(userId, request);

            assertThat(response.totalAmount()).isEqualTo(18_000L);
        }

        @Test
        void order_created_토픽으로_Outbox를_저장한다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();

            CartItem item = cartItem(1L, eventId, 1);
            InternalEventInfoResponse info = eventInfo(eventId, 10_000, 5);
            Order order = savedOrder(userId, 10_000, "hash");
            OrderItem savedItem = orderItem(order.getId(), userId, eventId, 10_000, 1);

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findAllByCartItemIdWithLock(anyList())).willReturn(List.of(item));
            given(orderRepository.findActiveOrder(eq(userId), anyString(), anyList()))
                    .willReturn(Optional.empty());
            given(orderToEventClient.getBulkEventInfo(anyList())).willReturn(List.of(info));
            given(orderRepository.save(any(Order.class))).willReturn(order);
            given(orderItemRepository.saveAll(anyList())).willReturn(List.of(savedItem));

            orderService.createOrderByCart(userId, new CartOrderRequest(List.of(UUID.randomUUID())));

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            then(outboxService).should().save(anyString(), anyString(), anyString(), topicCaptor.capture(), any());
            assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopics.ORDER_CREATED);
        }
    }

    // ── 중복_주문_반환 ────────────────────────────────────────────────

    @Nested
    class 중복_주문_반환 {

        @Test
        void CREATED_상태_활성_주문이_있으면_기존_주문을_반환한다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            CartItem item = cartItem(1L, eventId, 1);

            Order existingOrder = savedOrder(userId, 5_000, "some-hash");
            OrderItem existingItem = orderItem(existingOrder.getId(), userId, eventId, 5_000, 1);
            InternalEventInfoResponse info = eventInfo(eventId, 5_000, 5);

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findAllByCartItemIdWithLock(anyList())).willReturn(List.of(item));
            given(orderRepository.findActiveOrder(eq(userId), anyString(), anyList()))
                    .willReturn(Optional.of(existingOrder));
            given(orderItemRepository.findAllByOrderId(existingOrder.getId()))
                    .willReturn(List.of(existingItem));
            given(orderToEventClient.getBulkEventInfo(anyList())).willReturn(List.of(info));

            OrderResponse response = orderService.createOrderByCart(userId, new CartOrderRequest(List.of(UUID.randomUUID())));

            assertThat(response.orderId()).isEqualTo(existingOrder.getOrderId());
            then(orderRepository).should(never()).save(any(Order.class));
            then(outboxService).shouldHaveNoInteractions();
        }

        @Test
        void PAYMENT_PENDING_상태_활성_주문이_있으면_기존_주문을_반환한다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            CartItem item = cartItem(1L, eventId, 1);

            Order existingOrder = savedOrder(userId, 5_000, "some-hash");
            existingOrder.pendingPayment();
            OrderItem existingItem = orderItem(existingOrder.getId(), userId, eventId, 5_000, 1);
            InternalEventInfoResponse info = eventInfo(eventId, 5_000, 5);

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findAllByCartItemIdWithLock(anyList())).willReturn(List.of(item));
            given(orderRepository.findActiveOrder(eq(userId), anyString(), anyList()))
                    .willReturn(Optional.of(existingOrder));
            given(orderItemRepository.findAllByOrderId(existingOrder.getId()))
                    .willReturn(List.of(existingItem));
            given(orderToEventClient.getBulkEventInfo(anyList())).willReturn(List.of(info));

            OrderResponse response = orderService.createOrderByCart(userId, new CartOrderRequest(List.of(UUID.randomUUID())));

            assertThat(response.orderId()).isEqualTo(existingOrder.getOrderId());
            assertThat(response.orderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            then(orderRepository).should(never()).save(any(Order.class));
            then(outboxService).shouldHaveNoInteractions();
        }
    }

    // ── 예외_케이스 ────────────────────────────────────────────────────

    @Nested
    class 예외_케이스 {

        @Test
        void 장바구니가_없으면_BusinessException을_던진다() {
            UUID userId = UUID.randomUUID();
            given(cartRepository.findByUserId(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrderByCart(userId, new CartOrderRequest(List.of(UUID.randomUUID()))))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(CartErrorCode.CART_NOT_FOUND));

            then(cartItemRepository).shouldHaveNoInteractions();
            then(orderRepository).shouldHaveNoInteractions();
            then(outboxService).shouldHaveNoInteractions();
        }

        @Test
        void 중복된_cartItemId가_요청되면_DUPLICATE_CART_ITEM_ID_예외를_던진다() {
            UUID userId = UUID.randomUUID();
            UUID duplicateId = UUID.randomUUID();

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));

            assertThatThrownBy(() -> orderService.createOrderByCart(userId, new CartOrderRequest(List.of(duplicateId, duplicateId))))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(CartErrorCode.DUPLICATE_CART_ITEM_ID));

            then(cartItemRepository).shouldHaveNoInteractions();
            then(orderRepository).shouldHaveNoInteractions();
        }

        @Test
        void 요청한_cartItem이_DB에_없으면_CART_ITEM_NOT_FOUND_예외를_던진다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();

            CartItem item = cartItem(CART_ID, eventId, 1);

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findAllByCartItemIdWithLock(anyList())).willReturn(List.of(item));

            assertThatThrownBy(() -> orderService.createOrderByCart(userId, new CartOrderRequest(List.of(UUID.randomUUID(), UUID.randomUUID()))))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND));

            then(orderRepository).shouldHaveNoInteractions();
            then(outboxService).shouldHaveNoInteractions();
        }

        @Test
        void 다른_유저의_CartItem을_포함하면_CART_ITEM_NOT_FOUND_예외를_던진다() {
            UUID userId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            UUID cartItemId = UUID.randomUUID();

            CartItem otherUsersItem = cartItem(99L, eventId, 1);

            given(cartRepository.findByUserId(userId)).willReturn(Optional.of(cart(userId)));
            given(cartItemRepository.findAllByCartItemIdWithLock(anyList())).willReturn(List.of(otherUsersItem));

            assertThatThrownBy(() -> orderService.createOrderByCart(userId, new CartOrderRequest(List.of(cartItemId))))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND));

            then(orderRepository).shouldHaveNoInteractions();
            then(outboxService).shouldHaveNoInteractions();
        }
    }

    // ── ProcessStockDeducted ──────────────────────────────────────────

    @Nested
    class ProcessStockDeducted {

        @Test
        void CREATED_상태_주문을_PAYMENT_PENDING으로_전이한다() {
            Order order = createdOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            orderService.processStockDeducted(
                    messageId, KafkaTopics.STOCK_DEDUCTED, stockDeductedPayload(order.getOrderId()));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.STOCK_DEDUCTED);
        }

        @Test
        void 중복_메시지면_주문_조회_없이_스킵한다() {
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(true);

            orderService.processStockDeducted(
                    messageId, KafkaTopics.STOCK_DEDUCTED, stockDeductedPayload(UUID.randomUUID()));

            then(orderRepository).shouldHaveNoInteractions();
            then(deduplicationService).should(never()).markProcessed(messageId, KafkaTopics.STOCK_DEDUCTED);
        }

        @Test
        void 이미_PAYMENT_PENDING이면_멱등_스킵하고_markProcessed를_호출한다() {
            Order order = paymentPendingOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            orderService.processStockDeducted(
                    messageId, KafkaTopics.STOCK_DEDUCTED, stockDeductedPayload(order.getOrderId()));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.STOCK_DEDUCTED);
        }

        @Test
        void CANCELLED_상태면_정책적_스킵하고_markProcessed를_호출한다() {
            Order order = cancelledOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            orderService.processStockDeducted(
                    messageId, KafkaTopics.STOCK_DEDUCTED, stockDeductedPayload(order.getOrderId()));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.STOCK_DEDUCTED);
        }

        @Test
        void FAILED_상태면_정책적_스킵하고_markProcessed를_호출한다() {
            Order order = failedOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            orderService.processStockDeducted(
                    messageId, KafkaTopics.STOCK_DEDUCTED, stockDeductedPayload(order.getOrderId()));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.STOCK_DEDUCTED);
        }

        @Test
        void PAID_상태면_이상_상태_예외를_던진다() {
            Order order = paidOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.processStockDeducted(
                    messageId, KafkaTopics.STOCK_DEDUCTED, stockDeductedPayload(order.getOrderId())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAID");

            then(deduplicationService).should(never()).markProcessed(messageId, KafkaTopics.STOCK_DEDUCTED);
        }

        @Test
        void 주문이_없으면_BusinessException을_던진다() {
            UUID orderId = UUID.randomUUID();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.processStockDeducted(
                    messageId, KafkaTopics.STOCK_DEDUCTED, stockDeductedPayload(orderId)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── ProcessStockFailed ────────────────────────────────────────────

    @Nested
    class 주문_상태_폴링 {

        @Test
        void CREATED_상태와_updatedAt을_반환한다() {
            UUID userId = UUID.randomUUID();
            Order order = Order.create(userId, 10_000, "hash");
            LocalDateTime now = LocalDateTime.now();
            ReflectionTestUtils.setField(order, "updatedAt", now);
            UUID orderId = UUID.randomUUID();

            given(orderRepository.findByOrderId(orderId)).willReturn(Optional.of(order));

            OrderStatusResponse response = orderService.getOrderStatus(userId, orderId);

            assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(response.orderId()).isEqualTo(order.getOrderId());
            assertThat(response.updatedAt()).isEqualTo(now);
        }

        @Test
        void PAYMENT_PENDING_상태를_반환한다() {
            UUID userId = UUID.randomUUID();
            Order order = Order.create(userId, 10_000, "hash");
            order.pendingPayment();
            UUID orderId = UUID.randomUUID();

            given(orderRepository.findByOrderId(orderId)).willReturn(Optional.of(order));

            OrderStatusResponse response = orderService.getOrderStatus(userId, orderId);

            assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        }

        @Test
        void 존재하지_않는_주문이면_ORDER_NOT_FOUND_예외를_던진다() {
            UUID userId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            given(orderRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderStatus(userId, orderId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
        }

        @Test
        void 다른_유저의_주문이면_ORDER_FORBIDDEN_예외를_던진다() {
            UUID userId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            Order order = Order.create(otherUserId, 10_000, "hash");

            given(orderRepository.findByOrderId(orderId)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.getOrderStatus(userId, orderId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(OrderErrorCode.ORDER_FORBIDDEN));
        }
    }

    @Nested
    class ProcessStockFailed {

        @Test
        void CREATED_상태_주문을_FAILED로_전이한다() {
            Order order = createdOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            orderService.processStockFailed(
                    messageId, KafkaTopics.STOCK_FAILED, stockFailedPayload(order.getOrderId()));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.STOCK_FAILED);
        }

        @Test
        void 중복_메시지면_주문_조회_없이_스킵한다() {
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(true);

            orderService.processStockFailed(
                    messageId, KafkaTopics.STOCK_FAILED, stockFailedPayload(UUID.randomUUID()));

            then(orderRepository).shouldHaveNoInteractions();
            then(deduplicationService).should(never()).markProcessed(messageId, KafkaTopics.STOCK_FAILED);
        }

        @Test
        void 이미_FAILED면_멱등_스킵하고_markProcessed를_호출한다() {
            Order order = failedOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            orderService.processStockFailed(
                    messageId, KafkaTopics.STOCK_FAILED, stockFailedPayload(order.getOrderId()));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.STOCK_FAILED);
        }

        @Test
        void CANCELLED_상태면_정책적_스킵하고_markProcessed를_호출한다() {
            Order order = cancelledOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            orderService.processStockFailed(
                    messageId, KafkaTopics.STOCK_FAILED, stockFailedPayload(order.getOrderId()));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(deduplicationService).should().markProcessed(messageId, KafkaTopics.STOCK_FAILED);
        }

        @Test
        void PAYMENT_PENDING_상태면_이상_상태_예외를_던진다() {
            Order order = paymentPendingOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.processStockFailed(
                    messageId, KafkaTopics.STOCK_FAILED, stockFailedPayload(order.getOrderId())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAYMENT_PENDING");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            then(deduplicationService).should(never()).markProcessed(messageId, KafkaTopics.STOCK_FAILED);
        }

        @Test
        void PAID_상태면_이상_상태_예외를_던진다() {
            Order order = paidOrder();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(order.getOrderId())).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.processStockFailed(
                    messageId, KafkaTopics.STOCK_FAILED, stockFailedPayload(order.getOrderId())))
                    .isInstanceOf(IllegalStateException.class);

            then(deduplicationService).should(never()).markProcessed(messageId, KafkaTopics.STOCK_FAILED);
        }

        @Test
        void 주문이_없으면_BusinessException을_던진다() {
            UUID orderId = UUID.randomUUID();
            UUID messageId = UUID.randomUUID();
            given(deduplicationService.isDuplicate(messageId)).willReturn(false);
            given(orderRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.processStockFailed(
                    messageId, KafkaTopics.STOCK_FAILED, stockFailedPayload(orderId)))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
