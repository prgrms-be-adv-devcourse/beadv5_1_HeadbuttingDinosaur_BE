package com.devticket.commerce.cart.presentation.dto.res;

import com.devticket.commerce.cart.domain.model.CartItem;
import lombok.Builder;

@Builder
public record CartItemDetail(
    Long eventId,
    String eventTitle,
    int price,
    int quantity
) {

    //엔티티와 외부 정보를 조합하여 DTO로 변환하는 정적 팩토리 메서드
    public static CartItemDetail of(CartItem cartItem, String title, int price) {
        return CartItemDetail.builder()
            .eventId(cartItem.getEventId())
            .eventTitle(title)
            .price(price)
            .quantity(cartItem.getQuantity())
            .build();
    }

}
