package com.devticket.commerce.order.application.service;

import com.devticket.commerce.cart.domain.exception.EventErrorCode;
import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.cart.domain.repository.CartItemRepository;
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
import com.devticket.commerce.order.presentation.dto.res.OrderResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class OrderService implements OrderUsecase {

    private final OrderToEventClient orderToEventClient;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    // ==== Public Methods (Main Flow) ====================================

    // 장바구니에서 상품선택 후 주문하기 진행
    @Transactional
    @Override
    public OrderResponse createOrderByCart(UUID userId, CartOrderRequest request) {
        //장바구니 아이템 조회
        List<CartItem> cartItems = cartItemRepository.findAllById(request.cartItemIds());
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
            Map<Long, String> eventTitles = eventResults.stream()
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

    // ==== Private Helpers (Logic & Validation) ==========================

    private int calculateTotalAmount(List<CartItem> cartItems, List<InternalStockAdjustmentResponse> eventItems) {
        int totalAmount = 0;
        Map<Long, Integer> priceMap = eventItems.stream()
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

        Map<Long, InternalStockAdjustmentResponse> eventMap = eventItems.stream()
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


}


