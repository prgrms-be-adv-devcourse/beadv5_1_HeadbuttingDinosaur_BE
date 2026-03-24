package com.devticket.payment.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/payments")
public class PaymentController {
    @PostMapping
    @Operation(
        summary = "결제 요청",
        description = "주문에 대한 결제를 요청합니다. (PG 또는 예치금)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "결제 요청 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "502", description = "PG 연동 실패")
    })
    public Map<String, Object> requestPayment(@RequestBody Map<String, Object> request) {
        return Map.of(
            "status", "SUCCESS",
            "message", "결제 요청 완료"
        );
    }
}