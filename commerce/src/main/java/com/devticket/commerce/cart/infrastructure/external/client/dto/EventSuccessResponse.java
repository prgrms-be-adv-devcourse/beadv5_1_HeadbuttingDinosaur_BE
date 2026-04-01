package com.devticket.commerce.cart.infrastructure.external.client.dto;

public record EventSuccessResponse<T>(
    int status,
    String message,
    T data
) {

}
