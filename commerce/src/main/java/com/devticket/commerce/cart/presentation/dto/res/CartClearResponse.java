package com.devticket.commerce.cart.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장바구니 전체 삭제 응답")
public record CartClearResponse(

    @Schema(description = "응답 메시지")
    String message

) {

    public static CartClearResponse of() {
        return new CartClearResponse("삭제 완료");
    }

}
