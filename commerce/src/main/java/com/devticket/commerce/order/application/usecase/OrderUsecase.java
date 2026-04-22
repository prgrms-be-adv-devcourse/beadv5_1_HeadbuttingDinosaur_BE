package com.devticket.commerce.order.application.usecase;

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
import java.util.UUID;

public interface OrderUsecase {

    //주문하기_장바구니에서 단건,다건 주문
    OrderResponse createOrderByCart(UUID userId, CartOrderRequest request);

    //주문 목록 조회
    OrderListResponse getOrderList(UUID userId, OrderListRequest request);

    //주문 상세 조회
    OrderDetailResponse getOrderDetail(UUID userId, UUID orderId);

    //주문 상태 폴링
    OrderStatusResponse getOrderStatus(UUID userId, UUID orderId);

    InternalOrderInfoResponse getOrderInfo(UUID orderId);

    InternalOrderItemsResponse getOrderListForSettlement(Long id);

    void completeOrder(UUID orderId);

    void failOrder(UUID orderId);

    InternalSettlementDataResponse getSettelmentData(UUID sellerId, String periodStart, String periodEnd);

    InternalOrderItemResponse getOrderItemByTicketId(Long ticketId);

    void completeRefund(Long ticketId);

    //InternalEventOrdersResponse getOrdersByEvent(Long eventId, String status);

    // 결제 전 주문 취소
    OrderCancelResponse cancelOrder(UUID userId, UUID orderId);

    // Kafka Consumer 처리용
    void processPaymentCompleted(UUID messageId, String topic, String payload);

    void processPaymentFailed(UUID messageId, String topic, String payload);

}

