package com.devticket.commerce.cart.presentation.dto.res;

import com.devticket.commerce.cart.domain.model.CartItem;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CartItemDetail(
    UUID cartItemId,
    UUID eventId,
    String eventTitle,
    int price,
    int quantity
) {

    public static CartItemDetail of(CartItem cartItem, String title, int price) {
        return CartItemDetail.builder()
            .cartItemId(cartItem.getCartItemId())
            .eventId(cartItem.getEventId())
            .eventTitle(title)
            .price(price)
            .quantity(cartItem.getQuantity())
            .build();
    }
}
