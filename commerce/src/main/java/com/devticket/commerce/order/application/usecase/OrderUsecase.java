package com.devticket.commerce.order.application.usecase;

import com.devticket.commerce.order.presentation.dto.req.CartOrderRequest;
import com.devticket.commerce.order.presentation.dto.res.OrderResponse;
import java.util.UUID;

public interface OrderUsecase {

    //주문하기_장바구니에서 단건,다건 주문
    OrderResponse createOrderByCart(UUID userId, CartOrderRequest request);


}
