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

    public record CartItemDetail(
        String eventId,
        String eventTitle,
        long price,
        int quantity
    ) {

    }

    //엔티티 및 계산된 데이터를 DTO로 변환하는 정적 팩토리 메서드
    public static CartItemResponse of(Cart cart, CartItem savedItem, String title, int price) {
        CartItemDetail detail = new CartItemDetail(
            savedItem.getEventId().toString(),
            title,
            (long) price,
            savedItem.getQuantity()
        );

        int totalAmount = price * savedItem.getQuantity();

        return CartItemResponse.builder()
            .cartId(String.valueOf(cart.getId()))
            .items(List.of(detail))
            .totalAmount(totalAmount)
            .build();
    }

}
