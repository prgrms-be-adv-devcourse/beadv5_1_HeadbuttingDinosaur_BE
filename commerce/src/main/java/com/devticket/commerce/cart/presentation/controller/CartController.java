package com.devticket.commerce.cart.presentation.controller;

import com.devticket.commerce.cart.application.usecase.CartItemUseCase;
import com.devticket.commerce.cart.application.usecase.CartUseCase;
import com.devticket.commerce.cart.presentation.dto.req.CartItemQuantityRequest;
import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartClearResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartItemDeleteResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartItemQuantityResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;
import com.devticket.commerce.cart.presentation.dto.res.CartResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart API", description = "클라이언트 대상 장바구니 API")
public class CartController {

    private final CartUseCase cartUseCase;
    private final CartItemUseCase cartItemUseCase;

    @PostMapping("/items")
    @Operation(description = "장바구니 담기")
    public ResponseEntity<CartItemResponse> addToCart(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestBody CartItemRequest request
    ) {
        CartItemResponse response = cartUseCase.save(userId, request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @GetMapping
    @Operation(description = "장바구니 조회")
    public ResponseEntity<CartResponse> getCart(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        CartResponse response = cartUseCase.getCart(userId);
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(response);
    }

    @PatchMapping("/items/{cartItemId}")
    @Operation(description = "장바구니 아이템 증감")
    public ResponseEntity<CartItemQuantityResponse> updateCartItemQuantity(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable Long cartItemId,
        @RequestBody CartItemQuantityRequest request
    ) {
        CartItemQuantityResponse response = cartItemUseCase.updateTicket(userId, cartItemId, request);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(response);
    }

    @DeleteMapping("/items/{cartItemId}")
    @Operation(description = "장바구니 아이템 삭제")
    public ResponseEntity<CartItemDeleteResponse> deleteCartItem(
        @PathVariable Long cartItemId,
        @RequestHeader("X-User-Id") UUID userId
    ) {
        CartItemDeleteResponse response = cartItemUseCase.deleteTicket(userId, cartItemId);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(response);
    }

    @DeleteMapping
    @Operation(description = "장바구니 아이템 전체 삭제")
    public ResponseEntity<CartClearResponse> deleteCartItemAll(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        CartClearResponse response = cartUseCase.clearCart(userId);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(response);
    }

}
