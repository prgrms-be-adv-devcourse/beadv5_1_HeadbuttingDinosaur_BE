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
import com.devticket.commerce.common.messaging.event.StockDeductedEvent;
import com.devticket.commerce.common.messaging.event.StockFailedEvent;
import com.devticket.commerce.common.messaging.event.TicketIssueFailedEvent;
import com.devticket.commerce.common.outbox.OutboxService;
import com.devticket.commerce.order.application.usecase.OrderUsecase;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
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
import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.domain.exception.TicketErrorCode;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // ==== Public Methods (Main Flow) ====================================

    // 장바구니에서 상품선택 후 주문하기 진행
    @Transactional
    @Override
    public OrderResponse createOrderByCart(UUID userId, CartOrderRequest request) {
        // 1. Cart 조회 — cartId 확보 (소유자 검증용)
        Cart cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.CART_NOT_FOUND));

        // 2-0. 중복 ID 검사 — DB 조회 전에 먼저 차단
        if (request.cartItemIds().size() != new HashSet<>(request.cartItemIds()).size()) {
            throw new BusinessException(CartErrorCode.DUPLICATE_CART_ITEM_ID);
        }

        // 2. 장바구니 아이템 조회 — 비관적 락으로 주문 처리 중 CartItem 변경 차단 및 동시 요청 직렬화
        // ORDER BY cartItemId로 락 순서 고정 — 부분 겹침 요청 간 데드락 방지
        List<CartItem> cartItems = cartItemRepository.findAllByCartItemIdWithLock(request.cartItemIds());

        // 2-1. 개수 불일치 검사 — 존재하지 않거나 삭제된 ID 방어
        if (cartItems.size() != request.cartItemIds().size()) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }

        // 2-2. 소유자 검증 — 다른 유저의 CartItem 방어
        boolean allBelongToCart = cartItems.stream()
            .allMatch(item -> item.getCartId().equals(cart.getId()));
        if (!allBelongToCart) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }

        // 3. cartHash 계산 — (eventId, quantity) 기준 서버 사이드 계산
        String cartHash = CartHashUtil.compute(cartItems);

        // 4. 활성 주문 중복 체크 — 같은 장바구니 내용의 처리 중 주문이 있으면 기존 주문 반환
        List<OrderStatus> activeStatuses = List.of(
            OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING
        );
        Optional<Order> existingOrder = orderRepository.findActiveOrder(userId, cartHash, activeStatuses);
        if (existingOrder.isPresent()) {
            Order active = existingOrder.get();
            List<OrderItem> existingItems = orderItemRepository.findAllByOrderId(active.getId());
            List<UUID> existingEventIds = existingItems.stream().map(OrderItem::getEventId).distinct().toList();
            Map<UUID, String> existingEventTitles = orderToEventClient.getBulkEventInfo(existingEventIds).stream()
                .collect(Collectors.toMap(InternalEventInfoResponse::eventId, InternalEventInfoResponse::title));
            return OrderResponse.of(active, existingItems, existingEventTitles);
        }

        // 5. 이벤트 정보 조회 (가격·maxQuantity·제목) — 읽기 전용, 재고 차감 없음
        List<UUID> eventIds = cartItems.stream().map(CartItem::getEventId).distinct().toList();
        List<InternalEventInfoResponse> eventInfos = orderToEventClient.getBulkEventInfo(eventIds);

        // 6. 총 주문 금액 계산
        int totalAmount = calculateTotalAmount(cartItems, eventInfos);

        // 7. Order 생성 (CREATED — 재고 확인 대기 중)
        Order order = Order.create(userId, totalAmount, cartHash);
        orderRepository.save(order);

        // 8. OrderItem 생성
        List<OrderItem> savedOrderItems = createOrderItem(order.getId(), userId, cartItems, eventInfos);

        // 9. order.created 발행 (Outbox) — Event 서비스가 수신하여 재고 차감 처리
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getOrderId(),
            userId,
            savedOrderItems.stream()
                .map(item -> new OrderCreatedEvent.OrderItem(item.getEventId(), item.getQuantity()))
                .toList(),
            totalAmount,
            Instant.now()
        );
        outboxService.save(
            order.getOrderId().toString(),
            order.getOrderId().toString(),
            "OrderCreated",
            KafkaTopics.ORDER_CREATED,
            event
        );

        // 10. 응답 반환
        Map<UUID, String> eventTitles = eventInfos.stream()
            .collect(Collectors.toMap(InternalEventInfoResponse::eventId, InternalEventInfoResponse::title));

        return OrderResponse.of(order, savedOrderItems, eventTitles);
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
        OrderStatus status = (request.status() != null && !request.status().isBlank())
            ? OrderStatus.valueOf(request.status())
            : null;

        PageRequest pageable = PageRequest.of(request.page() - 1, request.size(), Sort.by("id").descending());
        Page<Order> orderPage = orderRepository.findAllByUserId(userId, status, pageable);

        return OrderListResponse.of(orderPage);
    }

    @Override
    public InternalOrderInfoResponse getOrderInfo(UUID orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return InternalOrderInfoResponse.from(order);
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

                        if (OrderStatus.PAID.equals(order.getStatus())) {
                            totalSales += item.getPrice() * item.getQuantity();
                            soldQty += item.getQuantity();
                        } else if (OrderStatus.CANCELLED.equals(order.getStatus())) {
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
    public OrderCancelResponse cancelOrder(UUID userId, UUID orderId) {
        // 1. 주문 정보 확인
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        // 2. 주문자 검증
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
        }
        // 3. 결제 상태 체크
        if (order.getStatus().equals(OrderStatus.PAID)) {
            throw new BusinessException(OrderErrorCode.ALREADY_PAID_ORDER);
        }
        // 4. 주문 아이템 조회
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
        // 5. 재고 복구
        orderToEventClient.adjustStocks(InternalBulkStockAdjustmentRequest.createForCancelByOrderItems(orderItems));
        // 6. 주문 취소
        order.cancel();

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

        // 장바구니 비우기 (카트가 없으면 무시)
        Optional<Cart> cart = cartRepository.findByUserId(event.userId());
        cart.ifPresent(c -> {
            List<CartItem> cartItems = cartItemRepository.findAllByCartId(c.getId());
            if (!cartItems.isEmpty()) {
                cartItemRepository.deleteAllInBatch(cartItems);
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

        StockDeductedEvent event = parsePayload(payload, StockDeductedEvent.class);

        Order order = orderRepository.findByOrderId(event.orderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        // Step 2. 상태 전이 유효성 검증 (3분류)
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            // 1. 멱등 스킵: 이미 목표 상태
            log.info("[stock.deducted] 멱등 스킵 — 이미 PAYMENT_PENDING. orderId={}", event.orderId());
            deduplicationService.markProcessed(messageId, topic);
            return;
        }
        if (!order.canTransitionTo(OrderStatus.PAYMENT_PENDING)) {
            if (isExplainableSkipForStock(order.getStatus())) {
                // 2. 정책적 스킵: 만료 스케줄러 또는 사용자 취소로 이미 종단 상태
                log.warn("[stock.deducted] 정책적 스킵 — 주문 상태={}. orderId={}",
                    order.getStatus(), event.orderId());
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            // 3. 이상 상태: 설명 불가능 → throw → 재시도 → DLT
            log.error("[stock.deducted] 이상 상태 — {} → PAYMENT_PENDING 불가. orderId={}",
                order.getStatus(), event.orderId());
            throw new IllegalStateException(String.format(
                "[stock.deducted] 허용되지 않는 상태 전이: %s → PAYMENT_PENDING, orderId=%s",
                order.getStatus(), event.orderId()));
        }

        // Step 3. 비즈니스 로직: CREATED → PAYMENT_PENDING
        order.pendingPayment();

        // Step 4. Dedup 기록 저장 (같은 트랜잭션)
        deduplicationService.markProcessed(messageId, topic);
    }

    /**
     * stock.failed 수신 처리: Order CREATED → FAILED 전이 (재고 부족 보상)
     *
     * <p>처리 순서 (kafka-idempotency-guide.md §4):
     * isDuplicate → canTransitionTo(3분류) → failByStock() → markProcessed
     */
    @Transactional
    public void processStockFailed(UUID messageId, String topic, String payload) {
        // Step 1. Dedup 체크
        if (deduplicationService.isDuplicate(messageId)) {
            log.debug("[stock.failed] 중복 메시지 스킵. messageId={}", messageId);
            return;
        }

        StockFailedEvent event = parsePayload(payload, StockFailedEvent.class);

        Order order = orderRepository.findByOrderId(event.orderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        // Step 2. 상태 전이 유효성 검증 (3분류)
        if (order.getStatus() == OrderStatus.FAILED) {
            // ① 멱등 스킵: 이미 목표 상태
            log.info("[stock.failed] 멱등 스킵 — 이미 FAILED. orderId={}", event.orderId());
            deduplicationService.markProcessed(messageId, topic);
            return;
        }
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            // ③ 이상 상태: stock.deducted 처리 완료 후 stock.failed 재도달
            // canTransitionTo(FAILED)가 PAYMENT_PENDING에서도 true를 반환하므로 명시적으로 차단
            log.error("[stock.failed] 이상 상태 — stock.deducted 이미 처리됨(PAYMENT_PENDING)에서 stock.failed 재도달. orderId={}",
                event.orderId());
            throw new IllegalStateException(String.format(
                "[stock.failed] PAYMENT_PENDING 상태에서 재고 실패 이벤트 수신 불가. orderId=%s", event.orderId()));
        }
        if (!order.canTransitionTo(OrderStatus.FAILED)) {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                // ② 정책적 스킵: 재고 결과 도착 전에 사용자가 먼저 취소
                log.warn("[stock.failed] 정책적 스킵 — 주문 이미 CANCELLED. orderId={}", event.orderId());
                deduplicationService.markProcessed(messageId, topic);
                return;
            }
            // ③ 이상 상태: 설명 불가능 → throw → 재시도 → DLT
            log.error("[stock.failed] 이상 상태 — {} → FAILED 불가. reason={}, orderId={}",
                order.getStatus(), event.reason(), event.orderId());
            throw new IllegalStateException(String.format(
                "[stock.failed] 허용되지 않는 상태 전이: %s → FAILED, orderId=%s",
                order.getStatus(), event.orderId()));
        }

        // Step 3. 비즈니스 로직: CREATED → FAILED
        order.failByStock();
        log.info("[stock.failed] Order FAILED 전이 완료. reason={}, orderId={}",
            event.reason(), event.orderId());

        // Step 4. Dedup 기록 저장 (같은 트랜잭션)
        deduplicationService.markProcessed(messageId, topic);
    }

    // ==== Private Helpers (Logic & Validation) ==========================

    /**
     * stock 이벤트 수신 시 정책적 스킵 가능한 상태 — 만료 스케줄러 또는 사용자 취소로 이미 종단 도달
     */
    private boolean isExplainableSkipForStock(OrderStatus current) {
        return current == OrderStatus.CANCELLED || current == OrderStatus.FAILED;
    }


    private <T> T parsePayload(String payload, Class<T> clazz) {
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Kafka 페이로드 역직렬화 실패: " + e.getMessage(), e);
        }
    }

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


