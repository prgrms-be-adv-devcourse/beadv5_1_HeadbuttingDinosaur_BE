package com.devticket.commerce.cart.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장바구니 담기 요청건의 응답 데이터")
public record CartItemResponse(
    String eventId,
    String eventTitle,
    int price,
    int quantity
) {

}
