package com.devticket.commerce.order.presentation.controller;


import com.devticket.commerce.order.application.usecase.OrderUsecase;
import com.devticket.commerce.order.presentation.dto.res.InternalOrderInfoResponse;
import com.devticket.commerce.order.presentation.dto.res.InternalOrderItemResponse;
import com.devticket.commerce.order.presentation.dto.res.InternalOrderItemsResponse;
import com.devticket.commerce.order.presentation.dto.res.InternalSettlementDataResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<InternalOrderInfoResponse> getOrderInfo(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderUsecase.getOrderInfo(orderId));
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

    //Payment -> Commerce : 결제 실패 후 Order상태 FAILED로 변경
    @PatchMapping("/orders/{orderId}/payment-failed")
    public ResponseEntity<Void> failOrder(@PathVariable Long orderId) {
        orderUsecase.failOrder(orderId);
        return ResponseEntity.ok().build();
    }

    //Ticket -> Commerce : ticketId(PK)로 해당 OrderItem 전체 정보 조회
    @GetMapping("/order-items/by-ticket/{ticketId}")
    public ResponseEntity<InternalOrderItemResponse> getOrderItemByTicketId(@PathVariable Long ticketId) {
        InternalOrderItemResponse response = orderUsecase.getOrderItemByTicketId(ticketId);
        return ResponseEntity.ok(response);
    }

    //Refund -> Commerce : 환불 완료 후 ticket.status REFUNDED 변경 + orderItem.deletedAt 기록
    @PatchMapping("/tickets/{ticketId}/refund-completed")
    public ResponseEntity<Void> completeRefund(@PathVariable Long ticketId) {
        orderUsecase.completeRefund(ticketId);
        return ResponseEntity.ok().build();
    }

}
