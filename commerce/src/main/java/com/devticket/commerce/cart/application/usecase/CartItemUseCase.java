package com.devticket.commerce.cart.application.usecase;

import com.devticket.commerce.cart.presentation.dto.req.CartItemQuantityRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemDeleteResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartItemQuantityResponse;
import java.util.UUID;

public interface CartItemUseCase {

    // 장바구니 티켓 수량 변경
    CartItemQuantityResponse updateTicket(UUID userId, UUID cartItemId, CartItemQuantityRequest request);

    CartItemDeleteResponse deleteTicket(UUID userId, UUID cartItemId);

}
