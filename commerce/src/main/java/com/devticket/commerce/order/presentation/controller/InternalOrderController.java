package com.devticket.commerce.order.presentation.controller;

import com.devticket.commerce.mock.controller.dto.InternalOrderInfoResponse;
import com.devticket.commerce.mock.controller.dto.InternalOrderItemsResponse;
import com.devticket.commerce.order.application.usecase.OrderUsecase;
import com.devticket.commerce.order.presentation.dto.res.InternalOrderItemResponse;
import com.devticket.commerce.order.presentation.dto.res.InternalSettlementDataResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Order Internal API")
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderUsecase orderUsecase;

    //Payment -> Commerce : 결제요청시 주문정보 조회
    @GetMapping("/orders/{id}")
    public ResponseEntity<InternalOrderInfoResponse> getOrderInfo(@PathVariable Long id) {
        InternalOrderInfoResponse response = orderUsecase.getOrderInfo(id);
        return ResponseEntity.ok(response);
    }

    //Settlement -> Commerce : 정산집계시 주문 항목 조회
    @GetMapping("/orders/{id}/items")
    public InternalOrderItemsResponse getOrderListForSettlement(@PathVariable Long id) {
        InternalOrderItemsResponse response = orderUsecase.getOrderListForSettlement(id);
        return ResponseEntity.ok(response).getBody();
    }

    //Payment,Settlement -> Commerce : 이벤트별 결제 완료 주문 조회(이벤트 취소시 일괄 환불 대상)
//    @GetMapping("/orders/by-event/{eventId}")
//    public InternalEventOrdersResponse getOrdersByEvent(@PathVariable Long eventId, @RequestParam String status) {
//        InternalEventOrdersResponse response = orderUsecase.getOrdersByEvent(eventId, status);
//        return ResponseEntity.ok(response).getBody();
//    }

    //Settelement -> Commerce : 기간별 정산 대상 데이터 조회
    @GetMapping("/orders/settlement-data")
    public ResponseEntity<InternalSettlementDataResponse> getSettlementData(@RequestParam UUID sellerId,
        @RequestParam String periodStart, @RequestParam String periodEnd) {

        InternalSettlementDataResponse response = orderUsecase.getSettelmentData(sellerId, periodStart, periodEnd);
        return ResponseEntity.ok(response);
    }

    //Payment -> Commerce : 결제 완료 후 Order상태 PAID로 변경, Ticket발행
    @PostMapping("/orders/{orderId}/payment-completed")
    public ResponseEntity<Void> completeOrder(@PathVariable UUID orderId) {
        orderUsecase.completeOrder(orderId);
        return ResponseEntity.ok().build();
    }

    //Ticket -> Commerce : ticketId(PK)로 해당 OrderItem 전체 정보 조회
    @GetMapping("/order-items/by-ticket/{ticketId}")
    public ResponseEntity<InternalOrderItemResponse> getOrderItemByTicketId(@PathVariable Long ticketId) {
        InternalOrderItemResponse response = orderUsecase.getOrderItemByTicketId(ticketId);
        return ResponseEntity.ok(response);
    }

}
