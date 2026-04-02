package com.devticket.commerce.cart.presentation.dto.res;

import com.devticket.commerce.cart.domain.model.CartItem;
import lombok.Builder;

@Builder
public record CartItemQuantityResponse(

    String cartItemId,
    int quantity

) {

    public static CartItemQuantityResponse of(CartItem cartItem) {
        return CartItemQuantityResponse.builder()
            .cartItemId(String.valueOf(cartItem.getId()))
            .quantity(cartItem.getQuantity())
            .build();
    }

}
