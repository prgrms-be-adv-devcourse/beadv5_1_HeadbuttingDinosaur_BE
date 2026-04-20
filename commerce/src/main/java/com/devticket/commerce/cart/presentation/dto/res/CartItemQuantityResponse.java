package com.devticket.commerce.cart.presentation.dto.res;

import com.devticket.commerce.cart.domain.model.CartItem;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CartItemQuantityResponse(

    UUID cartItemId,
    int quantity

) {

    public static CartItemQuantityResponse of(CartItem cartItem) {
        return CartItemQuantityResponse.builder()
            .cartItemId(cartItem.getCartItemId())
            .quantity(cartItem.getQuantity())
            .build();
    }

}
