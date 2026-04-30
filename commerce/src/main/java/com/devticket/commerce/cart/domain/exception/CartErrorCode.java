package com.devticket.commerce.cart.domain.exception;

import com.devticket.commerce.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CartErrorCode implements ErrorCode {

    EVENT_ENDED(409, "CART_001", "판매가 종료된 이벤트입니다."),
    OUT_OF_STOCK(409, "CART_002", "재고가 부족합니다."),
    INVALID_QUANTITY(400, "CART_003", "수량은 1개 이상이어야 합니다."),
    EXCEED_MAX_PURCHASE(400, "CART_004", "인당 최대 구매 수량을 초과했습니다."),
    ITEM_NOT_FOUND(404, "CART_005", "장바구니에 해당 항목이 없습니다."),
    CART_EMPTY(400, "CART_006", "장바구니가 비어 있습니다."),
    EVENT_PURCHASE_VALID_UNAVAILABLE(500, "CART_007", "현재 이벤트 정보를 불러올 수 없어 장바구니 담기가 일시적으로 제한됩니다. 잠시 후 다시 시도해주세요."),
    CART_NOT_FOUND(404, "CART_008", "장바구니를 찾을 수 없습니다."),
    CART_ITEM_NOT_FOUND(404, "CART_009", "선택한 티켓이 장바구니에 없습니다."),
    DUPLICATE_CART_ITEM_ID(400, "CART_010", "중복된 상품이 포함되어 있습니다."),
    SELLER_CANNOT_PURCHASE_OWN_EVENT(403, "CART_011", "본인이 등록한 이벤트는 구매할 수 없습니다.");
    
    private final int status;
    private final String code;
    private final String message;
}