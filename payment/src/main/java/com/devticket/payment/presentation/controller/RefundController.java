package com.devticket.payment.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/refunds")
@Tag(name = "Refund", description = "환불 API")
public class RefundController {

    @GetMapping()
    @Operation(
        summary = "환불 내역 조회",
        description = "사용자의 환불 내역 목록을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "환불 내역 없음")
    })
    public Map<String, Object> getRefunds(@RequestParam Long userId) {
        return Map.of(
            "userId", userId,
            "refunds", List.of(
                Map.of(
                    "refundId", 1,
                    "amount", 30000,
                    "status", "COMPLETED"
                )
            )
        );
    }
}
