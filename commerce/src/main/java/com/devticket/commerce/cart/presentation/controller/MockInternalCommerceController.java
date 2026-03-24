package com.devticket.commerce.cart.presentation.controller;

import com.devticket.commerce.cart.presentation.dto.res.InternalEventOrdersResponse;
import com.devticket.commerce.cart.presentation.dto.res.InternalOrderInfoResponse;
import com.devticket.commerce.cart.presentation.dto.res.InternalOrderItemsResponse;
import com.devticket.commerce.cart.presentation.dto.res.InternalSettlementDataResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal API - Event", description = "Commerce 내부 시스템 연동용 Mock API")
@RequestMapping("/internal")
public class MockInternalCommerceController {

    @GetMapping("/orders/{orderId}")
    public InternalOrderInfoResponse getOrderInfo(@PathVariable Long orderId) {
        return new InternalOrderInfoResponse(10L, 20L, "order-123", "WALLET", 30000, "CREATED", "2025-08-15T14:30:00");
    }


    @GetMapping("/orders/{orderId}/items")
    public InternalOrderItemsResponse getOrderListForSettlement(@PathVariable Long orderId) {
        List<InternalOrderItemsResponse.Item> items = List.of(
            new InternalOrderItemsResponse.Item(1L, 15L, 30000, 1, 30000),
            new InternalOrderItemsResponse.Item(2L, 22L, 50000, 2, 100000));

        return new InternalOrderItemsResponse(orderId, items);
    }

    @GetMapping("/orders/by-event/{eventId}")
    public InternalEventOrdersResponse getOrdersByEvent(@PathVariable Long eventId, @RequestParam String status) {
        List<InternalEventOrdersResponse.Orders> orders = List.of(
            new InternalEventOrdersResponse.Orders(1L, 101L, "ORD-20240324-001", "CARD", 130000, status,
                "2026-03-24T10:15:30"),
            new InternalEventOrdersResponse.Orders(2L, 102L, "ORD-20240324-002", "KAKAO_PAY", 50000, status,
                "2026-03-24T11:20:00"));

        return new InternalEventOrdersResponse(eventId, orders);
    }

    @GetMapping("/internal/orders/settlement-data")
    public InternalSettlementDataResponse getSettlementData(@RequestParam Long sellerId,
        @RequestParam String periodStart, @RequestParam String periodEnd) {
        List<InternalSettlementDataResponse.EventSettlements.OrderItems> orderItems = List.of(
            new InternalSettlementDataResponse.EventSettlements.OrderItems(1L, 15L, 30000, 2, 60000),
            new InternalSettlementDataResponse.EventSettlements.OrderItems(2L, 15L, 50000, 1, 50000));

        List<InternalSettlementDataResponse.EventSettlements> eventSettlements = List.of(
            new InternalSettlementDataResponse.EventSettlements(15L, 110000, 10000, 3, 1, orderItems));

        return new InternalSettlementDataResponse(sellerId, periodStart, periodEnd, eventSettlements);
    }


}
