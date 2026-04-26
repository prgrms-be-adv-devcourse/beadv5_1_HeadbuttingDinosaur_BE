package com.devticket.commerce.order.application.service;

import com.devticket.commerce.cart.domain.exception.CartErrorCode;
import com.devticket.commerce.cart.domain.exception.EventErrorCode;
import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.cart.domain.repository.CartRepository;
import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.messaging.MessageDeduplicationService;
import com.devticket.commerce.common.messaging.event.OrderCreatedEvent;
import com.devticket.commerce.common.messaging.event.PaymentCompletedEvent;
import com.devticket.commerce.common.messaging.event.PaymentFailedEvent;
import com.devticket.commerce.common.messaging.event.TicketIssueFailedEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.application.usecase.OrderUsecase;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.common.messaging.KafkaTopics;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.domain.util.CartHashUtil;
import com.devticket.commerce.order.infrastructure.external.client.OrderToEventClient;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalBulkStockAdjustmentRequest;
import com.devticket.commerce.order.presentation.dto.req.CartOrderRequest;
import com.devticket.commerce.order.presentation.dto.req.OrderListRequest;
import com.devticket.commerce.order.presentation.dto.res.InternalOrderInfoResponse;
import com.devticket.commerce.order.presentation.dto.res.InternalOrderItemResponse;
import com.devticket.commerce.order.presentation.dto.res.InternalOrderItemsResponse;
import com.devticket.commerce.order.presentation.dto.res.InternalSettlementDataResponse;
import com.devticket.commerce.order.presentation.dto.res.OrderCancelResponse;
import com.devticket.commerce.order.presentation.dto.res.OrderDetailResponse;
import com.devticket.commerce.order.presentation.dto.res.OrderListResponse;
import com.devticket.commerce.order.presentation.dto.res.OrderResponse;
import com.devticket.commerce.order.presentation.dto.res.OrderStatusResponse;
import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.domain.exception.TicketErrorCode;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;

import java.util.Objects;
import java.util.stream.IntStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService implements OrderUsecase {

    private final OrderToEventClient orderToEventClient;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TicketUsecase ticketUsecase;
    private final TicketRepository ticketRepository;
    private final OutboxService outboxService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager txManager;

    // tx 경계 분리를 위한 중간 DTO
    private record PrepareResult(
        UUID userId,
        List<CartItem> cartItems,
        List<InternalEventInfoResponse> eventInfos,
        int totalAmount,
        String cartHash,
        Order existingOrder,
        List<OrderItem> existingItems,
        Map<UUID, String> existingEventTitles
    ) {}

    // ==== Public Methods (Main Flow) ====================================

    // 장바구니에서 상품선택 후 주문하기 진행
    @Override
    public OrderResponse createOrderByCart(UUID userId, CartOrderRequest request) {
        // tx1: 검증, 해시, dedup, 이벤트 정보 조회, 총액 계산
        PrepareResult p = Objects.requireNonNull(
            new TransactionTemplate(txManager).execute(status -> prepareAndValidate(userId, request))
        );

        // 이미 활성 주문이 있으면 즉시 반환 (재고 차감 없음)
        if (p.existingOrder() != null) {
            return OrderResponse.of(p.existingOrder(), p.existingItems(), p.existingEventTitles());
        }

        // HTTP, tx 없음: 재고 동기 차감 — 부분 실패 시 BusinessException
        adjustStocksOrThrow(p.cartItems());

        // tx2: Order/OrderItem 저장 (PAYMENT_PENDING) — 저장 실패 시 재고 보상
        try {
            return Objects.requireNonNull(
                new TransactionTemplate(txManager).execute(status -> persistOrder(p))
            );
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateActiveOrderViolation(e)) {
                compensateStock(p.cartItems());
                throw e;
            }
            // 동시 요청이 먼저 커밋한 경우 — 재고 보상 없이 기존 주문 반환
            return orderRepository.findActiveOrder(
                            p.userId(), p.cartHash(), List.of(OrderStatus.PAYMENT_PENDING))
                    .map(existing -> {
                        List<OrderItem> items = orderItemRepository.findAllByOrderId(existing.getId());
                        Map<UUID, String> titles = p.eventInfos().stream()
                                .collect(Collectors.toMap(
                                        InternalEventInfoResponse::eventId,
                                        InternalEventInfoResponse::title));
                        return OrderResponse.of(existing, items, titles);
                    })
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_CREATION_CONFLICT));
        } catch (RuntimeException e) {
            compensateStock(p.cartItems());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatusResponse getOrderStatus(UUID userId, UUID orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
        }

        return OrderStatusResponse.of(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(UUID userId, UUID orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());

        List<UUID> eventIds = orderItems.stream()
            .map(OrderItem::getEventId)
            .distinct()
            .toList();

        Map<UUID, String> eventTitles = orderToEventClient.getBulkEventInfo(eventIds).stream()
            .collect(Collectors.toMap(InternalEventInfoResponse::eventId, InternalEventInfoResponse::title));

        return OrderDetailResponse.of(order, orderItems, eventTitles);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderListResponse getOrderList(UUID userId, OrderListRequest request) {
        OrderStatus status = null;
        if (request.status() != null && !request.status().isBlank()) {
            try {
                status = OrderStatus.valueOf(request.status());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_FILTER);
            }
        }

        PageRequest pageable = PageRequest.of(request.page() - 1, request.size(), Sort.by("id").descending());
        Page<Order> orderPage = orderRepository.findAllByUserId(userId, status, pageable);

        return OrderListResponse.of(orderPage);
    }

    @Override
    public InternalOrderInfoResponse getOrderInfo(UUID orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
        return InternalOrderInfoResponse.from(order, orderItems);
    }

    @Override
    public InternalOrderItemsResponse getOrderListForSettlement(Long id) {
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(id);
        return InternalOrderItemsResponse.from(id, orderItems);
    }

    @Override
    @Transactional
    public void failOrder(UUID orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        order.failPayment();
    }

    @Override
    @Transactional
    public void completeOrder(UUID orderId) {
        //order조회
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        //order상태값 변경
        order.completePayment();
        //Ticket발행
        TicketRequest request = new TicketRequest(order.getId());
        ticketUsecase.createTicket(request);
    }

    @Override
    public InternalSettlementDataResponse getSettelmentData(UUID sellerId, String periodStart, String periodEnd) {
        try {
            log.info("[Settlement Debug] 시작 - sellerId: {}, period: {} ~ {}", sellerId, periodStart, periodEnd);

            // 1. Event 서비스에서 sellerId + 기간으로 해당 판매자의 이벤트 목록 조회
            List<InternalEventInfoResponse> sellerEvents = orderToEventClient.getSellerEventsByPeriod(
                sellerId, periodStart, periodEnd);
            log.info("[Settlement Debug] 정산 기간 내 판매자 Event 수: {}", sellerEvents.size());

            if (sellerEvents.isEmpty()) {
                log.warn("[Settlement Debug] 정산 기간 내 이벤트가 없어 빈 응답을 반환합니다.");
                return new InternalSettlementDataResponse(sellerId, periodStart, periodEnd, List.of());
            }

            // 2. eventId 목록 추출 후 해당 OrderItem만 조회
            List<UUID> eventIds = sellerEvents.stream()
                .map(InternalEventInfoResponse::eventId)
                .toList();
            log.info("[Settlement Debug] 조회할 Event ID 목록: {}", eventIds);

            List<OrderItem> orderItems = orderItemRepository.findSettlementItems(eventIds);
            log.info("[Settlement Debug] 조회된 OrderItem 수: {}", orderItems.size());

            if (orderItems.isEmpty()) {
                log.warn("[Settlement Debug] 조회된 데이터가 없어 빈 응답을 반환합니다.");
                return new InternalSettlementDataResponse(sellerId, periodStart, periodEnd, List.of());
            }

            // 3. OrderId 추출 및 Order 조회
            List<Long> orderIds = orderItems.stream()
                .map(OrderItem::getOrderId)
                .distinct()
                .toList();
            log.info("[Settlement Debug] 추출된 중복 제거 Order PK 리스트: {}", orderIds);

            List<Order> orders = orderRepository.findAllByIds(orderIds);
            log.info("[Settlement Debug] DB에서 조회된 Order 엔티티 수: {}", orders.size());

            // [주의] 여기서 중복 키 에러가 자주 발생하므로 안전하게 처리
            Map<Long, Order> orderMap = orders.stream()
                .collect(Collectors.toMap(
                    Order::getId,
                    order -> order,
                    (existing, replacement) -> existing // 중복 시 기존값 유지
                ));

            // 4. eventId별로 그룹화
            Map<UUID, List<OrderItem>> itemsByEvent = orderItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getEventId));
            log.info("[Settlement Debug] 그룹화된 Event 수: {}", itemsByEvent.size());

            // 5. 데이터 집계
            List<InternalSettlementDataResponse.EventSettlements> eventSettlements = itemsByEvent.entrySet().stream()
                .map(entry -> {
                    UUID eventId = entry.getKey();
                    List<OrderItem> itemList = entry.getValue();

                    int totalSales = 0;
                    int totalRefund = 0;
                    int soldQty = 0;
                    int refundQty = 0;

                    for (OrderItem item : itemList) {
                        Order order = orderMap.get(item.getOrderId());
                        if (order == null) {
                            log.warn("[Settlement Debug] Order를 찾을 수 없음 - orderId: {}", item.getOrderId());
                            continue;
                        }

                        OrderStatus orderStatus = order.getStatus();
                        if (OrderStatus.PAID.equals(orderStatus)) {
                            totalSales += item.getPrice() * item.getQuantity();
                            soldQty += item.getQuantity();
                        } else if (OrderStatus.CANCELLED.equals(orderStatus)
                            || OrderStatus.REFUND_PENDING.equals(orderStatus)
                            || OrderStatus.REFUNDED.equals(orderStatus)) {
                            // REFUND_PENDING/REFUNDED: Saga 로 확정된 환불 — CANCELLED 와 동일하게 환불 집계에 포함.
                            totalRefund += item.getPrice() * item.getQuantity();
                            refundQty += item.getQuantity();
                        }
                    }

                    List<InternalSettlementDataResponse.EventSettlements.OrderItems> detailItems = itemList.stream()
                        .map(i -> {
                            Order itemOrder = orderMap.get(i.getOrderId());
                            String orderStatus = (itemOrder != null) ? itemOrder.getStatus().name() : "UNKNOWN";

                            return new InternalSettlementDataResponse.EventSettlements.OrderItems(
                                i.getOrderItemId(),
                                i.getEventId(),
                                i.getPrice(),
                                i.getQuantity(),
                                i.getSubtotalAmount(),
                                orderStatus
                            );
                        })
                        .toList();

                    return new InternalSettlementDataResponse.EventSettlements(
                        eventId, totalSales, totalRefund, soldQty, refundQty, detailItems
                    );
                })
                .toList();

            log.info("[Settlement Debug] 최종 응답 구성 완료");
            return new InternalSettlementDataResponse(sellerId, periodStart, periodEnd, eventSettlements);

        } catch (Exception e) {
            log.error("[Settlement Debug] 에러 발생 원인: ", e);
            throw e;
        }
    }

    // 결제 전 주문 취소
    @Override
    @Transactional
    public OrderCancelResponse cancelOrder(UUID userId, UUID orderId) {
        // 1. 주문 정보 확인
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        // 2. 주문자 검증
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
        }
        // 3. 결제 상태 체크 (환불 흐름은 별도 경로 — API로는 PAID 취소 차단)
        if (order.getStatus().equals(OrderStatus.PAID)) {
            throw new BusinessException(OrderErrorCode.ALREADY_PAID_ORDER);
        }
        // 4. 상태 전이 선수행 — canTransitionTo(CANCELLED) 위반 시 외부 호출 전에 throw
        order.cancel();

        // 5. 전이 성공 후에만 재고 복구 — 종단 상태(FAILED/CANCELLED 등) 재진입으로 인한 over-restore 차단
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
        orderToEventClient.adjustStocks(InternalBulkStockAdjustmentRequest.createForCancelByOrderItems(orderItems));

        orderRepository.save(order);

        return OrderCancelResponse.of(order);
    }

//    @Override
//    public InternalEventOrdersResponse getOrdersByEvent(Long eventId, String status) {
//        List<OrderItem> orderItems = orderItemRepository.findAllByEventId(eventId);
//        return InternalEventOrdersResponse.from(eventId, orderItems);
//    }

    // ==== Kafka Consumer Handlers =======================================

    @Override
    @Transactional
    public void processPaymentCompleted(UUID messageId, String topic, String payload) {
        // Step 1. Dedup 체크
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }

        PaymentCompletedEvent event = deserialize(payload, PaymentCompletedEvent.class);

        // Step 2. 상태 전이 유효성 검증 (3분류)
        Order order = orderRepository.findByOrderId(event.orderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.canTransitionTo(OrderStatus.PAID)) {
            if (order.getStatus() == OrderStatus.PAID) {
                // ① 멱등 스킵: 이미 목표 상태
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FAILED) {
                // ② 정책적 스킵: 보상/만료가 먼저 처리됨
                log.warn("[processPaymentCompleted] 정책적 스킵 — orderId={}, 현재상태={}",
                    event.orderId(), order.getStatus());
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            // ③ 이상 상태 → throw → 재시도 → DLT
            throw new IllegalStateException(
                "Invalid transition: " + order.getStatus() + " -> PAID, orderId=" + event.orderId());
        }

        // Step 3. 비즈니스 로직 — paymentId/paymentMethod 기록하면서 PAID 전이
        order.completePayment(event.paymentId(), event.paymentMethod());

        // 티켓 발급 (직접 호출 — @Transactional 프록시 경유 시 rollback-only 오염 방지)
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
        if (orderItems.isEmpty()) {
            // 영구 실패: OrderItem 없음 → Order CANCELLED + ticket.issue-failed Outbox 발행
            log.error("[processPaymentCompleted] 티켓 발급 실패 — OrderItem 없음. orderId={}", event.orderId());
            order.cancel();

            TicketIssueFailedEvent failedEvent = new TicketIssueFailedEvent(
                event.orderId(),
                event.userId(),
                event.paymentId(),
                List.of(),
                event.totalAmount(),
                "OrderItem not found",
                Instant.now()
            );

            outboxService.save(
                event.orderId().toString(),
                event.orderId().toString(),
                "TICKET_ISSUE_FAILED",
                KafkaTopics.TICKET_ISSUE_FAILED,
                failedEvent
            );

            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        try {
            List<Ticket> tickets = orderItems.stream()
                .flatMap(item -> IntStream.range(0, item.getQuantity())
                    .mapToObj(i -> Ticket.create(item.getOrderItemId(), item.getUserId(), item.getEventId())))
                .collect(Collectors.toList());
            ticketRepository.saveAll(tickets);
        } catch (Exception e) {
            // 영구 실패: 티켓 발급 실패 → Order CANCELLED + ticket.issue-failed Outbox 발행
            log.error("[processPaymentCompleted] 티켓 발급 실패 — orderId={}, error={}",
                event.orderId(), e.getMessage());
            order.cancel();

            TicketIssueFailedEvent failedEvent = new TicketIssueFailedEvent(
                event.orderId(),
                event.userId(),
                event.paymentId(),
                List.of(),
                event.totalAmount(),
                e.getMessage(),
                Instant.now()
            );

            outboxService.save(
                event.orderId().toString(),
                event.orderId().toString(),
                "TICKET_ISSUE_FAILED",
                KafkaTopics.TICKET_ISSUE_FAILED,
                failedEvent
            );

            deduplicationService.markProcessed(messageId, topic);
            return;
        }

        // 장바구니 분기 삭제 (A안, #427) — 결제된 OrderItem eventId별 총 qty를 카트에서 차감
        //   - 차감 후 orderedByEvent 잔여 수량을 갱신/제거 → 동일 eventId 행이 복수일 때 과차감 방지
        //     (UNIQUE 제약은 신규 데이터에 대해서만 보장 — 레거시 중복 행 가능성 방어)
        //   - remaining == 0 이면 row 삭제, 그 외 quantity 갱신
        //   - 결제 대상이 아닌 CartItem 은 카트에 보존 (부분 결제 UX 대응)
        Map<UUID, Integer> orderedByEvent = orderItems.stream()
                .collect(Collectors.toMap(
                        OrderItem::getEventId,
                        OrderItem::getQuantity,
                        Integer::sum
                ));
        cartRepository.findByUserId(event.userId()).ifPresent(cart -> {
            List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());
            List<CartItem> toDelete = new ArrayList<>();
            for (CartItem cartItem : cartItems) {
                Integer orderedQty = orderedByEvent.get(cartItem.getEventId());
                if (orderedQty == null || orderedQty == 0) {
                    continue;
                }
                int deduct = Math.min(orderedQty, cartItem.getQuantity());
                int remaining = cartItem.getQuantity() - deduct;
                if (remaining == 0) {
                    toDelete.add(cartItem);
                } else {
                    cartItem.updateQuantity(remaining);
                }
                int remainingOrder = orderedQty - deduct;
                if (remainingOrder == 0) {
                    orderedByEvent.remove(cartItem.getEventId());
                } else {
                    orderedByEvent.put(cartItem.getEventId(), remainingOrder);
                }
            }
            if (!toDelete.isEmpty()) {
                cartItemRepository.deleteAllInBatch(toDelete);
            }
        });

        // Step 4. Dedup 기록 (같은 트랜잭션)
        deduplicationService.markProcessed(messageId, topic);
    }

    @Override
    @Transactional
    public void processPaymentFailed(UUID messageId, String topic, String payload) {
        // Step 1. Dedup 체크
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }

        PaymentFailedEvent event = deserialize(payload, PaymentFailedEvent.class);

        // Step 2. 상태 전이 유효성 검증 (3분류)
        Order order = orderRepository.findByOrderId(event.orderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.canTransitionTo(OrderStatus.FAILED)) {
            if (order.getStatus() == OrderStatus.FAILED) {
                // ① 멱등 스킵: 이미 목표 상태
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            if (order.getStatus() == OrderStatus.CANCELLED) {
                // ② 정책적 스킵
                log.warn("[processPaymentFailed] 정책적 스킵 — orderId={}, 현재상태={}",
                    event.orderId(), order.getStatus());
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            // ③ 이상 상태 → throw → 재시도 → DLT
            throw new IllegalStateException(
                "Invalid transition: " + order.getStatus() + " -> FAILED, orderId=" + event.orderId());
        }

        // Step 3. 비즈니스 로직
        order.failPayment();

        // Step 4. Dedup 기록 (같은 트랜잭션)
        deduplicationService.markProcessed(messageId, topic);
    }

    private <T> T deserialize(String payload, Class<T> clazz) {
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Kafka 메시지 역직렬화 실패: " + clazz.getSimpleName(), e);
        }
    }

    /**
     * stock.deducted 수신 처리: Order CREATED → PAYMENT_PENDING 전이
     * <p>
     * 처리 순서 : isDuplicate → canTransitionTo(3분류) → pendingPayment() → markProcessed
     */
    @Transactional
    public void processStockDeducted(UUID messageId, String topic, String payload) {
        // Step 1. Dedup 체크
        if (deduplicationService.isDuplicate(messageId)) {
            log.debug("[stock.deducted] 중복 메시지 스킵. messageId={}", messageId);
            return;
        }
    }
    // ==== Private Helpers (Order Creation) ==============================

    private static final String ACTIVE_ORDER_DEDUP_INDEX = "idx_order_active_dedup";

    private boolean isDuplicateActiveOrderViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException cve) {
            String name = cve.getConstraintName();
            return name != null && name.contains(ACTIVE_ORDER_DEDUP_INDEX);
        }
        return false;
    }

    private PrepareResult prepareAndValidate(UUID userId, CartOrderRequest request) {
        // 1. Cart 조회 — cartId 확보 (소유자 검증용)
        Cart cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.CART_NOT_FOUND));

        // 2. 중복 ID 검사 — DB 조회 전에 먼저 차단
        if (request.cartItemIds().size() != new HashSet<>(request.cartItemIds()).size()) {
            throw new BusinessException(CartErrorCode.DUPLICATE_CART_ITEM_ID);
        }

        // 3. 장바구니 아이템 조회 — 비관적 락으로 동시 요청 직렬화, ORDER BY로 데드락 방지
        List<CartItem> cartItems = cartItemRepository.findAllByCartItemIdWithLock(request.cartItemIds());

        if (cartItems.size() != request.cartItemIds().size()) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }

        boolean allBelongToCart = cartItems.stream()
            .allMatch(item -> item.getCartId().equals(cart.getId()));
        if (!allBelongToCart) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }

        // 4. cartHash 계산
        String cartHash = CartHashUtil.compute(cartItems);

        // 5. 활성 주문 dedup 체크 — adjustStocks 이전에 수행하여 재시도 시 재차감 방지
        List<OrderStatus> activeStatuses = List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);
        Optional<Order> existingOrder = orderRepository.findActiveOrder(userId, cartHash, activeStatuses);
        if (existingOrder.isPresent()) {
            Order active = existingOrder.get();
            List<OrderItem> existingItems = orderItemRepository.findAllByOrderId(active.getId());
            List<UUID> existingEventIds = existingItems.stream().map(OrderItem::getEventId).distinct().toList();
            Map<UUID, String> existingEventTitles = orderToEventClient.getBulkEventInfo(existingEventIds).stream()
                .collect(Collectors.toMap(InternalEventInfoResponse::eventId, InternalEventInfoResponse::title));
            return new PrepareResult(userId, cartItems, List.of(), 0, cartHash,
                active, existingItems, existingEventTitles);
        }

        // 6. 이벤트 정보 조회 (가격·maxQuantity·제목)
        List<UUID> eventIds = cartItems.stream().map(CartItem::getEventId).distinct().toList();
        List<InternalEventInfoResponse> eventInfos = orderToEventClient.getBulkEventInfo(eventIds);

        // 7. 총 주문 금액 계산
        int totalAmount = calculateTotalAmount(cartItems, eventInfos);

        return new PrepareResult(userId, cartItems, eventInfos, totalAmount, cartHash, null, List.of(), Map.of());
    }

    private void adjustStocksOrThrow(List<CartItem> cartItems) {
        orderToEventClient.adjustStocks(InternalBulkStockAdjustmentRequest.createForOrder(cartItems));
    }

    private void compensateStock(List<CartItem> cartItems) {
        try {
            orderToEventClient.adjustStocks(InternalBulkStockAdjustmentRequest.createForCancel(cartItems));
        } catch (Exception e) {
            log.error("[createOrderByCart] 재고 보상 실패 — 수동 복구 필요. error={}", e.getMessage(), e);
        }
    }

    private OrderResponse persistOrder(PrepareResult p) {
        Order order = Order.createPending(p.userId(), p.totalAmount(), p.cartHash());
        order = orderRepository.save(order);

        List<OrderItem> savedOrderItems = createOrderItem(order.getId(), p.userId(), p.cartItems(), p.eventInfos());

        Map<UUID, String> eventTitles = p.eventInfos().stream()
            .collect(Collectors.toMap(InternalEventInfoResponse::eventId, InternalEventInfoResponse::title));

        return OrderResponse.of(order, savedOrderItems, eventTitles);
    }

    // ==== Private Helpers (Logic & Validation) ==========================

    private int calculateTotalAmount(List<CartItem> cartItems, List<InternalEventInfoResponse> eventInfos) {
        Map<UUID, Integer> priceMap = eventInfos.stream()
            .collect(Collectors.toMap(InternalEventInfoResponse::eventId, InternalEventInfoResponse::price));

        return cartItems.stream()
            .mapToInt(cartItem -> {
                Integer currentPrice = priceMap.get(cartItem.getEventId());
                if (currentPrice == null) {
                    throw new BusinessException(EventErrorCode.EVENT_NOT_FOUND);
                }
                return currentPrice * cartItem.getQuantity();
            })
            .sum();
    }

    private List<OrderItem> createOrderItem(Long orderId, UUID userId, List<CartItem> cartItems,
        List<InternalEventInfoResponse> eventInfos) {

        Map<UUID, InternalEventInfoResponse> eventMap = eventInfos.stream()
            .collect(Collectors.toMap(InternalEventInfoResponse::eventId, r -> r));

        List<OrderItem> orderItems = cartItems.stream()
            .map(cartItem -> {
                InternalEventInfoResponse detail = eventMap.get(cartItem.getEventId());
                if (detail == null) {
                    throw new BusinessException(EventErrorCode.EVENT_NOT_FOUND);
                }
                return OrderItem.create(
                    orderId,
                    userId,
                    cartItem.getEventId(),
                    detail.price(),
                    cartItem.getQuantity(),
                    detail.maxQuantity()
                );
            })
            .toList();

        return orderItemRepository.saveAll(orderItems);
    }


    @Override
    @Transactional(readOnly = true)
    public InternalOrderItemResponse getOrderItemByTicketId(UUID ticketId) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
            .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));

        OrderItem orderItem = orderItemRepository.findByOrderItemId(ticket.getOrderItemId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        Order order = orderRepository.findById(orderItem.getOrderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        return InternalOrderItemResponse.from(orderItem, order.getOrderId());
    }

    @Override
    @Transactional(readOnly = true)
    public com.devticket.commerce.order.presentation.dto.res.InternalOrderTicketsResponse
    getOrderTickets(UUID orderId, com.devticket.commerce.ticket.domain.enums.TicketStatus status) {

        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        List<Ticket> tickets = (status == null)
            ? ticketRepository.findAllByOrderId(order.getId())
            : ticketRepository.findAllByOrderIdAndStatus(order.getId(), status);

        // price 조회용 OrderItem map — orderItemId → OrderItem
        Map<UUID, OrderItem> itemsByOrderItemId = orderItemRepository.findAllByOrderId(order.getId())
            .stream()
            .collect(Collectors.toMap(OrderItem::getOrderItemId, oi -> oi));

        List<com.devticket.commerce.order.presentation.dto.res.InternalOrderTicketsResponse.TicketItem> ticketItems = tickets.stream()
            .map(t -> new com.devticket.commerce.order.presentation.dto.res.InternalOrderTicketsResponse.TicketItem(
                t.getTicketId(),
                t.getEventId(),
                itemsByOrderItemId.getOrDefault(t.getOrderItemId(),
                    OrderItem.builder().price(0).build()).getPrice(),
                t.getStatus()
            ))
            .toList();

        int remainingAmount = ticketItems.stream()
            .mapToInt(com.devticket.commerce.order.presentation.dto.res.InternalOrderTicketsResponse.TicketItem::amount)
            .sum();

        return new com.devticket.commerce.order.presentation.dto.res.InternalOrderTicketsResponse(
            order.getOrderId(),
            order.getUserId(),
            order.getPaymentId(),
            order.getTotalAmount(),
            remainingAmount,
            ticketItems
        );
    }

}


