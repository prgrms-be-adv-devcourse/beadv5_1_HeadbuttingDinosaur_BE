package com.devticket.commerce.order.presentation.dto.req;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

//장바구니에서 주문요청
public record CartOrderRequest(
    @NotEmpty(message = "주문할 장바구니 아이템을 1개 이상 선택해야 합니다.")
    List<UUID> cartItemIds
) {

}
