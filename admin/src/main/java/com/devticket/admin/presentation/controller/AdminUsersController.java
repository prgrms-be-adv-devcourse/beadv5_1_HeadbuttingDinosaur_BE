package com.devticket.admin.presentation.controller;

import com.devticket.admin.application.service.AdminUserService;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import com.devticket.admin.presentation.dto.res.UserListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Admin User", description = "관리자 회원 관리 API")
public class AdminUsersController {

    private final AdminUserService adminUserService;

    @Operation(summary = "회원 목록 조회")
    @ApiResponse(responseCode = "200", description = "정산 내역 조회 성공")
    @GetMapping("/admin/users")
    public List<UserListResponse> getUsers(
        @RequestBody UserSearchCondition condition) {

        return adminUserService.getMembers(condition);
    }

    @Operation(summary = "회원 제재 api")
    @ApiResponse(responseCode = "200", description = "회원 제재 성공")
    @PatchMapping("/admin/users/{userId}/status")
    public void penalizeUser(
        @RequestHeader("X-User-Id") UUID adminId,
        @PathVariable UUID userId,
        @RequestBody UserStatusRequest request) {

        adminUserService.penalizeUser(adminId, userId, request);
    }

    @Operation(summary = "회원 권한 변경 api")
    @ApiResponse(responseCode = "200", description = "회원 권한 변경 성공")
    @PatchMapping("/admin/users/{userId}/role")
    public void updateUserRole(
        @RequestHeader("X-User-Id") UUID adminId,
        @PathVariable UUID userId,
        @RequestBody UserRoleRequest request) {

        adminUserService.updateUserRole(adminId, userId, request);

    }


}
