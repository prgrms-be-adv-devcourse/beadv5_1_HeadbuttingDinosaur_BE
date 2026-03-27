package com.devticket.commerce.cart.presentation.dto.res;

import com.devticket.commerce.cart.domain.model.Cart;
import com.devticket.commerce.cart.domain.model.CartItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

@Builder
@Schema(description = "장바구니 담기 요청건의 응답 데이터")
public record CartItemResponse(
    String cartId,
    List<CartItemDetail> items,
    long totalAmount
) {

    public static CartItemResponse of(Cart cart, CartItem cartItem, String title, int price) {

        CartItemDetail detail = CartItemDetail.of(cartItem, title, price);

        int totalAmount = price * cartItem.getQuantity();

        return CartItemResponse.builder()
            .cartId(String.valueOf(cart.getId()))
            .items(List.of(detail))
            .totalAmount(totalAmount)
            .build();
    }
}

//CartItemResponse의 Inner Record
@Builder
record CartItemDetail(
    Long eventId,
    String eventTitle,
    int price,
    int quantity
) {

    //엔티티와 외부 정보를 조합하여 DTO로 변환하는 정적 팩토리 메서드
    static CartItemDetail of(CartItem cartItem, String title, int price) {
        return CartItemDetail.builder()
            .eventId(cartItem.getEventId())
            .eventTitle(title)
            .price(price)
            .quantity(cartItem.getQuantity())
            .build();
    }

}