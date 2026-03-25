package com.devticket.commerce.cart.application.usecase;

import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;

public interface CartItemUseCase {

    CartItemResponse save(Long userId, CartItemRequest request);
}
