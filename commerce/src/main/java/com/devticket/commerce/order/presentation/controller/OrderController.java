package com.devticket.commerce.order.presentation.controller;

import com.devticket.commerce.order.application.usecase.OrderUsecase;
import com.devticket.commerce.order.presentation.dto.req.CartOrderRequest;

import com.devticket.commerce.order.presentation.dto.res.OrderCancelResponse;

import com.devticket.commerce.order.presentation.dto.req.OrderListRequest;
import com.devticket.commerce.order.presentation.dto.res.OrderDetailResponse;
import com.devticket.commerce.order.presentation.dto.res.OrderListResponse;

import com.devticket.commerce.order.presentation.dto.res.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PatchMapping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Order API", description = "주문서 생성,수정,조회,삭제")
public class OrderController {

    private final OrderUsecase orderUsecase;

    @PostMapping
    @Operation(description = "주문하기 : 장바구니에 저장된 상품 단건,다건 주문하기")
    public ResponseEntity<OrderResponse> createOrderByCart(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestBody CartOrderRequest request
    ) {
        OrderResponse response = orderUsecase.createOrderByCart(userId, request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @GetMapping
    @Operation(description = "주문 목록 조회 : 내 주문 목록을 페이징으로 조회 (status 파라미터로 필터링 가능)")
    public ResponseEntity<OrderListResponse> getOrderList(
        @RequestHeader("X-User-Id") UUID userId,
        @ModelAttribute OrderListRequest request
    ) {
        OrderListResponse response = orderUsecase.getOrderList(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    @Operation(description = "주문 상세 조회 : 특정 주문의 상세 정보 조회")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID orderId
    ) {
        OrderDetailResponse response = orderUsecase.getOrderDetail(userId, orderId);
        return ResponseEntity.ok(response);
    }
    
    @Operation(description = "결제 전 주문 취소")
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderCancelResponse> cancelOrder(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID orderId
    ) {
        OrderCancelResponse response = orderUsecase.cancelOrder(userId, orderId);
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(response);
    }

}
