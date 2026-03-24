package com.devticket.settlement.presentation.controller;

import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Settlement", description = "정산 외부 API")
@RestController
public class SettlementController {

    @Operation(
        summary = "Settlement 서비스 헬스 체크",
        description = "Swagger UI 및 서비스 기동 여부 확인용 API"
    )
    @ApiResponse(responseCode = "200", description = "정상 응답")
    @GetMapping("/api/settlement/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("settlement ok");
    }

    @Operation(
        summary = "판매자 정산 내역 조회",
        description = "판매자의 정산 내역을 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "정산 내역 조회 성공")
    @GetMapping("/seller/settlements")
    public ResponseEntity<List<SettlementResponse>> getSellerSettlements() {
        return ResponseEntity.ok(List.of(
            new SettlementResponse(
                501L,
                "2024-03-01",
                "2024-03-15",
                1_000_000L,
                50_000L,
                100_000L,
                850_000L,
                SettlementStatus.COMPLETED,
                "2024-03-16T10:00:00"
            )
        ));

    }
}