package com.devticket.admin.presentation.controller;

import com.devticket.admin.application.service.AdminSettlementService;
import com.devticket.admin.presentation.dto.req.AdminSettlementSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminSettlementListResponse;
import com.devticket.admin.presentation.dto.res.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Tag(name = "관리자 정산 내역 관리 API")
public class AdminSettlementController {

    private final AdminSettlementService adminSettlementService;

    @Operation(summary = "관리자 정산 내역 조회 API")
    @ApiResponse(responseCode = "200", description = "정산 목록 조회 성공")
    @ApiResponse(responseCode = "403", description = "접근 권한 없음 (COMMON_005)")
    @GetMapping("/settlements")
    public AdminSettlementListResponse getAdminSettlementList(
        @ModelAttribute @Valid AdminSettlementSearchRequest condition) {
        return adminSettlementService.getSettlementList(condition);
    }

    @Operation(summary = "관리자 정산 프로세스 실행")
    @ApiResponse(responseCode = "204", description = "정산 프로세스 실행 성공")
    @ApiResponse(responseCode = "403", description = "접근 권한 없음 (COMMON_005)")
    @ApiResponse(responseCode = "409", description = "이미 실행 중인 정산 프로세스 존재 (SETTLEMENT_002)")
    @PostMapping("/settlements/run")
    public void runSettlement(@RequestHeader("X-User-Id") UUID adminId) {
//        adminSettlementService.runSettlement(adminId);
    }

}
