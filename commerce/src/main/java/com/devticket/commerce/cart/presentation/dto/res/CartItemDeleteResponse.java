package com.devticket.commerce.cart.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장바구니 아이템 삭제 응답")
public record CartItemDeleteResponse(

    @Schema(description = "응답 메시지")
    String message

) {

    public static CartItemDeleteResponse of() {
        return new CartItemDeleteResponse("삭제되었습니다.");
    }
}
