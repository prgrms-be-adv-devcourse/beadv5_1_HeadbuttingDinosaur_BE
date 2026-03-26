package com.devticket.commerce.cart.presentation.controller;

import com.devticket.commerce.cart.application.usecase.CartUseCase;
import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@Tag(name = "Cart API", description = "클라이언트 대상 장바구니 API")
public class CartController {

    private final CartUseCase cartUseCase;

    @PostMapping("/items")
    @Operation(description = "장바구니 담기")
    public ResponseEntity<CartItemResponse> addToCart(
        @RequestHeader("X-User-Id") Long userId,
        @RequestBody CartItemRequest request
    ) {
        CartItemResponse response = cartUseCase.save(userId, request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
}
