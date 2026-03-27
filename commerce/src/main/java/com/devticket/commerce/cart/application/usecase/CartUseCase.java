package com.devticket.commerce.cart.application.usecase;

import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;

public interface CartUseCase {

    //사용자의 장바구니 유무 확인
    boolean findByUserId(Long userId);

    //장바구니 생성
    CartItemResponse save(Long userId, CartItemRequest request);


}
