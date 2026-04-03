package com.devticket.admin.presentation.controller;

import com.devticket.admin.presentation.dto.req.AdminSettlementSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminSettelmentListResponse;
import com.devticket.admin.presentation.dto.res.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "관리자 정산 내역 관리 API")
public class AdminSettlementController {

    @Operation(summary = "관리자 정산 내역 조회 API")
    @ApiResponse(responseCode = "200", description = "정산 목록 조회 성공")
    @ApiResponse(responseCode = "403", description = "접근 권한 없음 (COMMON_005)")
    @GetMapping("/admin/settlements")
    public AdminSettelmentListResponse getAdminSettlementList(@ModelAttribute AdminSettlementSearchRequest condition) {
        List<SettlementResponse> mockContent = List.of(
            new SettlementResponse(
                "a1b2c3d4-5678-9012-abcd-ef1234567890",
                "2026-03-01T00:00:00",
                "2026-03-31T23:59:59",
                1000000,
                50000,
                95000,
                855000,
                "COMPLETED",
                "2026-04-01T10:00:00"
            )
        );

        return new AdminSettelmentListResponse(mockContent, 0, 20, 1L, 1);
    }

    @Operation(summary = "관리자 정산 프로세스 실행")
    @ApiResponse(responseCode = "204", description = "정산 프로세스 실행 성공")
    @ApiResponse(responseCode = "403", description = "접근 권한 없음 (COMMON_005)")
    @ApiResponse(responseCode = "409", description = "이미 실행 중인 정산 프로세스 존재 (SETTLEMENT_002)")
    @PostMapping("/admin/settlements/run")
    public void runSettlement() {

    }

}
