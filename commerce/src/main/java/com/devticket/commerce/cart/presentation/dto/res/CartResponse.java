package com.devticket.commerce.cart.presentation.dto.res;

import com.devticket.commerce.cart.domain.model.Cart;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

@Builder
@Schema(description = "장바구니 조회 응답 데이터")
public record CartResponse(

    @Schema(description = "장바구니 ID")
    String cartId,

    @Schema(description = "장바구니 아이템 목록")
    List<CartItemDetail> items,

    @Schema(description = "총 금액")
    int totalAmount

) {

    // (카트 + 카트 아이템 상세) -> cartResponse 정적 팩토리 메서드
    public static CartResponse of(Cart cart, List<CartItemDetail> items) {
        int totalAmount = items.stream()
            .mapToInt(item -> item.price() * item.quantity())
            .sum();

        return CartResponse.builder()
            .cartId(String.valueOf(cart.getId()))
            .items(items)
            .totalAmount(totalAmount)
            .build();
    }

}
