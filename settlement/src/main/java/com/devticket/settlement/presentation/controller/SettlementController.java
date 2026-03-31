package com.devticket.settlement.presentation.controller;

import com.devticket.settlement.application.service.SettlementServiceImpl;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import com.devticket.settlement.presentation.dto.SellerSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import com.devticket.settlement.presentation.scheduler.SettlementScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Settlement", description = "정산 외부 API")
@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementServiceImpl settlementServiceImpl;

    // 자동 정산 스케쥴러 목 데이터
    private final SettlementScheduler settlementScheduler;

    // 목 데이터
    @GetMapping("/seller/settlements/fetch")
    public ResponseEntity<InternalSettlementDataResponse> fetchSettlementData(
        @RequestHeader("X-User-Id") UUID sellerId,
        @RequestParam String periodStart,
        @RequestParam String periodEnd
    ) {
        return ResponseEntity.ok(settlementServiceImpl.fetchSettlementData(sellerId, periodStart, periodEnd));
    }

    // 자동 정산 목 데이터
    @GetMapping("/test/batch")
    public ResponseEntity<String> runBatch() {
        settlementScheduler.runSettlementJob();
        return ResponseEntity.ok("배치 실행 완료");
    }

    @Operation(
        summary = "판매자 정산 내용 조회",
        description = "판매자 정산 내용을 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "정산 내역 조회 성공")
    @GetMapping("/seller/settlements")
    public ResponseEntity<List<SettlementResponse>> getSellerSettlements(
        @RequestHeader("X-User-Id") UUID sellerId) {
        return ResponseEntity.ok(settlementServiceImpl.getSellerSettlements(sellerId));
    }

    @Operation(
        summary = "판매자 정산 내용 상세 조회",
        description = "판매자 정산 내용을 상세 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "정산 내역 조회 성공")
    @GetMapping("/seller/settlements/{settlementId}")
    public ResponseEntity<SellerSettlementDetailResponse> getSellerSettlement(
        @RequestHeader("X-User-Id") UUID sellerId,
        @PathVariable UUID settlementId) {
        return ResponseEntity.ok(settlementServiceImpl.getSellerSettlementDetail(sellerId, settlementId));
    }

}