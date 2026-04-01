package com.devticket.commerce.order.application.service;

import com.devticket.commerce.cart.domain.exception.EventErrorCode;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.order.application.usecase.OrderUsecase;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.order.infrastructure.external.client.OrderToEventClient;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalBulkStockAdjustmentRequest;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalStockAdjustmentResponse;
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
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.exception.TicketErrorCode;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TicketUsecase ticketUsecase;
    private final TicketRepository ticketRepository;

    // ==== Public Methods (Main Flow) ====================================

    // 장바구니에서 상품선택 후 주문하기 진행
    @Transactional
    @Override
    public OrderResponse createOrderByCart(UUID userId, CartOrderRequest request) {
        //장바구니 아이템 조회
        List<CartItem> cartItems = cartItemRepository.findAllByCartItemId(request.cartItemIds());
        //재고 선차감 : Event API호출
        List<InternalStockAdjustmentResponse> eventResults = orderToEventClient.adjustStocks(
            InternalBulkStockAdjustmentRequest.createForOrder(cartItems));

        try {
            //총 주문 금액 계산
            int totalAmount = calculateTotalAmount(cartItems, eventResults);
            //재고 선차감 성공시 Order생성
            Order order = Order.create(userId, totalAmount);
            orderRepository.save(order);
            //OrderItem생성
            List<OrderItem> savedOrderItems = createOrderItem(order.getId(), userId, cartItems, eventResults);
            //주문완료건 장바구니에서 삭제처리
            if (!cartItems.isEmpty()) {
                cartItemRepository.deleteAllInBatch(cartItems);
            }
            //응답데이터 변환
            Map<UUID, String> eventTitles = eventResults.stream()
                .collect(Collectors.toMap(
                    InternalStockAdjustmentResponse::eventId,
                    InternalStockAdjustmentResponse::eventTitle
                ));
            return OrderResponse.of(order, savedOrderItems, eventTitles);

        } catch (Exception e) {
            //주문 생성 실패시 선차감한 재고를 원복하는 api호출
            List<InternalStockAdjustmentResponse> rollbackEventResults = orderToEventClient.adjustStocks(
                InternalBulkStockAdjustmentRequest.createForCancel(cartItems));
            throw new BusinessException(OrderErrorCode.ORDER_CREATION_FAILED);
        }
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
    public void failOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
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

    // ==== Private Helpers (Logic & Validation) ==========================

    private int calculateTotalAmount(List<CartItem> cartItems, List<InternalStockAdjustmentResponse> eventItems) {
        int totalAmount = 0;
        Map<UUID, Integer> priceMap = eventItems.stream()
            .collect(
                Collectors.toMap(InternalStockAdjustmentResponse::eventId, InternalStockAdjustmentResponse::price));

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
        List<InternalStockAdjustmentResponse> eventItems) {

        Map<UUID, InternalStockAdjustmentResponse> eventMap = eventItems.stream()
            .collect(Collectors.toMap(InternalStockAdjustmentResponse::eventId, r -> r));

        List<OrderItem> orderItems = cartItems.stream()
            .map(cartItem -> {
                InternalStockAdjustmentResponse detail = eventMap.get(cartItem.getEventId());

                if (detail == null) {
                    throw new BusinessException(EventErrorCode.EVENT_NOT_FOUND);
                }

                return OrderItem.create(
                    orderId,         // 발급된 부모 ID 주입
                    userId,                // 유저 식별값
                    cartItem.getEventId(), // 이벤트 ID
                    detail.price(),        // 최신 가격 스냅샷
                    cartItem.getQuantity(),// 주문 수량
                    detail.maxQuantity()   // 인당 제한 수량 검증용
                );
            })
            .toList();

        return orderItemRepository.saveAll(orderItems);
    }


    @Override
    @Transactional(readOnly = true)
    public InternalOrderItemResponse getOrderItemByTicketId(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));

        OrderItem orderItem = orderItemRepository.findByOrderItemId(ticket.getOrderItemId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        return InternalOrderItemResponse.from(orderItem);
    }

    @Override
    @Transactional
    public void completeRefund(Long ticketId) {
        // 1. 티켓 조회 후 REFUNDED 상태 변경
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));

        if (ticket.getStatus() == TicketStatus.REFUNDED) {
            log.info("이미 환불 처리된 티켓입니다. ticketId: {}", ticketId);
            return;
        }

        ticket.refundTicket();

        // 2. OrderItem 수량 -1, subtotalAmount 재계산
        OrderItem orderItem = orderItemRepository.findByOrderItemId(ticket.getOrderItemId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        orderItem.refundOneQuantity();

        // 3. Order totalAmount에서 환불 금액(price * 1) 차감
        Order order = orderRepository.findById(orderItem.getOrderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        order.adjustAmountForRefund(orderItem.getPrice());

        // 4. Event 재고 +1 복구
        orderToEventClient.adjustStocks(
            new InternalBulkStockAdjustmentRequest(
                List.of(new InternalBulkStockAdjustmentRequest.EventItem(orderItem.getEventId(), 1))
            )
        );
    }

}


