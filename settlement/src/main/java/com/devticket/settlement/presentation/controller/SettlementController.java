package com.devticket.settlement.presentation.controller;

import com.devticket.settlement.application.service.SettlementServiceImpl;
import com.devticket.settlement.presentation.dto.SellerSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Settlement", description = "정산 외부 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementServiceImpl settlementServiceImpl;

    @Operation(
        summary = "[테스트] 정산대상 데이터 수집 미리보기",
        description = "DB 저장 없이 Event 서비스 → Commerce 서비스 순서대로 실제 API를 호출하여 " +
            "수집될 정산대상 데이터를 미리 확인합니다. date 미입력 시 어제 날짜로 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "미리보기 조회 성공")
    @GetMapping("/test/settlement-target/preview")
    public ResponseEntity<SettlementTargetPreviewResponse> previewSettlementTarget(
        @Parameter(description = "종료된 이벤트 조회 기준 날짜 (yyyy-MM-dd), 미입력 시 어제")
        @RequestParam(required = false) LocalDate date
    ) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now().minusDays(1);
        return ResponseEntity.ok(settlementServiceImpl.previewSettlementTarget(targetDate));
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