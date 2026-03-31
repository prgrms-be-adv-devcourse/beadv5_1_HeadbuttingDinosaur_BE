package com.devticket.commerce.order.application.usecase;

import com.devticket.commerce.mock.controller.dto.InternalOrderInfoResponse;
import com.devticket.commerce.mock.controller.dto.InternalOrderItemsResponse;
import com.devticket.commerce.order.presentation.dto.req.CartOrderRequest;
import com.devticket.commerce.order.presentation.dto.res.InternalOrderItemResponse;
import com.devticket.commerce.order.presentation.dto.res.InternalSettlementDataResponse;
import com.devticket.commerce.order.presentation.dto.res.OrderResponse;
import java.util.UUID;

public interface OrderUsecase {

    //주문하기_장바구니에서 단건,다건 주문
    OrderResponse createOrderByCart(UUID userId, CartOrderRequest request);

    InternalOrderInfoResponse getOrderInfo(Long id);

    InternalOrderItemsResponse getOrderListForSettlement(Long id);

    void completeOrder(UUID orderId);

    InternalSettlementDataResponse getSettelmentData(UUID sellerId, String periodStart, String periodEnd);

    InternalOrderItemResponse getOrderItemByTicketId(Long ticketId);

    //InternalEventOrdersResponse getOrdersByEvent(Long eventId, String status);
}

