package com.devticket.admin.presentation.controller;

import com.devticket.admin.presentation.dto.res.SellerApplicationListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "SELLER(판매자) 신청 관리 API")
public class AdminSellerController {

    @Operation(summary = "판매자 신청 리스트 조회 API")
    @ApiResponse(responseCode = "200", description = "판매자 신청 리스트 조회 성공")
    @ApiResponse(responseCode = "403", description = "접근 권한 없음 (COMMON_005)")
    @GetMapping("/admin/seller-applications")
    public List<SellerApplicationListResponse> getSellerApplicationList() {

        return List.of();
    }

    @Operation(summary = "판매자 신청 승인/반려 API")
    @ApiResponse(responseCode = "204", description = "판매자 신청 승인/반려 성공")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (MEMBER_009)")
    @ApiResponse(responseCode = "409", description = "이미 처리된 판매자 신청 건 (ADMIN_003)")
    @ApiResponse(responseCode = "403", description = "접근 권한 없음 (COMMON_005)")
    @PatchMapping("/admin/seller-applications/{applicationId}")
    public void decideApplication(@PathVariable UUID applicationId) {

    }

}
