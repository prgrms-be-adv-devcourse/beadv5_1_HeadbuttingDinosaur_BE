package com.devticket.commerce.cart.application.usecase;

import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartResponse;
import java.util.UUID;

public interface CartUseCase {

    //사용자의 장바구니 유무 확인
    boolean findByUserId(UUID userId);

    //장바구니 생성
    CartItemResponse save(UUID userId, CartItemRequest request);

    // 장바구니 조회
    CartResponse getCart(UUID userId);
}
