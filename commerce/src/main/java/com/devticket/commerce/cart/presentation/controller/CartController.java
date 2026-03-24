package com.devticket.commerce.cart.presentation.controller;

import com.devticket.commerce.cart.presentation.dto.req.CartItemQuantityRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemQuantiryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
@Tag(name = "Cart : 장바구니 API", description = "장바구니")
public class CartController {

    @Operation(
        summary = "장바구니 조회",
        description = "현재 사용자의 장바구니 전체 내역을 조회합니다.",
        responses = @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "장바구니 조회 응답 예시",
                    value = """
                        {
                          "cartId": "cart-uuid-1234",
                          "items": [
                            {
                              "cartItemId": "c1d2e3",
                              "eventId": "a1b2c3",
                              "eventTitle": "Spring 밋업",
                              "price": 15000,
                              "quantity": 2
                            }
                          ],
                          "totalAmount": 30000
                        }
                        """
                )
            )
        )
    )
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCart() {
        return ResponseEntity.ok().build();
    }


    @Operation(
        summary = "장바구니 담기",
        description = "eventId와 quantity를 받아 장바구니에 추가합니다.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                examples = @ExampleObject(
                    name = "장바구니 담기 요청 예시",
                    value = "{ \"eventId\": \"a1b2c3d4-1111-2222-3333-abcdef123456\", \"quantity\": 2 }"
                )
            )
        ),
        responses = @ApiResponse(
            responseCode = "200",
            description = "담기 성공",
            content = @Content(
                examples = @ExampleObject(
                    name = "장바구니 담기 응답 예시",
                    value = "{ \"cartItemId\": \"c1d2e3f4-1111-2222-3333-abcdef123456\", \"eventId\": \"a1b2c3d4-1111-2222-3333-abcdef123456\", \"quantity\": 2 }"
                )
            )
        )
    )
    @PostMapping("/items")
    public ResponseEntity<Map<String, Object>> addItemToCart(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok().build();
    }

    @Operation(description = "장바구니 아이템 수량 변경")
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<CartItemQuantiryResponse> updateItemQuantity(@PathVariable String cartItemId,
        @RequestBody CartItemQuantityRequest request) {
        return ResponseEntity.ok().build();
    }


    @Operation(description = "")
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity deleteCartItem(@PathVariable String cartItemId) {
        return ResponseEntity.ok().build();
    }

}
