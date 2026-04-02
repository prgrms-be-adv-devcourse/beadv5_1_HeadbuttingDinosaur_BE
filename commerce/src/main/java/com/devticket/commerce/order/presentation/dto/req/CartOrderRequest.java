package com.devticket.commerce.order.presentation.dto.req;


import java.util.List;
import java.util.UUID;

//장바구니에서 주문요청
public record CartOrderRequest(
    List<UUID> cartItemIds
) {

}
