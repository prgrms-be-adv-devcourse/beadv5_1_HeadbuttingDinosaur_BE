package com.devticket.commerce.order.presentation.dto.req;


import java.util.List;

//장바구니에서 주문요청
public record CartOrderRequest(
    List<Long> cartItemIds
) {

}
