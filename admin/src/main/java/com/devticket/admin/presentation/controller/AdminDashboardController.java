package com.devticket.admin.presentation.controller;

import com.devticket.admin.presentation.dto.res.AdminDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Tag(name = "관리자 대시보드 통계 API")
public class AdminDashboardController {

    @Operation(summary = "관리자 대시보드 통계 API")
    @ApiResponse(responseCode = "200", description = "대시보드 통계 조회 성공")
    @ApiResponse(responseCode = "403", description = "접근 권한 없음 (COMMON_005)")
    @GetMapping("/dashboard")
    public AdminDashboardResponse getAdminDashboard() {

        return new AdminDashboardResponse(1521L, 1L, 5L, 4L);

    }

}
