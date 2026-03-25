package com.devticket.settlement.presentation.controller;

import com.devticket.settlement.application.service.SettlementServiceImpl;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Settlement", description = "정산 외부 API")
@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementServiceImpl settlementServiceImpl;

    @Operation(
        summary = "판매자 정산 내용 조회",
        description = "판매자 정산 내용을 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "정산 내역 조회 성공")
    @GetMapping("/seller/settlements")
    public ResponseEntity<List<SettlementResponse>> getSellerSettlements(Long sellerId) {
        return ResponseEntity.ok(settlementServiceImpl.getSellerSettlements(sellerId));
    }

}