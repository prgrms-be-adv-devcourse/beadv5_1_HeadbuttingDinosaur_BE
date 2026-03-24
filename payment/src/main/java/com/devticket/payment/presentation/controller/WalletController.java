package com.devticket.payment.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallet")
@Tag(name = "Wallet", description = "예치금 API")
public class WalletController {
    @PostMapping("/charge")
    @Operation(
        summary = "예치금 충전 시작",
        description = "예치금 충전을 시작합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "충전 요청 성공"),
        @ApiResponse(responseCode = "400", description = "충전 금액 오류"),
        @ApiResponse(responseCode = "502", description = "PG 연동 실패")
    })
    public Map<String, Object> chargeWallet(@RequestBody Map<String, Object> request) {
        return Map.of(
            "status", "SUCCESS",
            "message", "예치금 충전 요청 완료"
        );
    }
}