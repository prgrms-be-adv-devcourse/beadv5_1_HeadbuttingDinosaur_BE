package com.devticket.commerce.cart.presentation.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "장바구니 담기 요청")
public record CartItemRequest(

    @Schema(description = "이벤트 ID : Long타입")
    UUID eventId,

    @Schema(description = "장바구니에 담을 수량")
    int quantity
) {

}
